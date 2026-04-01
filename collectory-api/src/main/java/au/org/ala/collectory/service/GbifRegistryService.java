package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Services required to register and update organisations and datasets in GBIF.
 * <p>
 * This is intended to be used only when the ALA is the publishing gateway to GBIF and not when the ALA installation is
 * sourcing its data from GBIF. The service was originally written for the needs of the UK ALA installation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GbifRegistryService {

    private final AppProperties appProperties;
    private final IsoCodeService isoCodeService;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final InstitutionRepository institutionRepository;
    private final ContactForRepository contactForRepository;
    private final ObjectMapper objectMapper;

    // URL templates for the GBIF API relative to the base GBIF API url (e.g. https://api.gbif.org/v1/)
    static final String API_ORGANIZATION = "organization";
    static final String API_ORGANIZATION_COUNTRY_LIMIT = "organization?country={0}&limit={1}";
    static final String API_ORGANIZATION_DETAIL = "organization/{0}";
    static final String API_ORGANIZATION_CONTACT = "organization/{0}/contact";
    static final String API_ORGANIZATION_CONTACT_DETAIL = "organization/{0}/contact/{1}";

    static final String API_DATASET = "dataset";
    static final String API_DATASET_DETAIL = "dataset/{0}";
    static final String API_DATASET_ENDPOINT = "dataset/{0}/endpoint";
    static final String API_DATASET_ENDPOINT_DETAIL = "dataset/{0}/endpoint/{1}";

    private static final ExecutorService pool = Executors.newFixedThreadPool(1);

    private RestTemplate gbifRestTemplate;

    @PostConstruct
    public void init() {
        this.gbifRestTemplate = createGbifRestTemplate();
    }

    private boolean isDryRun() {
        return appProperties.isGbifRegistrationDryRun();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Updates all registrations of data providers and data resources with GBIF. This will create missing datasets
     * in GBIF, update the organisation metadata in GBIF, and set DOIs in the datasets in the Collectory if configured
     * to do so (i.e. config useGbifDoi=true).
     */
    @Transactional
    public List<DataProvider> updateAllRegistrations() {
        List<DataProvider> providers = new ArrayList<>();
        try {
            providers = dataProviderRepository.findAll();
            for (DataProvider dp : providers) {
                log.info("Update of registration of {}: {}", dp.getUid(), dp.getName());
                updateRegistration(dp, true, true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return providers;
    }

    /**
     * Updates the registration in GBIF for the ProviderGroup.
     * This updates the key metadata and the contacts which is typically all publishers provide to GBIF.
     */
    @Transactional
    public void updateRegistration(ProviderGroup dp, boolean syncContacts, boolean syncDataResources) {
        if (dp.getGbifRegistryKey() != null && !dp.getGbifRegistryKey().isEmpty()) {
            boolean success = updateRegistrationMetadata(dp);
            if (success) {
                log.info("Successfully updated provider in GBIF: {}", dp.getGbifRegistryKey());
                if (syncContacts) {
                    syncContactsForProviderGroup(dp);
                }
                log.info("Successfully synced contacts: {}", dp.getGbifRegistryKey());
                if (syncDataResources) {
                    syncDataResourcesForProviderGroup(dp);
                    log.info("Successfully synced resources in GBIF: {}", dp.getGbifRegistryKey());
                }
            }
        } else {
            log.info("No GBIF registration exists for dp[{}] - nothing to update", dp.getUid());
        }
    }

    /**
     * Creates a new registration in GBIF for the ProviderGroup as a publishing organization, endorsed by the relevant
     * node. Note: the GBIF Country to Attribute is used to instruct GBIF which country should be credited with
     * publishing the data.
     */
    @Transactional
    public void register(ProviderGroup dp, boolean syncContacts, boolean syncDataResources) {
        Map<String, Object> organisation = new LinkedHashMap<>();
        organisation.put("endorsingNodeKey", appProperties.getGbifEndorsingNodeKey());
        organisation.put("endorsementApproved", true);
        organisation.put("language", "eng");
        populateOrganisation(organisation, dp);

        if (!isDryRun()) {
            String url = appProperties.getGbifApiUrl() + API_ORGANIZATION;
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(organisation, jsonHeaders());

            try {
                ResponseEntity<String> response = gbifRestTemplate.exchange(url, HttpMethod.POST, request, String.class);
                if (isSuccess(response.getStatusCode())) {
                    String registryKey = response.getBody() != null ? response.getBody().replaceAll("\"", "") : null;
                    dp.setGbifRegistryKey(registryKey);
                    log.info("Successfully created provider in GBIF: {}", dp.getGbifRegistryKey());
                    saveProviderGroup(dp);

                    if (syncContacts) {
                        log.info("Attempting to sync contacts: {}", dp.getGbifRegistryKey());
                        syncContactsForProviderGroup(dp);
                        log.info("Successfully created contacts: {}", dp.getGbifRegistryKey());
                    }

                    if (syncDataResources) {
                        log.info("Attempting to sync resources: {}", dp.getGbifRegistryKey());
                        syncDataResourcesForProviderGroup(dp);
                        log.info("Successfully created resources: {}", dp.getGbifRegistryKey());
                    }
                } else {
                    log.info("Unable to register organisation = {}: {}", response.getStatusCode(), response.getBody());
                    log.debug("Organisation payload: {}", toJson(organisation));
                }
            } catch (Exception e) {
                log.error("Error registering organisation: {}", e.getMessage(), e);
            }
        } else {
            log.info("[DRY RUN] Registration request for {} - {}", dp.getUid(), dp.getName());
            log.info(toJson(organisation));
        }
    }

    /**
     * Registers a single data resource with GBIF, favouring institution over data provider as the publisher.
     */
    @Transactional
    public Map<String, Object> registerDataResource(DataResource dataResource) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "");

        String publisherGbifRegistryKey = "";
        Institution institution = dataResource.getInstitution();
        DataProvider dataProvider = dataResource.getDataProvider();

        if (institution == null) {
            if (dataResource.getConsumerInstitutions() != null && !dataResource.getConsumerInstitutions().isEmpty()) {
                institution = dataResource.getConsumerInstitutions().iterator().next();
            }
        }

        if (institution != null) {
            if (institution.getGbifRegistryKey() != null && !institution.getGbifRegistryKey().isEmpty()) {
                updateRegistrationMetadata(institution);
            } else {
                register(institution, true, false);
            }
            publisherGbifRegistryKey = institution.getGbifRegistryKey();

        } else if (dataProvider != null) {
            if (dataProvider.getGbifRegistryKey() != null && !dataProvider.getGbifRegistryKey().isEmpty()) {
                updateRegistrationMetadata(dataProvider);
            } else {
                register(dataProvider, true, false);
            }
            publisherGbifRegistryKey = dataProvider.getGbifRegistryKey();

        } else if (appProperties.getGbifOrphansPublisherID() != null && !appProperties.getGbifOrphansPublisherID().isEmpty()) {
            log.info("Unable to sync resource: {} - {}. No publishing organisation associated.",
                    dataResource.getUid(), dataResource.getName());
            publisherGbifRegistryKey = appProperties.getGbifOrphansPublisherID();
        } else {
            log.info("Unable to sync resource: {} - {}. No publishing organisation associated.",
                    dataResource.getUid(), dataResource.getName());
            result.put("success", false);
            result.put("message", "Unable to sync resource: " + dataResource.getUid() +
                    " - " + dataResource.getName() + ". No publishing organisation associated.");
        }

        if (publisherGbifRegistryKey != null && !publisherGbifRegistryKey.isEmpty()) {
            log.info("Syncing data resource {} - {} with {}", dataResource.getUid(), dataResource.getName(), publisherGbifRegistryKey);
            syncDataResource(dataResource, publisherGbifRegistryKey);
            log.info("Sync complete for data resource {} - {}", dataResource.getUid(), dataResource.getName());
            result.put("success", true);
            result.put("message", "Data resource sync-ed with GBIF.");
        }

        return result;
    }

    /**
     * Sync the data resource with the provided organisation registry key.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void syncDataResource(DataResource dataResource, String organisationRegistryKey) {
        if (dataResource.getGbifRegistryKey() == null || dataResource.getGbifRegistryKey().isEmpty()) {
            // Register the missing dataset
            log.info("Creating GBIF resource for {}", dataResource.getUid());
            Map<String, Object> dataset = newGBIFDatasetInstance(dataResource, organisationRegistryKey);
            log.info("Creating dataset in GBIF: {}", dataset);

            if (dataset != null) {
                if (!isDryRun()) {
                    String url = appProperties.getGbifApiUrl() + MessageFormat.format(API_DATASET, organisationRegistryKey);
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(dataset, jsonHeaders());

                    try {
                        ResponseEntity<String> response = gbifRestTemplate.exchange(url, HttpMethod.POST, request, String.class);
                        String responseString = response.getBody();

                        if (isSuccess(response.getStatusCode()) && responseString != null) {
                            dataResource.setGbifRegistryKey(responseString.replaceAll("\"", ""));
                            log.info("Added dataset {}", dataResource.getGbifRegistryKey());
                            log.info("Successfully created dataset in GBIF: {}", dataResource.getGbifRegistryKey());
                            dataResourceRepository.save(dataResource);
                        } else {
                            log.error("Unable to add dataset {}, status code:{}, response string: {}",
                                    dataResource.getUid(), response.getStatusCode(), responseString);
                        }

                        if (appProperties.isUseGbifDoi() && dataResource.getGbifRegistryKey() != null) {
                            Map<String, Object> created = loadDataset(dataResource.getGbifRegistryKey());
                            if (created != null && !created.isEmpty()) {
                                dataResource.setGbifDoi((String) created.get("doi"));
                                dataResourceRepository.save(dataResource);
                            } else {
                                log.error("Unable to load GBIF ID {}", dataResource.getGbifRegistryKey());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error creating dataset in GBIF for {}: {}", dataResource.getUid(), e.getMessage(), e);
                    }
                } else {
                    log.info("[DRY RUN] Registration request for {} - {}", dataResource.getUid(), dataResource.getName());
                    log.info(toJson(dataset));
                }
            } else {
                log.warn("Unable to register dataset - please check license: {} : {} : {}",
                        dataResource.getUid(), dataResource.getName(), dataResource.getLicenseType());
            }
        } else {
            // Update existing dataset — ensure the organisation is correct in GBIF
            Map<String, Object> dataset = loadDataset(dataResource.getGbifRegistryKey());
            if (dataset != null && !dataset.isEmpty()) {
                if (!isDryRun()) {
                    if (appProperties.isUseGbifDoi() && !Objects.equals(dataResource.getGbifDoi(), dataset.get("doi"))) {
                        log.info("Setting resource[{}] to use gbifDOI[{}]", dataResource.getUid(), dataset.get("doi"));
                        dataResource.setGbifDoi((String) dataset.get("doi"));
                        dataResourceRepository.save(dataResource);
                    }

                    log.info("Updating the GBIF registry dataset[{}] to point to organisation[{}]",
                            dataResource.getGbifRegistryKey(), organisationRegistryKey);
                    dataset.put("publishingOrganizationKey", organisationRegistryKey);
                    dataset.put("deleted", null);
                    dataset.put("license", getGBIFCompatibleLicence(dataResource.getLicenseType()));
                    dataset.put("title", dataResource.getName());
                    dataset.put("description", dataResource.getPubDescription());

                    String logoUrl = dataResource.buildLogoUrl(appProperties.getServerUrl());
                    dataset.put("logoUrl", logoUrl);

                    dataset.put("homepage", dataResource.getWebsiteUrl());

                    if (dataset.get("license") != null) {
                        String url = appProperties.getGbifApiUrl() +
                                MessageFormat.format(API_DATASET_DETAIL, dataResource.getGbifRegistryKey());
                        HttpEntity<Map<String, Object>> request = new HttpEntity<>(dataset, jsonHeaders());

                        try {
                            ResponseEntity<String> response = gbifRestTemplate.exchange(url, HttpMethod.PUT, request, String.class);
                            if (isSuccess(response.getStatusCode())) {
                                log.info("Successfully updated dataset in GBIF: {}", dataResource.getGbifRegistryKey());
                            }
                        } catch (Exception e) {
                            log.error("Error updating dataset in GBIF: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("Unable to update dataset - please check license: {} : {} : {}",
                                dataResource.getUid(), dataResource.getName(), dataResource.getLicenseType());
                    }
                } else {
                    log.info("[DRYRUN] Updating data resource {}", dataset);
                }
            } else {
                log.error("Unable to update {} as GBIF lookup failed for {}", dataResource.getUid(), dataResource.getGbifRegistryKey());
            }
        }

        try {
            syncEndpoints(dataResource);
        } catch (Exception e) {
            log.error("Unable to syncEndpoints {}", dataResource.getUid(), e);
        }
    }

    /**
     * Deletes a dataset from the GBIF registry.
     */
    @Transactional
    public DataResource deleteDataResource(DataResource resource) {
        if (!isDryRun()) {
            String url = appProperties.getGbifApiUrl() +
                    MessageFormat.format(API_DATASET_DETAIL, resource.getGbifRegistryKey());
            try {
                ResponseEntity<String> response = gbifRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
                if (isSuccess(response.getStatusCode())) {
                    log.info("Deleted Dataset[{}] from GBIF", resource.getGbifRegistryKey());
                    resource.setGbifRegistryKey(null);
                    dataResourceRepository.save(resource);
                } else {
                    log.info("The delete of {} from GBIF was unsuccessful: {}", resource.getUid(), response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error deleting dataset from GBIF: {}", e.getMessage(), e);
            }
        } else {
            log.info("[DryRun] Deleting {}", resource.getUid());
        }
        return resource;
    }

    /**
     * Maps an ALA licence type string to a GBIF-compatible licence URL.
     */
    public String getGBIFCompatibleLicence(String licenseType) {
        String mappingUrl = appProperties.getGbifLicenceMappingUrl();
        if (mappingUrl != null && !mappingUrl.isEmpty() && !"null".equals(mappingUrl)) {
            try {
                Map<String, String> jsonLicense = objectMapper.readValue(new URL(mappingUrl), new TypeReference<>() {});
                return jsonLicense.get(licenseType);
            } catch (Exception e) {
                log.error("Error reading GBIF licence mapping URL: {}", e.getMessage(), e);
                return null;
            }
        } else {
            if (licenseType == null) return null;
            switch (licenseType) {
                case "CC0":
                    return "https://creativecommons.org/publicdomain/zero/1.0/legalcode";
                case "CC-BY":
                    return "https://creativecommons.org/licenses/by/4.0/legalcode";
                case "CC-BY-NC":
                    return "https://creativecommons.org/licenses/by-nc/4.0/legalcode";
                case "OGL":
                    // See https://en.wikipedia.org/wiki/Open_Government_Licence
                    return "https://creativecommons.org/licenses/by/4.0/legalcode";
                default:
                    log.info("Unsupported license {} for GBIF so cannot be registered", licenseType);
                    return null;
            }
        }
    }

    /**
     * Loads all organizations for a specific country from the GBIF API.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadOrganizationsByCountry(String countryCode, int limit) {
        String url = appProperties.getGbifApiUrl() +
                MessageFormat.format(API_ORGANIZATION_COUNTRY_LIMIT, countryCode, String.valueOf(limit));
        try {
            ResponseEntity<Map> response = gbifRestTemplate.getForEntity(url, Map.class);
            if (isSuccess(response.getStatusCode()) && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody().get("results");
            } else {
                log.error("[loadOrganizationsByCountry] Error response {} for {} with {} and {}",
                        response.getStatusCode(), API_ORGANIZATION_COUNTRY_LIMIT, countryCode, limit);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("[loadOrganizationsByCountry] Error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Overload with default limit of 1000.
     */
    public List<Map<String, Object>> loadOrganizationsByCountry(String countryCode) {
        return loadOrganizationsByCountry(countryCode, 1000);
    }

    /**
     * Loads an organization from the GBIF API.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadOrganization(String gbifRegistryKey) {
        String url = appProperties.getGbifApiUrl() + MessageFormat.format(API_ORGANIZATION_DETAIL, gbifRegistryKey);
        try {
            ResponseEntity<Map> response = gbifRestTemplate.getForEntity(url, Map.class);
            if (isSuccess(response.getStatusCode()) && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("[loadOrganization] Error response {} for {} with {}",
                        response.getStatusCode(), API_ORGANIZATION_DETAIL, gbifRegistryKey);
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("[loadOrganization] Error: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Loads a dataset from the GBIF API.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadDataset(String gbifRegistryKey) {
        try {
            String url = appProperties.getGbifApiUrl() + MessageFormat.format(API_DATASET_DETAIL, gbifRegistryKey);
            ResponseEntity<Map> response = gbifRestTemplate.getForEntity(url, Map.class);
            if (isSuccess(response.getStatusCode()) && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("[loadDataset] Error response {} for {} with {}",
                        response.getStatusCode(), API_DATASET_DETAIL, gbifRegistryKey);
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("[loadDataset] Error loading dataset: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Populates a DataProvider from a GBIF organization (reverse mapping).
     */
    @Transactional
    public void populateDataProviderFromOrganization(DataProvider dp, String organisationKey) {
        Map<String, Object> organisation = loadOrganization(organisationKey);
        if (organisation == null || organisation.isEmpty()) {
            return;
        }
        dp.setGbifRegistryKey((String) organisation.get("key"));
        dp.setName((String) organisation.get("title"));
        dp.setAcronym((String) organisation.get("abbreviation"));
        dp.setPubDescription((String) organisation.get("description"));

        @SuppressWarnings("unchecked")
        List<String> emails = (List<String>) organisation.get("email");
        if (emails != null && !emails.isEmpty()) {
            dp.setEmail(emails.get(0));
        }

        @SuppressWarnings("unchecked")
        List<String> phones = (List<String>) organisation.get("phone");
        if (phones != null && !phones.isEmpty()) {
            dp.setPhone(phones.get(0));
        }

        @SuppressWarnings("unchecked")
        List<String> homepages = (List<String>) organisation.get("homepage");
        if (homepages != null && !homepages.isEmpty()) {
            dp.setWebsiteUrl(homepages.get(0));
        }

        if (organisation.get("latitude") != null) {
            dp.setLatitude(Double.parseDouble(organisation.get("latitude").toString()));
        }
        if (organisation.get("longitude") != null) {
            dp.setLongitude(Double.parseDouble(organisation.get("longitude").toString()));
        }

        String country = (String) organisation.get("country");
        if (country != null) {
            dp.setGbifCountryToAttribute(isoCodeService.iso2CountryCodeToIso3CountryCode(country));
        }

        Address address = new Address();
        address.setState((String) organisation.get("province"));
        @SuppressWarnings("unchecked")
        List<String> addresses = (List<String>) organisation.get("address");
        if (addresses != null && !addresses.isEmpty()) {
            address.setStreet(addresses.get(0));
        }
        address.setCity((String) organisation.get("city"));
        address.setPostcode((String) organisation.get("postalCode"));
        dp.setAddress(address);
    }

    /**
     * Generates a CSV report of GBIF sync status.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public void writeCSVReportForGBIF(OutputStream outputStream) {
        log.debug("Starting report.....");
        String url = appProperties.getBiocacheServicesUrl() +
                "/occurrences/search?q=*:*&facets=data_resource_uid&pageSize=0&facet=on&flimit=-1";

        Map<String, Object> biocacheSearch;
        try {
            biocacheSearch = objectMapper.readValue(new URL(url), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Error reading biocache search results: {}", e.getMessage(), e);
            return;
        }

        try (CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(outputStream))) {
            String[] header = {
                    "UID", "Data resource", "Data resource GBIF ID", "Record count",
                    "Data provider UID", "Data provider name", "Data provider GBIF ID",
                    "Institution UID", "Institution name", "Institution GBIF ID",
                    "Licence",
                    "Shareable with GBIF", "Licence Issues (preventing sharing)",
                    "Not Shareable (no owner)", "Flagged as Not-Shareable", "Provided by GBIF",
                    "Linked to Data Provider", "Linked to Institution",
                    "Verified"
            };
            csvWriter.writeNext(header);

            List<Map<String, Object>> facetResults = (List<Map<String, Object>>) biocacheSearch.get("facetResults");
            if (facetResults == null || facetResults.isEmpty()) return;

            List<Map<String, Object>> fieldResults = (List<Map<String, Object>>) facetResults.get(0).get("fieldResult");
            if (fieldResults == null) return;

            for (Map<String, Object> result : fieldResults) {
                String fq = (String) result.get("fq");
                String uid = fq.replaceAll("\"", "").replaceAll("data_resource_uid:", "");
                Object count = result.get("count");

                Optional<DataResource> drOpt = dataResourceRepository.findByUid(uid);
                if (drOpt.isPresent()) {
                    DataResource dataResource = drOpt.get();

                    boolean isShareable = true;
                    boolean licenceIssues = false;
                    boolean flaggedAsNotShareable = false;
                    boolean providedByGBIF = false;
                    boolean notShareableNoOwner = false;

                    DataProvider dataProvider = null;
                    Institution institution = null;

                    Set<Institution> consumerInstitutions = dataResource.getConsumerInstitutions();
                    if (consumerInstitutions != null && !consumerInstitutions.isEmpty()) {
                        institution = consumerInstitutions.iterator().next();
                    }

                    if (institution == null) {
                        dataProvider = dataResource.getDataProvider();
                        if (dataProvider == null) {
                            notShareableNoOwner = true;
                            isShareable = false;
                        }
                    }

                    if (dataResource.getLicenseType() == null || getGBIFCompatibleLicence(dataResource.getLicenseType()) == null) {
                        licenceIssues = true;
                        isShareable = false;
                    }

                    if (dataResource.getIsShareableWithGBIF() != null && !dataResource.getIsShareableWithGBIF()) {
                        flaggedAsNotShareable = true;
                        isShareable = false;
                    }

                    if (dataResource.getGbifDataset() != null && dataResource.getGbifDataset()) {
                        providedByGBIF = true;
                        isShareable = false;
                    }

                    String[] row = {
                            dataResource.getUid(),
                            dataResource.getName(),
                            dataResource.getGbifRegistryKey(),
                            String.valueOf(count),
                            dataProvider != null ? dataProvider.getUid() : null,
                            dataProvider != null ? dataProvider.getName() : null,
                            dataProvider != null ? dataProvider.getGbifRegistryKey() : null,
                            institution != null ? institution.getUid() : null,
                            institution != null ? institution.getName() : null,
                            institution != null ? institution.getGbifRegistryKey() : null,
                            dataResource.getLicenseType(),
                            isShareable ? "yes" : "no",
                            licenceIssues ? "yes" : "no",
                            notShareableNoOwner ? "yes" : "no",
                            flaggedAsNotShareable ? "yes" : "no",
                            providedByGBIF ? "yes" : "no",
                            institution != null ? "yes" : "no",
                            dataProvider != null ? "yes" : "no",
                            isDataResourceVerified(dataResource) ? "yes" : "no"
                    };
                    csvWriter.writeNext(row);
                }
            }
        } catch (Exception e) {
            log.error("Error writing CSV report: {}", e.getMessage(), e);
        }
    }

    /**
     * Determines whether a DataResource is "verified" by checking that
     * its defaultDarwinCoreValues JSON has both georeferenceVerificationStatus
     * and identificationVerificationStatus set to "verified".
     */
    private boolean isDataResourceVerified(DataResource dataResource) {
        String dcv = dataResource.getDefaultDarwinCoreValues();
        if (dcv == null || dcv.isBlank()) return false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = objectMapper.readValue(dcv, Map.class);
            return "verified".equals(values.get("georeferenceVerificationStatus"))
                    && "verified".equals(values.get("identificationVerificationStatus"));
        } catch (Exception e) {
            log.warn("Could not parse defaultDarwinCoreValues for {}: {}", dataResource.getUid(), e.getMessage());
            return false;
        }
    }

    /**
     * Synchronises all shareable resources with GBIF.
     *
     * @return a map of statistics showing number of updates.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateAllResources() {
        try {
            Map<String, Object> results = generateSyncBreakdown();

            int resourcesRegistered = 0;
            int resourcesUpdated = 0;
            int dataProviderRegistered = 0;
            int institutionsRegistered = 0;

            Map<String, Object> shareable = (Map<String, Object>) results.get("shareable");
            log.info("Attempting to sync {} data resources.....", shareable.size());

            List<String> institutionsUpdated = new ArrayList<>();
            List<String> dataProviderUpdated = new ArrayList<>();
            Map<String, Institution> linkedToInstitution = (Map<String, Institution>) results.get("linkedToInstitution");
            Map<String, DataProvider> linkedToDataProvider = (Map<String, DataProvider>) results.get("linkedToDataProvider");

            for (String drUid : shareable.keySet()) {
                try {
                    Optional<DataResource> drOpt = dataResourceRepository.findByUid(drUid);
                    if (drOpt.isEmpty()) continue;
                    DataResource dataResource = drOpt.get();

                    log.info("Starting sync of data resource {} - {}", dataResource.getUid(), dataResource.getName());

                    String publisherGbifRegistryKey = "";

                    Institution institution = linkedToInstitution.get(drUid);
                    DataProvider dataProvider = linkedToDataProvider.get(drUid);

                    if (institution != null) {
                        if (institution.getGbifRegistryKey() != null && !institution.getGbifRegistryKey().isEmpty()) {
                            if (!institutionsUpdated.contains(institution.getUid())) {
                                updateRegistrationMetadata(institution);
                            }
                            institutionsUpdated.add(institution.getUid());
                        } else {
                            register(institution, true, false);
                            institutionsRegistered++;
                        }
                        publisherGbifRegistryKey = institution.getGbifRegistryKey();

                    } else if (dataProvider != null) {
                        if (dataProvider.getGbifRegistryKey() != null && !dataProvider.getGbifRegistryKey().isEmpty()) {
                            if (!dataProviderUpdated.contains(dataProvider.getUid())) {
                                updateRegistrationMetadata(dataProvider);
                            }
                            dataProviderUpdated.add(dataProvider.getUid());
                        } else {
                            register(dataProvider, true, false);
                            dataProviderRegistered++;
                        }
                        publisherGbifRegistryKey = dataProvider.getGbifRegistryKey();

                    } else if (appProperties.getGbifOrphansPublisherID() != null && !appProperties.getGbifOrphansPublisherID().isEmpty()) {
                        publisherGbifRegistryKey = appProperties.getGbifOrphansPublisherID();
                        log.info("Using orphans publisher ID to sync resource: {}", dataResource.getUid());
                    } else {
                        log.info("Unable to sync resource: {} - {}. No publishing organisation associated. gbifOrphansPublisherID = {}",
                                dataResource.getUid(), dataResource.getName(), appProperties.getGbifOrphansPublisherID());
                    }

                    if (publisherGbifRegistryKey != null && !publisherGbifRegistryKey.isEmpty()) {
                        log.info("Syncing data resource {} - {}", dataResource.getUid(), dataResource.getName());
                        try {
                            syncDataResource(dataResource, publisherGbifRegistryKey);
                            log.info("Sync complete for data resource {} - {}", dataResource.getUid(), dataResource.getName());
                            if (dataResource.getGbifRegistryKey() != null) {
                                resourcesUpdated++;
                            } else {
                                resourcesRegistered++;
                            }
                        } catch (Exception e) {
                            log.error("Sync error for data resource {} - {} - {}", dataResource.getUid(), dataResource.getName(), e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Problem with sync error for data resource {} - {}", drUid, e.getMessage(), e);
                }
            }

            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("resourcesRegistered", resourcesRegistered);
            resultMap.put("resourcesUpdated", resourcesUpdated);
            resultMap.put("dataProviderRegistered", dataProviderRegistered);
            resultMap.put("dataProviderUpdated", dataProviderUpdated.size());
            resultMap.put("institutionsRegistered", institutionsRegistered);
            resultMap.put("institutionsUpdated", institutionsUpdated.size());
            log.info("Sync complete {}", resultMap);
            return resultMap;

        } catch (Exception e) {
            log.error("Problem syncing resources - {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Retrieves a breakdown of data resources with available data, categorizing them by shareability with GBIF.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateSyncBreakdown() {
        log.info("Generating sync breakdown...");

        String url = appProperties.getBiocacheServicesUrl() +
                "/occurrences/search?q=*:*&facets=data_resource_uid&pageSize=0&facet=on&flimit=-1";

        Map<String, Object> biocacheSearch;
        try {
            biocacheSearch = objectMapper.readValue(new URL(url), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Error reading biocache search results: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }

        Map<String, Object> dataResourcesWithData = new LinkedHashMap<>();
        Map<String, Object> shareable = new LinkedHashMap<>();
        Map<String, Object> licenceIssues = new LinkedHashMap<>();
        Map<String, Object> notShareable = new LinkedHashMap<>();
        Map<String, Object> providedByGBIF = new LinkedHashMap<>();
        Map<String, Object> notShareableNoOwner = new LinkedHashMap<>();
        Map<String, DataProvider> linkedToDataProvider = new LinkedHashMap<>();
        Map<String, Institution> linkedToInstitution = new LinkedHashMap<>();
        long recordsShareable = 0;

        List<Map<String, Object>> facetResults = (List<Map<String, Object>>) biocacheSearch.get("facetResults");
        if (facetResults == null || facetResults.isEmpty()) {
            return buildBreakdown(biocacheSearch, recordsShareable, dataResourcesWithData, shareable,
                    licenceIssues, notShareable, providedByGBIF, notShareableNoOwner,
                    linkedToDataProvider, linkedToInstitution);
        }

        List<Map<String, Object>> fieldResults = (List<Map<String, Object>>) facetResults.get(0).get("fieldResult");
        if (fieldResults == null) {
            return buildBreakdown(biocacheSearch, recordsShareable, dataResourcesWithData, shareable,
                    licenceIssues, notShareable, providedByGBIF, notShareableNoOwner,
                    linkedToDataProvider, linkedToInstitution);
        }

        for (Map<String, Object> result : fieldResults) {
            String fq = (String) result.get("fq");
            String uid = fq.replaceAll("\"", "")
                    .replaceAll("dataResourceUid:", "")
                    .replaceAll("data_resource_uid:", "");
            Object count = result.get("count");

            boolean isShareable = true;

            log.info("Looking up data resource {}", uid);
            Optional<DataResource> drOpt = dataResourceRepository.findByUid(uid);
            if (drOpt.isPresent()) {
                DataResource dataResource = drOpt.get();
                dataResourcesWithData.put(dataResource.getUid(), count);

                // Find links to institutions
                Institution institution = dataResource.getInstitution();
                if (institution != null) {
                    linkedToInstitution.put(dataResource.getUid(), institution);
                } else {
                    Set<Institution> consumerInstitutions = dataResource.getConsumerInstitutions();
                    if (consumerInstitutions != null && !consumerInstitutions.isEmpty()) {
                        institution = consumerInstitutions.iterator().next();
                        linkedToInstitution.put(dataResource.getUid(), institution);
                    }
                }

                if (institution == null) {
                    DataProvider dataProvider = dataResource.getDataProvider();
                    if (dataProvider != null) {
                        linkedToDataProvider.put(dataResource.getUid(), dataProvider);
                    } else {
                        if (appProperties.getGbifOrphansPublisherID() == null || appProperties.getGbifOrphansPublisherID().isEmpty()) {
                            notShareableNoOwner.put(dataResource.getUid(), count);
                            isShareable = false;
                        }
                    }
                }

                if (dataResource.getLicenseType() == null || getGBIFCompatibleLicence(dataResource.getLicenseType()) == null) {
                    licenceIssues.put(dataResource.getUid(), count);
                    isShareable = false;
                }

                if (dataResource.getIsShareableWithGBIF() != null && !dataResource.getIsShareableWithGBIF()) {
                    notShareable.put(dataResource.getUid(), count);
                    isShareable = false;
                }

                if (dataResource.getGbifDataset() != null && dataResource.getGbifDataset()) {
                    providedByGBIF.put(dataResource.getUid(), count);
                    isShareable = false;
                }

                if (isShareable) {
                    shareable.put(dataResource.getUid(), count);
                    recordsShareable += ((Number) count).longValue();
                }
            }
        }

        return buildBreakdown(biocacheSearch, recordsShareable, dataResourcesWithData, shareable,
                licenceIssues, notShareable, providedByGBIF, notShareableNoOwner,
                linkedToDataProvider, linkedToInstitution);
    }

    // -----------------------------------------------------------------------
    // Private methods
    // -----------------------------------------------------------------------

    /**
     * Update the registration metadata.
     */
    private boolean updateRegistrationMetadata(ProviderGroup pg) {
        log.info("Updating GBIF organisation {}: {}", pg.getUid(), pg.getGbifRegistryKey());

        boolean success = false;

        Map<String, Object> organisation = loadOrganization(pg.getGbifRegistryKey());
        if (organisation == null || organisation.isEmpty()) {
            return false;
        }

        populateOrganisation(organisation, pg);

        if (!isDryRun()) {
            String url = appProperties.getGbifApiUrl() +
                    MessageFormat.format(API_ORGANIZATION_DETAIL, pg.getGbifRegistryKey());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(organisation, jsonHeaders());

            try {
                ResponseEntity<String> response = gbifRestTemplate.exchange(url, HttpMethod.PUT, request, String.class);
                if (isSuccess(response.getStatusCode())) {
                    success = true;
                }
            } catch (Exception e) {
                log.error("Error updating GBIF organisation: {}", e.getMessage(), e);
            }
        } else {
            log.info("[DRY-RUN] Registration request for {} - {}", pg.getUid(), pg.getName());
            log.info(toJson(organisation));
        }
        return success;
    }

    /**
     * Syncs the contacts with the GBIF registry.
     */
    @SuppressWarnings("unchecked")
    private void syncContactsForProviderGroup(ProviderGroup dp) {
        Map<String, Object> organisation = loadOrganization(dp.getGbifRegistryKey());
        if (organisation == null || organisation.isEmpty()) {
            log.error("Unable to sync contacts for GBIF id {}", dp.getGbifRegistryKey());
            return;
        }

        // Remove existing contacts from GBIF
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) organisation.get("contacts");
        if (contacts != null && !contacts.isEmpty()) {
            log.info("Removing contacts");
            for (Map<String, Object> contact : contacts) {
                if (!isDryRun()) {
                    String url = appProperties.getGbifApiUrl() +
                            MessageFormat.format(API_ORGANIZATION_CONTACT_DETAIL,
                                    dp.getGbifRegistryKey(), String.valueOf(contact.get("key")));
                    try {
                        gbifRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
                        log.info("Removed contact {}", contact.get("key"));
                    } catch (Exception e) {
                        log.error("Error removing contact: {}", e.getMessage(), e);
                    }
                }
            }
        }

        // Add current contacts from the collectory
        List<ContactFor> contactFors = contactForRepository.findAllByEntityUid(dp.getUid());
        if (contactFors != null && !contactFors.isEmpty()) {
            for (ContactFor cf : contactFors) {
                Contact contact = cf.getContact();
                log.info("Adding contact {}", contact);

                Map<String, Object> gbifContact = new LinkedHashMap<>();
                gbifContact.put("firstName", contact.getFirstName());
                gbifContact.put("lastName", contact.getLastName());
                gbifContact.put("type", "ADMINISTRATIVE_POINT_OF_CONTACT");
                gbifContact.put("email", Collections.singletonList(contact.getEmail()));
                gbifContact.put("phone", Collections.singletonList(contact.getPhone()));

                if (!isDryRun()) {
                    String url = appProperties.getGbifApiUrl() +
                            MessageFormat.format(API_ORGANIZATION_CONTACT, dp.getGbifRegistryKey());
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(gbifContact, jsonHeaders());

                    try {
                        ResponseEntity<String> response = gbifRestTemplate.exchange(url, HttpMethod.POST, request, String.class);
                        if (isSuccess(response.getStatusCode())) {
                            log.info("Added contact");
                        }
                    } catch (Exception e) {
                        log.error("Error adding contact: {}", e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * This creates any missing data resources and updates endpoints for all datasets.
     * Deletions are not propagated at this point instead deferring to the current helpdesk@gbif.org process.
     */
    private void syncDataResourcesForProviderGroup(ProviderGroup dp) {
        if (dp.getGbifRegistryKey() != null && !dp.getGbifRegistryKey().isEmpty()) {
            if (dp instanceof DataProvider) {
                DataProvider dataProvider = (DataProvider) dp;
                Set<DataResource> resources = dataProvider.getResources();
                if (resources != null) {
                    for (DataResource resource : resources) {
                        boolean skipSync = false;

                        if (resource.getInstitution() != null) {
                            log.warn("{} is sourced from an institution [{}]... not syncing",
                                    resource.getUid(), resource.getInstitution().getUid());
                            skipSync = true;
                        }

                        if (resource.getConsumerInstitutions() != null && !resource.getConsumerInstitutions().isEmpty()) {
                            skipSync = true;
                            log.warn("{} is linked to an institution... not syncing", resource.getUid());
                        }

                        if (!skipSync) {
                            syncDataResource(resource, dp.getGbifRegistryKey());
                        }
                    }
                }
            } else if (dp instanceof Institution) {
                Institution inst = (Institution) dp;
                // DataResources linked via consumerInstitutions join table
                List<DataResource> consumerResources = dataResourceRepository.findAllByConsumerInstitutionsContaining(inst);
                // DataResources linked via direct institution FK
                List<DataResource> directResources = dataResourceRepository.findAllByInstitution(inst);
                // Merge, deduplicate
                java.util.Set<Long> seen = new java.util.LinkedHashSet<>();
                java.util.List<DataResource> instResources = new java.util.ArrayList<>();
                for (DataResource r : consumerResources) {
                    if (seen.add(r.getId())) instResources.add(r);
                }
                for (DataResource r : directResources) {
                    if (seen.add(r.getId())) instResources.add(r);
                }
                for (DataResource resource : instResources) {
                    syncDataResource(resource, inst.getGbifRegistryKey());
                }
            }
        } else {
            log.warn("Not syncing resources for {}. Not registered with GBIF....", dp.getUid());
        }
    }

    /**
     * Checks that the GBIF registry holds the single endpoint for the data resource,
     * creating or updating if required.
     */
    @SuppressWarnings("unchecked")
    private void syncEndpoints(DataResource resource) {
        if (resource.getGbifRegistryKey() == null || resource.getGbifRegistryKey().isEmpty()) {
            log.info("Registry key not set for resource: {}. Not syncing.....", resource.getUid());
            return;
        }

        log.info("Syncing endpoints for resource[{}], gbifKey[{}]", resource.getId(), resource.getGbifRegistryKey());

        try {
            log.info("Syncing endpoints for resource[{}]... loading dataset", resource.getId());
            Map<String, Object> dataset = loadDataset(resource.getGbifRegistryKey());

            if (dataset == null || dataset.isEmpty()) {
                log.info("Unable to load dataset info from GBIF with registry key {}, for resource {}. Not syncing.....",
                        resource.getGbifRegistryKey(), resource.getUid());
                return;
            }

            if (!isDryRun()) {
                String exportUrlTemplate = appProperties.getGbifExportUrlTemplate();
                String dwcaUrl = exportUrlTemplate != null ? exportUrlTemplate.replaceAll("@UID@", resource.getUid()) : "";

                List<Map<String, Object>> endpoints = (List<Map<String, Object>>) dataset.get("endpoints");

                if (endpoints != null && endpoints.size() == 1 && dwcaUrl.equals(endpoints.get(0).get("url"))) {
                    log.info("Dataset[{}] has correct URL[{}]", resource.getGbifRegistryKey(), dwcaUrl);
                } else {
                    // Delete existing endpoints
                    if (endpoints != null && !endpoints.isEmpty()) {
                        for (Map<String, Object> endpoint : endpoints) {
                            String deleteUrl = appProperties.getGbifApiUrl() +
                                    MessageFormat.format(API_DATASET_ENDPOINT_DETAIL,
                                            resource.getGbifRegistryKey(), String.valueOf(endpoint.get("key")));
                            try {
                                ResponseEntity<String> response = gbifRestTemplate.exchange(
                                        deleteUrl, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
                                if (isSuccess(response.getStatusCode())) {
                                    log.info("Removed endpoint {}", endpoint.get("key"));
                                } else {
                                    log.warn("Unable to remove endpoint {} - {}", endpoint.get("key"), response.getStatusCode());
                                }
                            } catch (Exception e) {
                                log.error("Error removing endpoint: {}", e.getMessage(), e);
                            }
                        }
                    } else {
                        log.warn("No endpoints to create for Dataset[{}] with URL[{}]",
                                resource.getGbifRegistryKey(), resource.getUid());
                    }

                    // Add the correct endpoint
                    Map<String, Object> endpointData = new LinkedHashMap<>();
                    endpointData.put("type", "DWC_ARCHIVE");
                    endpointData.put("url", dwcaUrl);

                    String postUrl = appProperties.getGbifApiUrl() +
                            MessageFormat.format(API_DATASET_ENDPOINT, resource.getGbifRegistryKey());
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(endpointData, jsonHeaders());

                    try {
                        log.info("Creating endpoint for Dataset[{}] with URL[{}]", resource.getGbifRegistryKey(), dwcaUrl);
                        ResponseEntity<String> response = gbifRestTemplate.exchange(postUrl, HttpMethod.POST, request, String.class);
                        if (isSuccess(response.getStatusCode())) {
                            log.info("Created endpoint for Dataset[{}] with URL[{}]", resource.getGbifRegistryKey(), dwcaUrl);
                        } else {
                            log.info("Error creating endpoint for Dataset[{}] with URL[{}] - {}",
                                    resource.getGbifRegistryKey(), dwcaUrl, response.getStatusCode());
                        }
                    } catch (Exception e) {
                        log.error("Error creating endpoint: {}", e.getMessage(), e);
                    }
                }
            } else {
                log.info("[DRYRUN] syncing endpoints with registry key {}, for resource {}",
                        resource.getGbifRegistryKey(), resource.getUid());
            }
        } catch (Exception e) {
            log.info("Error syncing endpoints: {}. Not syncing..... {}", resource.getUid(), e.getMessage(), e);
        }
    }

    /**
     * Creates the content for a dataset to POST to GBIF from the supplied resource or null if it can't be created.
     */
    private Map<String, Object> newGBIFDatasetInstance(DataResource resource, String organisationRegistryKey) {
        String license = getGBIFCompatibleLicence(resource.getLicenseType());
        if (license == null) {
            return null;
        }

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("type", "OCCURRENCE");
        dataset.put("license", license);
        dataset.put("installationKey", appProperties.getGbifInstallationKey());
        dataset.put("publishingOrganizationKey", organisationRegistryKey);
        dataset.put("title", resource.getName());
        dataset.put("description", resource.getPubDescription());
        return dataset;
    }

    /**
     * Takes the values from the ProviderGroup and populates them in the organisation map suitable for the GBIF API.
     */
    private void populateOrganisation(Map<String, Object> organisation, ProviderGroup dp) {
        organisation.put("title", dp.getName());

        // Defensive coding to pass GBIF validation rules (abbreviation max 10 chars)
        if (dp.getAcronym() != null && dp.getAcronym().length() <= 10) {
            organisation.put("abbreviation", dp.getAcronym());
        }

        organisation.put("description", dp.getPubDescription());

        if (dp.getEmail() != null && !dp.getEmail().isEmpty()) {
            organisation.put("email", Collections.singletonList(dp.getEmail()));
        }
        if (dp.getPhone() != null && !dp.getPhone().isEmpty()) {
            organisation.put("phone", Collections.singletonList(dp.getPhone()));
        }
        if (dp.getWebsiteUrl() != null && !dp.getWebsiteUrl().isEmpty()) {
            organisation.put("homepage", Collections.singletonList(dp.getWebsiteUrl()));
        }

        // Latitude/longitude: null out -1 values (sentinel)
        Double lat = dp.getLatitude();
        if (lat != null && lat.intValue() == -1) {
            lat = null;
        }
        organisation.put("latitude", lat);

        Double lon = dp.getLongitude();
        if (lon != null && lon.intValue() == -1) {
            lon = null;
        }
        organisation.put("longitude", lon);

        String logoUrl = dp.buildLogoUrl(appProperties.getServerUrl());
        if (logoUrl != null) {
            organisation.put("logoUrl", logoUrl);
        }

        // Convert the 3-digit ISO code to the 2-digit ISO code GBIF needs.
        // For International organisations `ZZ` is required.
        String country = appProperties.getGbifDefaultEntityCountry() != null ? appProperties.getGbifDefaultEntityCountry() : "";

        String gbifCountryToAttribute = getGbifCountryToAttribute(dp);
        if (gbifCountryToAttribute != null && !gbifCountryToAttribute.isEmpty()) {
            String upperCountry = gbifCountryToAttribute.toUpperCase();
            if (isoCodeService.isIso2Code(upperCountry)) {
                country = gbifCountryToAttribute;
            } else {
                String iso2 = isoCodeService.iso3CountryCodeToIso2CountryCode(upperCountry);
                if (iso2 != null) {
                    log.info("Setting GBIF country of attribution to {}", iso2);
                    country = iso2;
                } else if ("ZZZ".equalsIgnoreCase(gbifCountryToAttribute)) {
                    log.info("Setting GBIF country of attribution to the value for international ZZ");
                    country = "ZZ";
                }
            }
        }
        organisation.put("country", country);

        Address address = dp.getAddress();
        if (address != null) {
            organisation.put("province", address.getState());
            if (address.getStreet() != null) {
                organisation.put("address", Collections.singletonList(address.getStreet()));
            }
            organisation.put("city", address.getCity());
            organisation.put("postalCode", address.getPostcode());
        }
    }

    /**
     * Gets the gbifCountryToAttribute from a ProviderGroup (only DataProvider and Institution have this field).
     */
    private String getGbifCountryToAttribute(ProviderGroup pg) {
        if (pg instanceof DataProvider) {
            return ((DataProvider) pg).getGbifCountryToAttribute();
        } else if (pg instanceof Institution) {
            return ((Institution) pg).getGbifCountryToAttribute();
        }
        return null;
    }

    /**
     * Saves a ProviderGroup to the appropriate repository based on its type.
     */
    private void saveProviderGroup(ProviderGroup pg) {
        if (pg instanceof DataProvider) {
            dataProviderRepository.save((DataProvider) pg);
        } else if (pg instanceof Institution) {
            institutionRepository.save((Institution) pg);
        }
    }

    /**
     * Builds the breakdown map result.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildBreakdown(Map<String, Object> biocacheSearch, long recordsShareable,
                                                Map<String, Object> dataResourcesWithData,
                                                Map<String, Object> shareable,
                                                Map<String, Object> licenceIssues,
                                                Map<String, Object> notShareable,
                                                Map<String, Object> providedByGBIF,
                                                Map<String, Object> notShareableNoOwner,
                                                Map<String, DataProvider> linkedToDataProvider,
                                                Map<String, Institution> linkedToInstitution) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("indexedRecords", biocacheSearch.get("totalRecords"));
        breakdown.put("recordsShareable", recordsShareable);
        breakdown.put("dataResourcesWithData", dataResourcesWithData);
        breakdown.put("shareable", shareable);
        breakdown.put("licenceIssues", licenceIssues);
        breakdown.put("notShareable", notShareable);
        breakdown.put("providedByGBIF", providedByGBIF);
        breakdown.put("notShareableNoOwner", notShareableNoOwner);
        breakdown.put("linkedToDataProvider", linkedToDataProvider);
        breakdown.put("linkedToInstitution", linkedToInstitution);
        return breakdown;
    }

    // -----------------------------------------------------------------------
    // HTTP helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a RestTemplate configured with Basic authentication for the GBIF API.
     */
    private RestTemplate createGbifRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String credentials = appProperties.getGbifApiUser() + ":" + appProperties.getGbifApiPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            return execution.execute(request, body);
        });
        return restTemplate;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private boolean isSuccess(HttpStatusCode statusCode) {
        int code = statusCode.value();
        return code >= 200 && code <= 204;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error serializing to JSON: {}", e.getMessage());
            return obj.toString();
        }
    }
}
