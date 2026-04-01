package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collect datasets from an IPT service and update the metadata.
 * <p>
 * The IPT service is associated with a {@link DataProvider} that identifies the sources of the service.
 * When invoked, the service scans the RSS feed supplied by the service and uses it to identify new and
 * updated {@link DataResource}s. New datasets are collected and then supplied to the collectory for
 * loading.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IptService {

    private final IdGeneratorService idGeneratorService;
    private final EmlImportService emlImportService;
    private final CollectoryAuthService collectoryAuthService;
    private final ActivityLogService activityLogService;
    private final DataResourceRepository dataResourceRepository;
    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;

    /** The IPT XML namespace URIs */
    private static final String NS_IPT = "http://ipt.gbif.org/";
    private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    private static final String NS_FOAF = "http://xmlns.com/foaf/0.1/";
    private static final String NS_GEO = "http://www.w3.org/2003/01/geo/wgs84_pos#";
    private static final String NS_EML = "eml://ecoinformatics.org/eml-2.1.1";

    /** Source of the RSS feed */
    private static final String RSS_PATH = "rss.do";

    /** Parse RFC 822 date/times */
    private static final DateTimeFormatter RFC822_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * The field names that can be extracted from RSS items.
     */
    private static final Set<String> RSS_FIELD_NAMES = Set.of(
            "guid", "name", "pubDescription", "websiteUrl", "dataCurrency",
            "lastChecked", "provenance", "contentTypes", "gbifRegistryKey"
    );

    /**
     * The EML field names that are handled by the EmlImportService.
     * These correspond to the fields extracted in extractEmlFields / extractContactsFromEml.
     */
    private static final Set<String> EML_FIELD_NAMES = Set.of(
            "guid", "pubDescription", "name", "email", "rights", "citation", "state", "phone",
            "geographicDescription", "northBoundingCoordinate", "southBoundingCoordinate",
            "eastBoundingCoordinate", "westBoundingCoordinate", "beginDate", "endDate",
            "purpose", "methodStepDescription", "qualityControlDescription",
            "gbifDoi", "licenseType", "licenseVersion"
    );

    /**
     * Returns the union of RSS field names and EML field names.
     */
    public Set<String> allFields() {
        Set<String> all = new LinkedHashSet<>(RSS_FIELD_NAMES);
        all.addAll(EML_FIELD_NAMES);
        return all;
    }

    /**
     * See what needs updating for a data provider.
     *
     * @param provider       The provider
     * @param create         Update existing datasets and create data resources for new datasets
     * @param check          Check to see whether a resource needs updating by looking at the data currency
     * @param keyName        The term to use as a key when creating new resources
     * @param username       The current user
     * @param admin          Whether the user is an admin
     * @param shareWithGbif  Whether data is shareable with GBIF
     * @return A list of data resources that need re-loading
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Map<String, Object>> scan(DataProvider provider, boolean create, boolean check,
                                          String keyName, String username, boolean admin,
                                          boolean shareWithGbif) {
        activityLogService.log(username, admin, provider.getUid(), Action.SCAN);
        List<Map<String, Object>> updates = rss(provider, keyName, shareWithGbif);
        return merge(provider, updates, create, check, username, admin);
    }

    /**
     * Merge the datasets known to the data provider with a list of updates.
     *
     * @param provider The provider
     * @param updates  The updates from the RSS feed
     * @param create   Update existing data resources and create any new resources
     * @param check    Check against existing data for currency
     * @param username The current user
     * @param admin    Whether the user is an admin
     * @return A list of merged data resources
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Map<String, Object>> merge(DataProvider provider, List<Map<String, Object>> updates,
                                           boolean create, boolean check,
                                           String username, boolean admin) {
        // Index current resources by websiteUrl
        Map<String, DataResource> currentResources = new HashMap<>();
        if (provider.getResources() != null) {
            for (DataResource resource : provider.getResources()) {
                if (resource.getWebsiteUrl() != null) {
                    currentResources.put(resource.getWebsiteUrl(), resource);
                }
            }
        }

        List<Map<String, Object>> mergedResources = new ArrayList<>();

        for (Map<String, Object> update : updates) {
            @SuppressWarnings("unchecked")
            DataResource updateResource = (DataResource) update.get("resource");
            DataResource existingResource = currentResources.get(updateResource.getWebsiteUrl());

            if (existingResource != null) {
                if (!check
                        || existingResource.getDataCurrency() == null
                        || updateResource.getDataCurrency() == null
                        || updateResource.getDataCurrency().isAfter(existingResource.getDataCurrency())) {
                    updateFields(existingResource, update, username);
                    @SuppressWarnings("unchecked")
                    List<Contact> contacts = (List<Contact>) update.get("contacts");
                    @SuppressWarnings("unchecked")
                    List<Contact> primaryContacts = (List<Contact>) update.get("primaryContacts");
                    syncContacts(existingResource, contacts, primaryContacts, username, admin);
                    activityLogService.log(username, admin, Action.EDIT_SAVE,
                            "Updated IPT data resource " + existingResource.getUid() + " from scan");
                }
                mergedResources.add(Map.of("resource", existingResource));
            } else {
                if (create) {
                    createNewResource(provider, update, username, admin);
                }
                mergedResources.add(update);
            }
        }

        return mergedResources;
    }

    /**
     * Update all fields on an existing resource from a scanned update.
     */
    private void updateFields(DataResource existingResource, Map<String, Object> update, String username) {
        Set<String> fieldsToUpdate = allFields();
        log.info("Fields to update: {}", fieldsToUpdate);

        for (String fieldName : fieldsToUpdate) {
            if (update.containsKey(fieldName)) {
                Object newValue = update.get(fieldName);
                setResourceField(existingResource, fieldName, newValue);
            } else {
                setResourceField(existingResource, fieldName, null);
            }
        }

        existingResource.setUserLastModified(username);
        existingResource.setLastChecked(LocalDateTime.now());

        dataResourceRepository.saveAndFlush(existingResource);
    }

    /**
     * Set a field value on a DataResource using reflection.
     */
    private void setResourceField(DataResource resource, String fieldName, Object value) {
        try {
            String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            for (java.lang.reflect.Method method : resource.getClass().getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    if (value == null) {
                        if (!paramType.isPrimitive()) {
                            method.invoke(resource, (Object) null);
                        }
                    } else if (paramType.isAssignableFrom(value.getClass())) {
                        method.invoke(resource, value);
                    } else if (paramType == String.class) {
                        method.invoke(resource, value.toString());
                    }
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("Could not set field {} on DataResource: {}", fieldName, e.getMessage());
        }
    }

    /**
     * Sync contacts on a resource: add new contacts, remove obsolete ones, delete orphaned contacts.
     * Package-visible so DataImportService can call it after EML import.
     */
    void syncContacts(DataResource resource, List<Contact> newContacts,
                              List<Contact> primaryContacts, String username, boolean admin) {
        if (newContacts == null) newContacts = List.of();
        if (primaryContacts == null) primaryContacts = List.of();

        List<ContactFor> existingContactForEntries = contactForRepository.findAllByEntityUid(resource.getUid());

        // Index current contacts by contact ID
        Map<Long, ContactFor> existingContactsById = new HashMap<>();
        for (ContactFor cf : existingContactForEntries) {
            if (cf.getContact() != null && cf.getContact().getId() != null) {
                existingContactsById.put(cf.getContact().getId(), cf);
            }
        }

        // Index new contacts by ID (only those already persisted)
        Set<Long> newContactIds = newContacts.stream()
                .filter(c -> c.getId() != null)
                .map(Contact::getId)
                .collect(Collectors.toSet());

        // Identify obsolete contacts (in existing but not in new)
        List<ContactFor> obsoleteContactsFor = existingContactForEntries.stream()
                .filter(cf -> cf.getContact() != null && cf.getContact().getId() != null
                        && !newContactIds.contains(cf.getContact().getId()))
                .collect(Collectors.toList());

        // Remove obsolete contact associations
        for (ContactFor contactFor : obsoleteContactsFor) {
            Contact contact = contactFor.getContact();
            contactForRepository.delete(contactFor);
            contactForRepository.flush();

            activityLogService.log(username, admin, Action.DELETE,
                    "Removed obsolete contact " + contact.buildName() + " from resource " + resource.getUid());

            // Delete orphaned contacts (not associated with any other entity)
            List<ContactFor> remaining = contactForRepository.findAllByContact(contact);
            if (remaining.isEmpty()) {
                contactRepository.delete(contact);
                contactRepository.flush();
                activityLogService.log(username, admin, Action.DELETE,
                        "Deleted orphaned contact " + contact.buildName());
            }
        }

        // Add new contact associations
        List<Contact> finalPrimaryContacts = primaryContacts;
        for (Contact newContact : newContacts) {
            Optional<ContactFor> existingAssociation =
                    contactForRepository.findByContactAndEntityUid(newContact, resource.getUid());
            if (existingAssociation.isEmpty()) {
                ContactFor cf = ContactFor.builder()
                        .contact(newContact)
                        .entityUid(resource.getUid())
                        .primaryContact(finalPrimaryContacts.contains(newContact))
                        .administrator(false)
                        .userLastModified(username)
                        .dateCreated(LocalDateTime.now())
                        .dateLastModified(LocalDateTime.now())
                        .build();
                contactForRepository.saveAndFlush(cf);
            } else {
                log.info("Skipping contact {} for resource {} - already associated.",
                        newContact.buildName(), resource.getUid());
            }
        }

        activityLogService.log(username, admin, Action.EDIT_SAVE,
                "Synced contacts for resource " + resource.getUid());
    }

    /**
     * Create a new data resource from a scanned update and associate it with the provider.
     */
    private void createNewResource(DataProvider provider, Map<String, Object> update,
                                   String username, boolean admin) {
        DataResource resource = (DataResource) update.get("resource");
        resource.setUid(idGeneratorService.getNextDataResourceId());
        resource.setUserLastModified(username);
        resource.setDataProvider(provider);

        dataResourceRepository.saveAndFlush(resource);

        @SuppressWarnings("unchecked")
        List<Contact> contacts = (List<Contact>) update.get("contacts");
        @SuppressWarnings("unchecked")
        List<Contact> primaryContacts = (List<Contact>) update.get("primaryContacts");
        syncContacts(resource, contacts, primaryContacts, username, admin);

        activityLogService.log(username, admin, Action.CREATE,
                "Created new IPT data resource for provider " + provider.getUid()
                        + " with uid " + resource.getUid()
                        + " for dataset " + resource.getWebsiteUrl());
    }

    /**
     * Scan an IPT data provider's RSS stream and build a set of datasets.
     * <p>
     * The datasets are not saved unless the need to create a new dataset comes into play.
     *
     * @param provider           The provider
     * @param keyName            The term to use as a key when creating new resources
     * @param isShareableWithGBIF Whether data is shareable with GBIF
     * @return A list of update maps (resource, contacts, primaryContacts)
     */
    public List<Map<String, Object>> rss(DataProvider provider, String keyName, Boolean isShareableWithGBIF) {
        try {
            String url = provider.getWebsiteUrl();
            if (url != null && !url.endsWith("/")) {
                url = url + "/";
            }

            URL base = new URL(url);
            URL rsspath = new URL(base, RSS_PATH);
            log.info("Scanning {} from {}", rsspath, base);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc;
            try (InputStream is = rsspath.openStream()) {
                doc = builder.parse(is);
            }

            List<Map<String, Object>> results = new ArrayList<>();
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                results.add(createDataResource(provider, item, keyName, isShareableWithGBIF));
            }
            return results;
        } catch (Exception e) {
            log.error("Error scanning RSS feed for provider {}: {}", provider.getUid(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Construct a DataResource from an RSS item element.
     *
     * @param provider           The data provider
     * @param rssItem            The RSS item element
     * @param keyName            The term to use as a key when creating new resources
     * @param isShareableWithGBIF Whether data is shareable with GBIF
     * @return A map containing the resource, contacts, and primaryContacts
     */
    private Map<String, Object> createDataResource(DataProvider provider, Element rssItem,
                                                    String keyName, Boolean isShareableWithGBIF) {
        DataResource resource = new DataResource();
        resource.setDataProvider(provider);

        // Extract RSS fields
        resource.setGuid(getElementText(rssItem, "link"));
        resource.setName(getElementText(rssItem, "title"));
        resource.setPubDescription(getElementText(rssItem, "description"));
        resource.setWebsiteUrl(getElementText(rssItem, "link"));

        // dataCurrency from pubDate
        String pubDate = getElementText(rssItem, "pubDate");
        if (pubDate != null && !pubDate.isEmpty()) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(pubDate, RFC822_FORMATTER);
                resource.setDataCurrency(zdt.toLocalDateTime());
            } catch (Exception e) {
                log.warn("Could not parse pubDate '{}': {}", pubDate, e.getMessage());
            }
        }

        resource.setLastChecked(LocalDateTime.now());
        resource.setProvenance("Published dataset");
        resource.setContentTypes("[ \"point occurrence data\" ]");

        // gbifRegistryKey from guid element
        String guid = getElementText(rssItem, "guid");
        if (guid != null && !guid.startsWith("http")) {
            int versionMarker = guid.indexOf("/");
            if (versionMarker > 0) {
                guid = guid.substring(0, versionMarker);
            }
            resource.setGbifRegistryKey(guid);
        }

        // DwCA connection parameters
        String dwca = getElementTextNS(rssItem, NS_IPT, "dwca");
        if (dwca != null && !dwca.isEmpty()) {
            resource.setConnectionParameters(
                    "{ \"protocol\": \"DwCA\", \"url\": \"" + dwca
                            + "\", \"automation\": true, \"termsForUniqueKey\": [ \"" + keyName + "\" ] }");
        }

        resource.setIsShareableWithGBIF(isShareableWithGBIF);

        // Retrieve EML if available
        List<Contact> contacts = new ArrayList<>();
        List<Contact> primaryContacts = new ArrayList<>();
        String eml = getElementTextNS(rssItem, NS_IPT, "eml");
        if (eml != null && !eml.isEmpty()) {
            Map<String, Object> result = retrieveEml(resource, eml);
            if (result != null) {
                @SuppressWarnings("unchecked")
                List<Contact> c = (List<Contact>) result.get("contacts");
                @SuppressWarnings("unchecked")
                List<Contact> pc = (List<Contact>) result.get("primaryContacts");
                if (c != null) contacts = c;
                if (pc != null) primaryContacts = pc;
            }
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("resource", resource);
        resultMap.put("contacts", contacts);
        resultMap.put("primaryContacts", primaryContacts);

        // Also put individual field values into the map for updateFields
        resultMap.put("guid", resource.getGuid());
        resultMap.put("name", resource.getName());
        resultMap.put("pubDescription", resource.getPubDescription());
        resultMap.put("websiteUrl", resource.getWebsiteUrl());
        resultMap.put("dataCurrency", resource.getDataCurrency());
        resultMap.put("lastChecked", resource.getLastChecked());
        resultMap.put("provenance", resource.getProvenance());
        resultMap.put("contentTypes", resource.getContentTypes());
        resultMap.put("gbifRegistryKey", resource.getGbifRegistryKey());

        return resultMap;
    }

    /**
     * Retrieve EML metadata for the dataset and populate the resource description.
     *
     * @param resource The resource
     * @param url      The URL of the EML metadata
     * @return A map with contacts and primaryContacts, or null on error
     */
    private Map<String, Object> retrieveEml(DataResource resource, String url) {
        try {
            log.info("Retrieving EML from {}", url);
            URL emlUrl = new URL(url);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc;
            try (InputStream is = emlUrl.openStream()) {
                doc = builder.parse(is);
            }

            return emlImportService.extractContactsFromEml(doc, resource);
        } catch (Exception e) {
            log.error("Problem retrieving EML from: {}", url, e);
            return Map.of("contacts", List.of(), "primaryContacts", List.of());
        }
    }

    /**
     * Get text content of a direct child element by tag name (non-namespaced).
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    /**
     * Get text content of a namespaced child element.
     */
    private String getElementTextNS(Element parent, String namespaceUri, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespaceUri, localName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }
}
