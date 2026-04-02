package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.util.JSONHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CrudService {

    private final IdGeneratorService idGeneratorService;
    private final ProviderGroupService providerGroupService;
    private final DataHubService dataHubService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final InstitutionRepository institutionRepository;
    private final CollectionRepository collectionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final DataHubRepository dataHubRepository;
    private final TempDataResourceRepository tempDataResourceRepository;
    private final ProviderMapRepository providerMapRepository;
    private final ProviderCodeRepository providerCodeRepository;
    private final ExternalIdentifierRepository externalIdentifierRepository;
    private final AttributionRepository attributionRepository;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // --- DataProvider ---

    public Map<String, Object> readDataProvider(DataProvider p) {
        Map<String, Object> result = buildBaseJson(p, "dataProvider");
        result.put("dataResources", briefEntities(p.getResources()));
        if (p.getConsumerCollections() != null || p.getConsumerInstitutions() != null) {
            Set<ProviderGroup> consumers = new HashSet<>();
            if (p.getConsumerCollections() != null) consumers.addAll(p.getConsumerCollections());
            if (p.getConsumerInstitutions() != null) consumers.addAll(p.getConsumerInstitutions());
            if (!consumers.isEmpty()) {
                result.put("linkedRecordConsumers", consumers.stream()
                        .map(c -> Map.of("name", c.getName(), "uri", c.buildUri(appProperties.getServerUrl()), "uid", c.getUid()))
                        .toList());
            }
        }
        addExternalIdentifiers(result, p.getExternalIdentifiers());
        if (p.getHiddenJSON() != null) {
            result.put("hiddenJSON", JSONHelper.parseJson(p.getHiddenJSON()));
        }
        result.put("gbifRegistryKey", p.getGbifRegistryKey());
        return result;
    }

    public DataProvider insertDataProvider(Map<String, Object> obj) {
        DataProvider dp = new DataProvider();
        dp.setUid(idGeneratorService.getNextDataProviderId());
        dp.setGbifCountryToAttribute(appProperties.getGbifDefaultEntityCountry());
        updateBaseProperties(dp, obj);
        updateStringProperty(dp, obj, "hiddenJSON");
        dp.setUserLastModified(getUser(obj));
        return dataProviderRepository.saveAndFlush(dp);
    }

    public DataProvider updateDataProvider(DataProvider dp, Map<String, Object> obj) {
        updateBaseProperties(dp, obj);
        updateStringProperty(dp, obj, "hiddenJSON");
        dp.setUserLastModified(getUser(obj));
        return dataProviderRepository.saveAndFlush(dp);
    }

    // --- DataHub ---

    public Map<String, Object> readDataHub(DataHub p) {
        Map<String, Object> result = buildBaseJson(p, "dataHub");
        result.put("members", JSONHelper.parseJson(p.getMembers()));
        result.put("memberInstitutions", JSONHelper.parseJson(p.getMemberInstitutions()));
        result.put("memberCollections", JSONHelper.parseJson(p.getMemberCollections()));
        result.put("memberDataResources", JSONHelper.parseJson(p.getMemberDataResources()));
        addExternalIdentifiers(result, p.getExternalIdentifiers());
        return result;
    }

    public DataHub insertDataHub(Map<String, Object> obj) {
        DataHub dh = new DataHub();
        dh.setUid(idGeneratorService.getNextDataHubId());
        updateBaseProperties(dh, obj);
        dh.setUserLastModified(getUser(obj));
        return dataHubRepository.saveAndFlush(dh);
    }

    public DataHub updateDataHub(DataHub dh, Map<String, Object> obj) {
        updateBaseProperties(dh, obj);
        updateStringProperty(dh, obj, "memberDataResources");
        dh.setUserLastModified(getUser(obj));
        return dataHubRepository.saveAndFlush(dh);
    }

    // --- DataResource ---

    public Map<String, Object> readDataResource(DataResource p, boolean includeConnectionUrl) {
        Map<String, Object> result = buildBaseJson(p, "dataResource");

        // resource-specific fields
        if (p.getDataProvider() != null) {
            Map<String, Object> dpMap = new java.util.LinkedHashMap<>();
            dpMap.put("name", p.getDataProvider().getName());
            dpMap.put("uri", p.getDataProvider().buildUri(appProperties.getServerUrl()));
            dpMap.put("uid", p.getDataProvider().getUid());
            if (p.getDataProvider().getLogoRef() != null && p.getDataProvider().getLogoRef().getFile() != null) {
                dpMap.put("logoRef", Map.of("file", p.getDataProvider().getLogoRef().getFile()));
            }
            result.put("dataProvider", dpMap);
        }
        if (p.getInstitution() != null) {
            result.put("institution", Map.of(
                    "name", p.getInstitution().getName(),
                    "uri", p.getInstitution().buildUri(appProperties.getServerUrl()),
                    "uid", p.getInstitution().getUid()));
        }

        result.put("rights", p.getRights());
        result.put("licenseType", p.getLicenseType());
        result.put("licenseVersion", p.getLicenseVersion());
        result.put("citation", p.getCitation());
        result.put("resourceType", p.getResourceType());
        result.put("dataGeneralizations", p.getDataGeneralizations());
        result.put("informationWithheld", p.getInformationWithheld());
        result.put("permissionsDocument", p.getPermissionsDocument());
        result.put("permissionsDocumentType", p.getPermissionsDocumentType());
        if ("Data Provider Agreement".equals(p.getPermissionsDocumentType())) {
            result.put("filed", p.isFiled());
            result.put("riskAssessment", p.isRiskAssessment());
        }
        result.put("contentTypes", JSONHelper.parseJson(p.getContentTypes()));

        Set<ProviderGroup> consumers = new HashSet<>();
        if (p.getConsumerCollections() != null) consumers.addAll(p.getConsumerCollections());
        if (p.getConsumerInstitutions() != null) consumers.addAll(p.getConsumerInstitutions());
        if (!consumers.isEmpty()) {
            result.put("linkedRecordConsumers", consumers.stream()
                    .map(c -> Map.of("name", c.getName(), "uri", c.buildUri(appProperties.getServerUrl()), "uid", c.getUid()))
                    .toList());
        }

        if (p.getConnectionParameters() != null && includeConnectionUrl) {
            result.put("connectionParameters", JSONHelper.parseJson(p.getConnectionParameters()));
        } else if (p.getConnectionParameters() != null) {
            Object connParams = JSONHelper.parseJson(p.getConnectionParameters());
            if (connParams instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cp = new LinkedHashMap<>((Map<String, Object>) connParams);
                cp.remove("url");
                result.put("connectionParameters", cp);
            }
        }
        if (p.getImageMetadata() != null) {
            result.put("imageMetadata", JSONHelper.parseJson(p.getImageMetadata()));
        }
        if (p.getDefaultDarwinCoreValues() != null) {
            result.put("defaultDarwinCoreValues", JSONHelper.parseJson(p.getDefaultDarwinCoreValues()));
        }

        result.put("hasMappedCollections", p.getConsumerCollections() != null && !p.getConsumerCollections().isEmpty());
        result.put("status", p.getStatus());
        result.put("provenance", p.getProvenance());
        result.put("harvestFrequency", p.getHarvestFrequency());
        result.put("lastChecked", p.getLastChecked());
        result.put("dataCurrency", p.getDataCurrency());
        result.put("harvestingNotes", p.getHarvestingNotes());
        result.put("publicArchiveAvailable", p.isPublicArchiveAvailable());
        String publicArchiveTemplate = appProperties.getPublicArchiveUrlTemplate();
        if (publicArchiveTemplate != null) {
            result.put("publicArchiveUrl", publicArchiveTemplate.replace("@UID@", p.getUid()));
        }
        String gbifExportTemplate = appProperties.getGbifExportUrlTemplate();
        if (gbifExportTemplate != null) {
            result.put("gbifArchiveUrl", gbifExportTemplate.replace("@UID@", p.getUid()));
        }
        result.put("downloadLimit", p.getDownloadLimit());
        result.put("gbifDataset", p.getGbifDataset());
        result.put("isShareableWithGBIF", p.getIsShareableWithGBIF());
        result.put("verified", isDataResourceVerified(p));
        result.put("hubMembership", formatHubMembership(dataHubService.listDataHubs(), p.getUid(), "dataResource"));
        result.put("gbifRegistryKey", p.getGbifRegistryKey());
        addExternalIdentifiers(result, p.getExternalIdentifiers());
        result.put("doi", p.getGbifDoi());
        result.put("repatriationCountry", p.getRepatriationCountry());
        result.put("createdByID", p.getCreatedByID());
        result.put("dataCollectionProtocolName", p.getDataCollectionProtocolName());
        result.put("dataCollectionProtocolDoc", p.getDataCollectionProtocolDoc());
        result.put("suitableFor", p.getSuitableFor());
        result.put("suitableForOtherDetail", p.getSuitableForOtherDetail());

        return result;
    }

    public DataResource insertDataResource(Map<String, Object> obj) {
        try {
            DataResource dr = new DataResource();
            dr.setUid(idGeneratorService.getNextDataResourceId());
            updateBaseProperties(dr, obj);
            updateDataResourceProperties(dr, obj);
            dr.setUserLastModified(getUser(obj));
            return dataResourceRepository.saveAndFlush(dr);
        } catch (Exception e) {
            log.error("Insert data resource failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public DataResource updateDataResource(DataResource dr, Map<String, Object> obj) {
        updateBaseProperties(dr, obj);
        updateDataResourceProperties(dr, obj);
        dr.setUserLastModified(getUser(obj));
        return dataResourceRepository.saveAndFlush(dr);
    }

    private void updateDataResourceProperties(DataResource dr, Map<String, Object> obj) {
        // String properties
        for (String prop : List.of("rights", "citation", "dataGeneralizations", "informationWithheld",
                "permissionsDocument", "licenseType", "licenseVersion", "status", "mobilisationNotes",
                "provenance", "harvestingNotes", "connectionParameters", "resourceType",
                "permissionsDocumentType", "contentTypes", "defaultDarwinCoreValues", "imageMetadata",
                "geographicDescription", "northBoundingCoordinate", "southBoundingCoordinate",
                "eastBoundingCoordinate", "westBoundingCoordinate", "beginDate", "endDate",
                "qualityControlDescription", "methodStepDescription", "gbifDoi",
                "repatriationCountry", "createdByID", "dataCollectionProtocolName",
                "dataCollectionProtocolDoc", "suitableFor", "suitableForOtherDetail")) {
            updateStringProperty(dr, obj, prop);
        }

        // Number properties
        updateIntProperty(dr, obj, "harvestFrequency");
        updateIntProperty(dr, obj, "downloadLimit");

        // Timestamp properties
        updateTimestampProperty(dr, obj, "lastChecked");
        updateTimestampProperty(dr, obj, "dataCurrency");

        // Boolean properties
        updateBooleanProperty(dr, obj, "gbifDataset");
        updateBooleanProperty(dr, obj, "isShareableWithGBIF");
        updateBooleanProperty(dr, obj, "riskAssessment");
        updateBooleanProperty(dr, obj, "filed");
        updateBooleanProperty(dr, obj, "publicArchiveAvailable");
        updateBooleanProperty(dr, obj, "isPrivate");
        updateBooleanProperty(dr, obj, "makeContactPublic");

        // DataProvider relationship
        if (obj.containsKey("dataProvider")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dpObj = (Map<String, Object>) obj.get("dataProvider");
            if (dpObj != null && dpObj.containsKey("uid")) {
                dataProviderRepository.findByUid((String) dpObj.get("uid"))
                        .ifPresent(dr::setDataProvider);
            }
        }

        // Institution relationship
        if (obj.containsKey("institution")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> instObj = (Map<String, Object>) obj.get("institution");
            if (instObj != null && instObj.containsKey("uid")) {
                institutionRepository.findByUid((String) instObj.get("uid"))
                        .ifPresent(dr::setInstitution);
            }
        }

        // External identifiers
        updateExternalIdentifiers(dr, obj);
    }

    // --- TempDataResource ---

    public Map<String, Object> readTempDataResource(TempDataResource p) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", p.getName());
        result.put("uid", p.getUid());
        result.put("email", p.getEmail());
        result.put("firstName", p.getFirstName());
        result.put("lastName", p.getLastName());
        result.put("alaId", p.getAlaId());
        result.put("dateCreated", p.getDateCreated());
        result.put("lastUpdated", p.getLastUpdated());
        result.put("numberOfRecords", p.getNumberOfRecords());
        result.put("description", p.getDescription());
        result.put("uiUrl", p.getUiUrl());
        result.put("license", p.getLicense());
        result.put("status", p.getStatus());
        result.put("isContactPublic", p.getIsContactPublic());
        result.put("dataGeneralisations", p.getDataGeneralisations());
        result.put("informationWithheld", p.getInformationWithheld());
        result.put("citation", p.getCitation());
        result.put("sourceFile", p.getSourceFile());
        result.put("prodUid", p.getProdUid());
        result.put("keyFields", p.getKeyFields());
        result.put("csvSeparator", p.getCsvSeparator());
        result.put("type", p.getType());
        return result;
    }

    public TempDataResource insertTempDataResource(Map<String, Object> obj) {
        TempDataResource drt = new TempDataResource();
        drt.setUid(idGeneratorService.getNextTempDataResourceId());
        updateTempDataResourceProperties(drt, obj);
        return tempDataResourceRepository.saveAndFlush(drt);
    }

    public TempDataResource updateTempDataResource(TempDataResource drt, Map<String, Object> obj) {
        updateTempDataResourceProperties(drt, obj);
        return tempDataResourceRepository.saveAndFlush(drt);
    }

    private void updateTempDataResourceProperties(TempDataResource drt, Map<String, Object> obj) {
        for (String prop : List.of("firstName", "lastName", "name", "email", "alaId",
                "webserviceUrl", "uiUrl", "description", "status", "dataGeneralisations",
                "informationWithheld", "license", "citation", "sourceFile", "prodUid",
                "keyFields", "csvSeparator")) {
            if (obj.containsKey(prop)) {
                setStringField(drt, prop, obj.get(prop));
            }
        }
        updateIntProperty(drt, obj, "numberOfRecords");
        updateBooleanProperty(drt, obj, "isContactPublic");
    }

    // --- Institution ---

    public Map<String, Object> readInstitution(Institution p) {
        Map<String, Object> result = buildBaseJson(p, "institution");
        result.put("institutionType", p.getInstitutionType());
        // Include a brief abstract (up to 400 chars) for each collection, mirroring makeAbstract(400) in the GSP
        List<Map<String, Object>> collectionBriefs = p.getCollections() == null ? List.of() :
                p.getCollections().stream().map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", c.getName());
                    m.put("uri", c.buildUri(appProperties.getServerUrl()));
                    m.put("uid", c.getUid());
                    m.put("id", c.getId());
                    String desc = c.getPubDescription() != null ? c.getPubDescription()
                            : c.getTechDescription() != null ? c.getTechDescription()
                            : c.getFocus() != null ? c.getFocus() : "";
                    // break at first newline
                    int nl = desc.indexOf('\n');
                    if (nl > 0) desc = desc.substring(0, nl);
                    if (desc.length() > 400) desc = desc.substring(0, 400);
                    m.put("abstract", desc);
                    return m;
                }).toList();
        result.put("collections", collectionBriefs);
        result.put("hubMembership", formatHubMembership(dataHubService.listDataHubs(), p.getUid(), "institution"));

        // M9: linked record providers (DataResources + DataProviders that consume this institution)
        List<Map<String, Object>> linkedProviders = new ArrayList<>();
        dataResourceRepository.findAllByConsumerInstitutionsContaining(p).forEach(dr ->
                linkedProviders.add(Map.of("name", dr.getName(), "uri", dr.buildUri(appProperties.getServerUrl()), "uid", dr.getUid())));
        dataProviderRepository.findAllByConsumerInstitutionsContaining(p).forEach(dp ->
                linkedProviders.add(Map.of("name", dp.getName(), "uri", dp.buildUri(appProperties.getServerUrl()), "uid", dp.getUid())));
        if (!linkedProviders.isEmpty()) {
            result.put("linkedRecordProviders", linkedProviders);
        }

        // M10: parent and child institutions
        List<Institution> parents = institutionRepository.findByChildInstitutionsContaining(p.getUid());
        result.put("parentInstitutions", parents.stream()
                .map(i -> Map.of("name", i.getName(), "uri", i.buildUri(appProperties.getServerUrl()), "uid", i.getUid(), "id", i.getId()))
                .toList());
        List<Map<String, Object>> children = new ArrayList<>();
        if (p.getChildInstitutions() != null && !p.getChildInstitutions().isBlank()) {
            for (String childUid : p.getChildInstitutions().split("\\s+")) {
                if (!childUid.isBlank()) {
                    institutionRepository.findByUid(childUid).ifPresent(child ->
                            children.add(Map.of("name", child.getName(), "uri", child.buildUri(appProperties.getServerUrl()), "uid", child.getUid(), "id", child.getId())));
                }
            }
        }
        result.put("childInstitutions", children);

        result.put("gbifRegistryKey", p.getGbifRegistryKey());
        addExternalIdentifiers(result, p.getExternalIdentifiers());
        return result;
    }

    public Institution insertInstitution(Map<String, Object> obj) {
        Institution inst = new Institution();
        inst.setUid(idGeneratorService.getNextInstitutionId());
        inst.setGbifCountryToAttribute(appProperties.getGbifDefaultEntityCountry());
        updateBaseProperties(inst, obj);
        updateStringProperty(inst, obj, "institutionType");
        inst.setUserLastModified(getUser(obj));
        updateExternalIdentifiers(inst, obj);
        return institutionRepository.saveAndFlush(inst);
    }

    public Institution updateInstitution(Institution inst, Map<String, Object> obj) {
        updateBaseProperties(inst, obj);
        updateStringProperty(inst, obj, "institutionType");
        inst.setUserLastModified(getUser(obj));
        updateExternalIdentifiers(inst, obj);
        return institutionRepository.saveAndFlush(inst);
    }

    // --- Collection ---

    public Map<String, Object> readCollection(au.org.ala.collectory.domain.Collection p) {
        Map<String, Object> result = buildBaseJson(p, "collection");
        result.put("collectionType", JSONHelper.parseJson(p.getCollectionType()));
        result.put("keywords", JSONHelper.parseJson(p.getKeywords()));
        result.put("active", p.getActive());
        // M12: output "not known" when value is -1
        result.put("numRecords", p.getNumRecords() == -1 ? "not known" : p.getNumRecords());
        result.put("numRecordsDigitised", p.getNumRecordsDigitised() == -1 ? "not known" : p.getNumRecordsDigitised());
        result.put("states", p.getStates());
        result.put("geographicDescription", p.getGeographicDescription());
        result.put("startDate", p.getStartDate());
        result.put("endDate", p.getEndDate());
        result.put("kingdomCoverage", p.getKingdomCoverage() != null ?
                Arrays.asList(p.getKingdomCoverage().split(" ")) : null);
        result.put("scientificNames", JSONHelper.parseJson(p.getScientificNames()));
        result.put("subCollections", JSONHelper.parseJson(p.getSubCollections()));

        // M13: geographicRange sub-object (only when not all -1)
        BigDecimal minusOne = BigDecimal.valueOf(-1);
        BigDecimal east = p.getEastCoordinate() != null ? p.getEastCoordinate() : minusOne;
        BigDecimal west = p.getWestCoordinate() != null ? p.getWestCoordinate() : minusOne;
        BigDecimal north = p.getNorthCoordinate() != null ? p.getNorthCoordinate() : minusOne;
        BigDecimal south = p.getSouthCoordinate() != null ? p.getSouthCoordinate() : minusOne;
        if (east.compareTo(minusOne) != 0 || west.compareTo(minusOne) != 0
                || north.compareTo(minusOne) != 0 || south.compareTo(minusOne) != 0) {
            Map<String, Object> geoRange = new LinkedHashMap<>();
            geoRange.put("eastCoordinate", east.compareTo(minusOne) == 0 ? "not known" : east);
            geoRange.put("westCoordinate", west.compareTo(minusOne) == 0 ? "not known" : west);
            geoRange.put("northCoordinate", north.compareTo(minusOne) == 0 ? "not known" : north);
            geoRange.put("southCoordinate", south.compareTo(minusOne) == 0 ? "not known" : south);
            result.put("geographicRange", geoRange);
        }

        if (p.getInstitution() != null) {
            Map<String, Object> instMap = new LinkedHashMap<>();
            instMap.put("name", p.getInstitution().getName());
            instMap.put("uri", p.getInstitution().buildUri(appProperties.getServerUrl()));
            instMap.put("uid", p.getInstitution().getUid());
            instMap.put("websiteUrl", p.getInstitution().getWebsiteUrl() != null ? p.getInstitution().getWebsiteUrl() : "");
            instMap.put("institutionType", p.getInstitution().getInstitutionType() != null ? p.getInstitution().getInstitutionType() : "");
            if (p.getInstitution().getLogoRef() != null && p.getInstitution().getLogoRef().getFile() != null) {
                instMap.put("logoRef", Map.of(
                        "filename", p.getInstitution().getLogoRef().getFile(),
                        "caption", Objects.toString(p.getInstitution().getLogoRef().getCaption(), ""),
                        "copyright", Objects.toString(p.getInstitution().getLogoRef().getCopyright(), ""),
                        "attribution", Objects.toString(p.getInstitution().getLogoRef().getAttribution(), ""),
                        "uri", appProperties.getServerUrl() + "/data/institution/" + p.getInstitution().getLogoRef().getFile()
                ));
            }
            result.put("institution", instMap);
        }

        // M11: recordsProviderMapping
        if (p.getProviderMap() != null) {
            ProviderMap pm = p.getProviderMap();
            Map<String, Object> rpm = new LinkedHashMap<>();
            rpm.put("collectionCodes", pm.getCollectionCodes().stream().map(ProviderCode::getCode).toList());
            rpm.put("institutionCodes", pm.getInstitutionCodes().stream().map(ProviderCode::getCode).toList());
            rpm.put("matchAnyCollectionCode", pm.isMatchAnyCollectionCode());
            rpm.put("exact", pm.isExact());
            rpm.put("warning", pm.getWarning());
            rpm.put("dateCreated", pm.getDateCreated());
            rpm.put("lastUpdated", pm.getLastUpdated());
            result.put("recordsProviderMapping", rpm);
        }

        // M9: linked record providers (DataResources + DataProviders that consume this collection)
        List<Map<String, Object>> linkedProviders = new ArrayList<>();
        dataResourceRepository.findAllByConsumerCollectionsContaining(p).forEach(dr ->
                linkedProviders.add(Map.of("name", dr.getName(), "uri", dr.buildUri(appProperties.getServerUrl()), "uid", dr.getUid())));
        dataProviderRepository.findAllByConsumerCollectionsContaining(p).forEach(dp ->
                linkedProviders.add(Map.of("name", dp.getName(), "uri", dp.buildUri(appProperties.getServerUrl()), "uid", dp.getUid())));
        if (!linkedProviders.isEmpty()) {
            result.put("linkedRecordProviders", linkedProviders);
        }

        // M6: hub membership
        result.put("hubMembership", formatHubMembership(dataHubService.listDataHubs(), p.getUid(), "collection"));

        result.put("gbifRegistryKey", p.getGbifRegistryKey());
        addExternalIdentifiers(result, p.getExternalIdentifiers());
        return result;
    }

    public au.org.ala.collectory.domain.Collection insertCollection(Map<String, Object> obj) {
        au.org.ala.collectory.domain.Collection co = new au.org.ala.collectory.domain.Collection();
        co.setUid(idGeneratorService.getNextCollectionId());
        updateBaseProperties(co, obj);
        updateCollectionProperties(co, obj);
        co.setUserLastModified(getUser(obj));
        return collectionRepository.saveAndFlush(co);
    }

    public au.org.ala.collectory.domain.Collection updateCollection(au.org.ala.collectory.domain.Collection co, Map<String, Object> obj) {
        updateBaseProperties(co, obj);
        updateCollectionProperties(co, obj);
        co.setUserLastModified(getUser(obj));
        return collectionRepository.saveAndFlush(co);
    }

    private void updateCollectionProperties(au.org.ala.collectory.domain.Collection co, Map<String, Object> obj) {
        for (String prop : List.of("collectionType", "keywords", "active", "states",
                "geographicDescription", "startDate", "endDate", "kingdomCoverage",
                "scientificNames", "subCollections")) {
            updateStringProperty(co, obj, prop);
        }
        updateIntProperty(co, obj, "numRecords");
        updateIntProperty(co, obj, "numRecordsDigitised");
        updateDecimalProperty(co, obj, "eastCoordinate");
        updateDecimalProperty(co, obj, "westCoordinate");
        updateDecimalProperty(co, obj, "northCoordinate");
        updateDecimalProperty(co, obj, "southCoordinate");

        // Institution relationship
        if (obj.containsKey("institution")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> instObj = (Map<String, Object>) obj.get("institution");
            if (instObj != null && instObj.containsKey("uid")) {
                institutionRepository.findByUid((String) instObj.get("uid"))
                        .ifPresent(co::setInstitution);
            }
        }

        updateExternalIdentifiers(co, obj);
    }

    // --- Common helpers ---

    private Map<String, Object> buildBaseJson(ProviderGroup p, String entityUrlForm) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", p.getName());
        result.put("acronym", p.getAcronym());
        result.put("uid", p.getUid());
        result.put("guid", p.getGuid());

        if (p.getAddress() != null) {
            Map<String, Object> addr = new LinkedHashMap<>();
            addr.put("street", p.getAddress().getStreet());
            addr.put("city", p.getAddress().getCity());
            addr.put("state", p.getAddress().getState());
            addr.put("postcode", p.getAddress().getPostcode());
            addr.put("country", p.getAddress().getCountry());
            addr.put("postBox", p.getAddress().getPostBox());
            result.put("address", addr);
        }

        result.put("phone", p.getPhone());
        result.put("email", p.getEmail());
        result.put("pubShortDescription", p.getPubShortDescription());
        result.put("pubDescription", p.getPubDescription());
        result.put("techDescription", p.getTechDescription());
        result.put("focus", p.getFocus());

        if (p.getLatitude() != null && p.getLatitude() != -1.0) {
            result.put("latitude", p.getLatitude());
        }
        if (p.getLongitude() != null && p.getLongitude() != -1.0) {
            result.put("longitude", p.getLongitude());
        }

        result.put("state", p.getState());
        result.put("websiteUrl", p.getWebsiteUrl());
        result.put("alaPublicUrl", p.buildPublicUrl(appProperties.getServerUrl()));

        if (p.getImageRef() != null && p.getImageRef().getFile() != null) {
            result.put("imageRef", Map.of(
                    "filename", p.getImageRef().getFile(),
                    "caption", Objects.toString(p.getImageRef().getCaption(), ""),
                    "copyright", Objects.toString(p.getImageRef().getCopyright(), ""),
                    "attribution", Objects.toString(p.getImageRef().getAttribution(), ""),
                    "uri", appProperties.getServerUrl() + "/data/" + entityUrlForm + "/" + p.getImageRef().getFile()
            ));
        }
        if (p.getLogoRef() != null && p.getLogoRef().getFile() != null) {
            result.put("logoRef", Map.of(
                    "filename", p.getLogoRef().getFile(),
                    "caption", Objects.toString(p.getLogoRef().getCaption(), ""),
                    "copyright", Objects.toString(p.getLogoRef().getCopyright(), ""),
                    "attribution", Objects.toString(p.getLogoRef().getAttribution(), ""),
                    "uri", appProperties.getServerUrl() + "/data/" + entityUrlForm + "/" + p.getLogoRef().getFile()
            ));
        }

        result.put("networkMembership", expandNetworkMembership(p.getNetworkMembership()));
        result.put("attributions", formatAttributions(p.getAttributions()));
        result.put("taxonomyCoverageHints", JSONHelper.taxonomyHints(p.getTaxonomyHints()));
        result.put("dateCreated", p.getDateCreated());
        result.put("lastUpdated", p.getLastUpdated());
        result.put("userLastModified", p.getUserLastModified());

        return result;
    }

    private void updateBaseProperties(ProviderGroup pg, Map<String, Object> obj) {
        for (String prop : List.of("guid", "name", "acronym", "phone", "email",
                "pubShortDescription", "pubDescription", "techDescription", "notes",
                "focus", "websiteUrl", "networkMembership", "altitude", "gbifRegistryKey")) {
            updateStringProperty(pg, obj, prop);
        }

        if (obj.containsKey("isALAPartner")) {
            pg.setALAPartner(toBool(obj.get("isALAPartner")));
        }

        if (obj.containsKey("latitude")) {
            pg.setLatitude(toDouble(obj.get("latitude")));
        }
        if (obj.containsKey("longitude")) {
            pg.setLongitude(toDouble(obj.get("longitude")));
        }
        if (obj.containsKey("state")) {
            pg.setState(asString(obj.get("state")));
        }

        // Embedded address
        if (obj.containsKey("address")) {
            Object addrObj = obj.get("address");
            if (addrObj == null) {
                pg.setAddress(null);
            } else if (addrObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> addrMap = (Map<String, Object>) addrObj;
                Address addr = pg.getAddress() != null ? pg.getAddress() : new Address();
                if (addrMap.containsKey("street")) addr.setStreet(asString(addrMap.get("street")));
                if (addrMap.containsKey("postBox")) addr.setPostBox(asString(addrMap.get("postBox")));
                if (addrMap.containsKey("city")) addr.setCity(asString(addrMap.get("city")));
                if (addrMap.containsKey("state")) addr.setState(asString(addrMap.get("state")));
                if (addrMap.containsKey("postcode")) addr.setPostcode(asString(addrMap.get("postcode")));
                if (addrMap.containsKey("country")) addr.setCountry(asString(addrMap.get("country")));
                pg.setAddress(addr);
            }
        }

        // Embedded images
        if (obj.containsKey("imageRef")) {
            Object imgObj = obj.get("imageRef");
            if (imgObj == null) {
                pg.setImageRef(null);
            } else if (imgObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> imgMap = (Map<String, Object>) imgObj;
                Image img = pg.getImageRef() != null ? pg.getImageRef() : new Image();
                if (imgMap.containsKey("file")) img.setFile(asString(imgMap.get("file")));
                if (imgMap.containsKey("caption")) img.setCaption(asString(imgMap.get("caption")));
                if (imgMap.containsKey("attribution")) img.setAttribution(asString(imgMap.get("attribution")));
                if (imgMap.containsKey("copyright")) img.setCopyright(asString(imgMap.get("copyright")));
                pg.setImageRef(img);
            }
        }
        if (obj.containsKey("logoRef")) {
            Object logoObj = obj.get("logoRef");
            if (logoObj == null) {
                pg.setLogoRef(null);
            } else if (logoObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> logoMap = (Map<String, Object>) logoObj;
                Image logo = pg.getLogoRef() != null ? pg.getLogoRef() : new Image();
                if (logoMap.containsKey("file")) logo.setFile(asString(logoMap.get("file")));
                if (logoMap.containsKey("caption")) logo.setCaption(asString(logoMap.get("caption")));
                if (logoMap.containsKey("attribution")) logo.setAttribution(asString(logoMap.get("attribution")));
                if (logoMap.containsKey("copyright")) logo.setCopyright(asString(logoMap.get("copyright")));
                pg.setLogoRef(logo);
            }
        }

        // Attributions
        if (obj.containsKey("attributions")) {
            pg.setAttributions(asString(obj.get("attributions")));
        }
    }

    @SuppressWarnings("unchecked")
    private void updateExternalIdentifiers(ProviderGroup pg, Map<String, Object> obj) {
        if (obj.containsKey("externalIdentifiers") && obj.get("externalIdentifiers") instanceof List) {
            List<Map<String, String>> extIds = (List<Map<String, String>>) obj.get("externalIdentifiers");
            providerGroupService.updateExternalIdentifiers(pg, extIds);
        }
    }

    private List<Map<String, Object>> briefEntities(java.util.Collection<? extends ProviderGroup> entities) {
        if (entities == null) return List.of();
        return entities.stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getName());
                    m.put("uri", e.buildUri(appProperties.getServerUrl()));
                    m.put("uid", e.getUid());
                    m.put("id", e.getId());
                    return m;
                })
                .toList();
    }

    private void addExternalIdentifiers(Map<String, Object> result, Set<ExternalIdentifier> externalIdentifiers) {
        if (externalIdentifiers != null && !externalIdentifiers.isEmpty()) {
            result.put("externalIdentifiers", externalIdentifiers.stream()
                    .map(e -> Map.of("source", e.getSource(), "identifier", e.getIdentifier(),
                            "uri", Objects.toString(e.getUri(), "")))
                    .toList());
        }
    }

    // --- Property setters using reflection-free approach ---

    private void updateStringProperty(Object target, Map<String, Object> obj, String prop) {
        if (!obj.containsKey(prop)) return;
        Object val = obj.get(prop);
        setStringField(target, prop, val);
    }

    private void setStringField(Object target, String fieldName, Object value) {
        String strValue = value != null && !"null".equals(value.toString()) ? value.toString() : null;
        try {
            // Convert JSON arrays/objects to string if needed
            if (value instanceof List || value instanceof Map) {
                strValue = JSONHelper.toJsonString(value);
            }
            java.lang.reflect.Method setter = findSetter(target.getClass(), fieldName);
            if (setter != null) {
                setter.invoke(target, strValue);
            }
        } catch (Exception e) {
            log.debug("Could not set field {} on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void updateIntProperty(Object target, Map<String, Object> obj, String prop) {
        if (!obj.containsKey(prop)) return;
        try {
            java.lang.reflect.Method setter = findSetter(target.getClass(), prop);
            if (setter != null) {
                Object val = obj.get(prop);
                int intVal = val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
                setter.invoke(target, intVal);
            }
        } catch (Exception e) {
            log.debug("Could not set int field {}: {}", prop, e.getMessage());
        }
    }

    private void updateDecimalProperty(Object target, Map<String, Object> obj, String prop) {
        if (!obj.containsKey(prop)) return;
        try {
            java.lang.reflect.Method setter = findSetter(target.getClass(), prop);
            if (setter != null) {
                Object val = obj.get(prop);
                double dbl = val instanceof Number ? ((Number) val).doubleValue()
                        : Double.parseDouble(val.toString());
                Class<?> paramType = setter.getParameterTypes()[0];
                if (paramType == BigDecimal.class) {
                    setter.invoke(target, BigDecimal.valueOf(dbl));
                } else {
                    setter.invoke(target, dbl);
                }
            }
        } catch (Exception e) {
            log.debug("Could not set decimal field {}: {}", prop, e.getMessage());
        }
    }

    private void updateBooleanProperty(Object target, Map<String, Object> obj, String prop) {
        if (!obj.containsKey(prop)) return;
        try {
            java.lang.reflect.Method setter = findSetter(target.getClass(), prop);
            if (setter != null) {
                boolean val = toBool(obj.get(prop));
                Class<?> paramType = setter.getParameterTypes()[0];
                if (paramType == Boolean.class) {
                    setter.invoke(target, (Boolean) val);
                } else {
                    setter.invoke(target, val);
                }
            }
        } catch (Exception e) {
            log.debug("Could not set boolean field {}: {}", prop, e.getMessage());
        }
    }

    private void updateTimestampProperty(Object target, Map<String, Object> obj, String prop) {
        if (!obj.containsKey(prop)) return;
        try {
            java.lang.reflect.Method setter = findSetter(target.getClass(), prop);
            if (setter != null) {
                Object val = obj.get(prop);
                LocalDateTime ts;
                if ("now".equals(val)) {
                    ts = LocalDateTime.now();
                } else if (val instanceof String s) {
                    ts = LocalDateTime.parse(s, TIMESTAMP_FORMAT);
                } else {
                    return;
                }
                setter.invoke(target, ts);
            }
        } catch (Exception e) {
            log.debug("Could not set timestamp field {}: {}", prop, e.getMessage());
        }
    }

    private java.lang.reflect.Method findSetter(Class<?> clazz, String fieldName) {
        String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    private String getUser(Map<String, Object> obj) {
        Object user = obj.get("user");
        return user != null ? user.toString() : "Data services";
    }

    private String asString(Object val) {
        if (val == null || "null".equals(val.toString())) return null;
        return val.toString();
    }

    private boolean toBool(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private Double toDouble(Object val) {
        if (val == null || "null".equals(val.toString())) return -1.0;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    // --- M6: hub membership formatting ---

    private List<Map<String, Object>> formatHubMembership(List<DataHub> hubs, String uid, String memberType) {
        if (hubs == null) return List.of();
        return hubs.stream()
                .filter(h -> switch (memberType) {
                    case "dataResource" -> isJsonArrayContaining(h.getMemberDataResources(), uid);
                    case "institution"  -> isJsonArrayContaining(h.getMemberInstitutions(), uid);
                    case "collection"   -> isJsonArrayContaining(h.getMemberCollections(), uid);
                    default -> false;
                })
                .map(h -> Map.<String, Object>of(
                        "uid", h.getUid(),
                        "name", h.getName(),
                        "uri", h.buildUri(appProperties.getServerUrl())))
                .toList();
    }

    private boolean isJsonArrayContaining(String jsonArray, String value) {
        if (jsonArray == null || jsonArray.isBlank()) return false;
        try {
            List<String> list = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
            return list.contains(value);
        } catch (Exception e) {
            return false;
        }
    }

    // --- M14: attributions formatting ---

    private List<Map<String, Object>> formatAttributions(String attributionsStr) {
        if (attributionsStr == null || attributionsStr.isBlank()) return List.of();
        List<String> uids = Arrays.asList(attributionsStr.trim().split("\\s+"));
        return attributionRepository.findAllByUidIn(uids).stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", a.getName());
                    m.put("url", a.getUrl());
                    return m;
                })
                .toList();
    }

    // --- M15: network membership expansion ---

    private static final Map<String, Map<String, String>> NETWORK_DETAILS = Map.of(
            "CHAFC", Map.of("name", "Council of Heads of Australian Faunal Collections", "logo", "/data/network/CHAFC_sm.jpg"),
            "CHAEC", Map.of("name", "Council of Heads of Australian Entomological Collections", "logo", "/data/network/chaec-logo.png"),
            "CHAH",  Map.of("name", "Council of Heads of Australasian Herbaria", "logo", "/data/network/CHAH_logo_col_70px_white.gif"),
            "CHACM", Map.of("name", "Council of Heads of Australian Collections of Microorganisms", "logo", "/data/network/chacm.png")
    );

    private Object expandNetworkMembership(String networkMembership) {
        if (networkMembership == null || networkMembership.isBlank()) return null;
        try {
            List<String> acronyms = objectMapper.readValue(networkMembership, new TypeReference<List<String>>() {});
            return acronyms.stream().map(acronym -> {
                Map<String, String> details = NETWORK_DETAILS.get(acronym);
                if (details != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", details.get("name"));
                    m.put("acronym", acronym);
                    m.put("logo", appProperties.getServerUrl() + details.get("logo"));
                    return m;
                } else {
                    return Map.<String, Object>of("acronym", acronym);
                }
            }).toList();
        } catch (Exception e) {
            log.debug("Could not parse networkMembership: {}", e.getMessage());
            return JSONHelper.parseJson(networkMembership);
        }
    }

    // --- M8: data resource verified check ---

    public boolean isDataResourceVerified(DataResource dr) {
        if (dr.getDefaultDarwinCoreValues() == null) return false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = objectMapper.readValue(dr.getDefaultDarwinCoreValues(), Map.class);
            return "verified".equals(values.get("georeferenceVerificationStatus"))
                    && "verified".equals(values.get("identificationVerificationStatus"));
        } catch (Exception e) {
            return false;
        }
    }
}
