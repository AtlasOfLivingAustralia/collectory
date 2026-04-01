package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/ws/reports", "/reports"})
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportsController {

    private final CollectionRepository collectionRepository;
    private final InstitutionRepository institutionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final DataHubRepository dataHubRepository;
    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final ProviderMapRepository providerMapRepository;
    private final AuditLogEventRepository auditLogEventRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CollectionService collectionService;
    private final InstitutionService institutionService;
    private final CollectoryAuthService collectoryAuthService;
    private final ActivityLogService activityLogService;
    private final AppProperties appProperties;

    @GetMapping("/data")
    public ResponseEntity<?> dataReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalCollections = collectionRepository.count();
        long totalInstitutions = institutionRepository.count();
        report.put("totalCollections", totalCollections);
        report.put("totalInstitutions", totalInstitutions);
        report.put("totalDataProviders", dataProviderRepository.count());
        report.put("totalDataResources", dataResourceRepository.count());
        report.put("totalDataHubs", dataHubRepository.count());
        report.put("totalContacts", contactRepository.count());

        // Data completeness
        report.put("collectionsWithType", collectionRepository.countWithCollectionType());
        report.put("collectionsWithFocus", collectionRepository.countWithFocus());
        report.put("collectionsWithDescriptions", collectionRepository.countWithDescriptions());
        report.put("collectionsWithKeywords", collectionRepository.countWithKeywords());
        report.put("collectionsWithProviderCodes", providerMapRepository.count());
        report.put("collectionsWithGeoDescription", collectionRepository.countWithGeoDescription());
        report.put("collectionsWithNumRecords", collectionRepository.countWithNumRecords());
        report.put("collectionsWithNumRecordsDigitised", collectionRepository.countWithNumRecordsDigitised());

        // Contact coverage
        report.put("collectionsWithoutContacts", collectionRepository.countWithoutContacts());
        report.put("collectionsWithoutEmailContacts", collectionRepository.countWithoutEmailContacts());
        report.put("institutionsWithoutContacts", institutionRepository.countWithoutContacts());
        report.put("institutionsWithoutEmailContacts", institutionRepository.countWithoutEmailContacts());

        return ResponseEntity.ok(report);
    }

    @GetMapping("/activity")
    public ResponseEntity<?> activityReport() {
        Map<String, Object> report = new LinkedHashMap<>();

        // Curator stats (administratorForEntity=true, admin=false)
        report.put("curatorViews", activityLogRepository.countByActionAndAdministratorForEntityTrueAndAdminFalse(Action.VIEW.toString()));
        report.put("curatorPreviews", activityLogRepository.countByActionAndAdministratorForEntityTrueAndAdminFalse(Action.PREVIEW.toString()));
        report.put("curatorEdits", activityLogRepository.countByActionAndAdministratorForEntityTrueAndAdminFalse(Action.EDIT_SAVE.toString()));

        // Admin stats (administratorForEntity=true, admin=true)
        report.put("adminViews", activityLogRepository.countByActionAndAdministratorForEntityTrueAndAdminTrue(Action.VIEW.toString()));
        report.put("adminPreviews", activityLogRepository.countByActionAndAdministratorForEntityTrueAndAdminTrue(Action.PREVIEW.toString()));
        report.put("adminEdits", activityLogRepository.countByActionAndAdministratorForEntityTrueAndAdminTrue(Action.EDIT_SAVE.toString()));

        // Latest activity
        List<ActivityLog> latest = activityLogRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"))).getContent();
        report.put("latestActivity", latest);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/membership")
    public ResponseEntity<?> membershipReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("partners", institutionRepository.findAllByIsALAPartnerTrue());

        // Network membership lists (collections + institutions for each network)
        report.put("chahMembers", buildNetworkMembers("CHAH"));
        report.put("chaecMembers", buildNetworkMembers("CHAEC"));
        report.put("chafcMembers", buildNetworkMembers("CHAFC"));
        report.put("amrrnMembers", buildNetworkMembers("CHACM"));
        report.put("camdMembers", buildNetworkMembers("CAMD"));

        return ResponseEntity.ok(report);
    }

    @GetMapping("/collections")
    public ResponseEntity<?> collectionsReport(@RequestParam(defaultValue = "false") String simple) {
        List<au.org.ala.collectory.domain.Collection> collections =
                collectionRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = collections.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", c.getUid());
            m.put("name", c.getName());
            m.put("acronym", c.getAcronym());
            m.put("collectionType", c.getCollectionType());
            m.put("focus", c.getFocus());
            m.put("keywords", c.getKeywords());
            m.put("numRecords", c.getNumRecords());
            m.put("numRecordsDigitised", c.getNumRecordsDigitised());
            if (c.getInstitution() != null) {
                m.put("institutionUid", c.getInstitution().getUid());
                m.put("institutionName", c.getInstitution().getName());
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/institutions")
    public ResponseEntity<?> institutionsReport(@RequestParam(defaultValue = "false") String simple) {
        List<Institution> institutions = institutionRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = institutions.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", i.getUid());
            m.put("name", i.getName());
            m.put("acronym", i.getAcronym());
            m.put("institutionType", i.getInstitutionType());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/providers")
    public ResponseEntity<?> providersReport() {
        List<DataProvider> providers = dataProviderRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = providers.stream().map(dp -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", dp.getUid());
            m.put("name", dp.getName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/resources")
    public ResponseEntity<?> resourcesReport() {
        List<DataResource> resources = dataResourceRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = resources.stream().map(dr -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", dr.getUid());
            m.put("name", dr.getName());
            m.put("status", dr.getStatus());
            m.put("resourceType", dr.getResourceType());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/contacts")
    public ResponseEntity<?> contactsReport() {
        List<Contact> contacts = contactRepository.findAll(Sort.by("lastName"));
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/contactsForCollections")
    public ResponseEntity<?> contactsForCollections() {
        List<au.org.ala.collectory.domain.Collection> collections =
                collectionRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = buildContactsModel(collections);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/contactsForInstitutions")
    public ResponseEntity<?> contactsForInstitutions() {
        List<Institution> institutions = institutionRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = buildContactsModel(institutions);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/codes")
    public ResponseEntity<?> codesReport() {
        List<ProviderMap> maps = providerMapRepository.findAll();
        List<Object> result = maps.stream()
                .filter(pm -> pm.getCollection() != null)
                .map(pm -> collectionService.buildSummary(pm.getCollection()))
                .sorted(Comparator.comparing(s -> s.getName() != null ? s.getName() : "", String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/duplicateContacts")
    public ResponseEntity<?> duplicateContacts() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Find duplicate emails
        List<Contact> allContacts = contactRepository.findAll();
        Map<String, List<Contact>> byEmail = allContacts.stream()
                .filter(c -> c.getEmail() != null && !c.getEmail().isEmpty())
                .collect(Collectors.groupingBy(Contact::getEmail));
        List<Map<String, Object>> dupEmails = byEmail.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> Map.<String, Object>of("email", e.getKey(), "contacts", e.getValue()))
                .collect(Collectors.toList());

        // Find duplicate names
        Map<String, List<Contact>> byName = allContacts.stream()
                .filter(c -> c.getFirstName() != null && c.getLastName() != null)
                .collect(Collectors.groupingBy(c -> c.getFirstName() + "|" + c.getLastName()));
        List<Map<String, Object>> dupNames = byName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 2);
                    return Map.<String, Object>of(
                            "firstName", parts[0], "lastName", parts[1], "contacts", e.getValue());
                })
                .collect(Collectors.toList());

        result.put("dupEmails", dupEmails);
        result.put("dupNames", dupNames);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/changes")
    public ResponseEntity<?> changesReport(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "") String who,
            @RequestParam(defaultValue = "") String what) {
        int pageSize = 100;
        Pageable pageable = PageRequest.of(offset / pageSize, pageSize,
                Sort.by(Sort.Direction.DESC, "lastUpdated"));

        Page<AuditLogEvent> page;
        boolean hasWho = !who.isEmpty();
        boolean hasWhat = !what.isEmpty();

        if (hasWho && hasWhat) {
            page = auditLogEventRepository.searchByWhoAndWhat(who, what, pageable);
        } else if (hasWho) {
            page = auditLogEventRepository.findAllByActorContainingIgnoreCase(who, pageable);
        } else if (hasWhat) {
            page = auditLogEventRepository.searchByWhat(what, pageable);
        } else {
            page = auditLogEventRepository.findAll(pageable);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changes", page.getContent());
        result.put("offset", offset);
        result.put("totalElements", page.getTotalElements());
        result.put("who", who);
        result.put("what", what);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> notifications() {
        // L3: filter by 'notify-service' user as Grails did
        List<ActivityLog> notices = activityLogRepository.findAllByUser(
                "notify-service",
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp"))).getContent();
        return ResponseEntity.ok(Map.of("notices", notices));
    }

    @GetMapping("/harvesters")
    public ResponseEntity<?> harvesters() {
        List<DataResource> resources = dataResourceRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = resources.stream().map(dr -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", dr.getUid());
            m.put("name", dr.getName());
            m.put("status", dr.getStatus());
            m.put("harvestingFrequency", dr.getHarvestFrequency());
            m.put("lastChecked", dr.getLastChecked());
            m.put("dataCurrency", dr.getDataCurrency());
            m.put("connectionParameters", dr.getConnectionParameters() != null ?
                    au.org.ala.collectory.util.JSONHelper.parseJson(dr.getConnectionParameters()) : null);
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rights")
    public ResponseEntity<?> rights() {
        List<DataResource> resources = dataResourceRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> result = resources.stream().map(dr -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", dr.getUid());
            m.put("name", dr.getName());
            m.put("rights", dr.getRights());
            m.put("licenseType", dr.getLicenseType());
            m.put("licenseVersion", dr.getLicenseVersion());
            m.put("permissionsDocument", dr.getPermissionsDocument());
            m.put("permissionsDocumentType", dr.getPermissionsDocumentType());
            m.put("filed", dr.isFiled());
            m.put("riskAssessment", dr.isRiskAssessment());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/attributions")
    public ResponseEntity<?> attributions() {
        List<Map<String, Object>> collAttributions = collectionRepository.findAll(Sort.by("name")).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("entity", collectionService.buildSummary(c));
                    // attributionList will be populated when Attribution support is completed
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> instAttributions = institutionRepository.findAll(Sort.by("name")).stream()
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("entity", institutionService.buildSummary(i));
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "collAttributions", collAttributions,
                "instAttributions", instAttributions));
    }

    @GetMapping("/classification")
    public ResponseEntity<?> classification() {
        List<au.org.ala.collectory.domain.Collection> collections =
                collectionRepository.findAll(Sort.by("name"));
        return ResponseEntity.ok(Map.of("collections", collections.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", c.getUid());
            m.put("name", c.getName());
            m.put("keywords", c.getKeywords());
            return m;
        }).collect(Collectors.toList())));
    }

    @GetMapping("/taxonomicHints")
    public ResponseEntity<?> taxonomicHints() {
        List<au.org.ala.collectory.domain.Collection> collections =
                collectionRepository.findAll(Sort.by("name"));
        return ResponseEntity.ok(collections.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", c.getUid());
            m.put("name", c.getName());
            m.put("taxonomyHints", c.getTaxonomyHints());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/collectionTypes")
    public ResponseEntity<?> collectionTypes() {
        List<au.org.ala.collectory.domain.Collection> collections =
                collectionRepository.findAll(Sort.by("name"));
        return ResponseEntity.ok(collections.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", c.getUid());
            m.put("name", c.getName());
            m.put("collectionType", c.getCollectionType());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/dataLinks")
    public ResponseEntity<?> dataLinks() {
        // Build link data from consumer relationships
        List<Map<String, String>> links = new ArrayList<>();

        dataProviderRepository.findAll().forEach(dp -> {
            if (dp.getConsumerInstitutions() != null) {
                dp.getConsumerInstitutions().forEach(inst ->
                        links.add(Map.of("consumer", inst.getUid(), "provider", dp.getUid())));
            }
            if (dp.getConsumerCollections() != null) {
                dp.getConsumerCollections().forEach(co ->
                        links.add(Map.of("consumer", co.getUid(), "provider", dp.getUid())));
            }
        });

        dataResourceRepository.findAll().forEach(dr -> {
            if (dr.getConsumerInstitutions() != null) {
                dr.getConsumerInstitutions().forEach(inst ->
                        links.add(Map.of("consumer", inst.getUid(), "provider", dr.getUid())));
            }
            if (dr.getConsumerCollections() != null) {
                dr.getConsumerCollections().forEach(co ->
                        links.add(Map.of("consumer", co.getUid(), "provider", dr.getUid())));
            }
        });

        links.sort(Comparator.comparing(m -> m.get("provider")));
        return ResponseEntity.ok(Map.of("links", links));
    }

    @GetMapping("/contactsForCouncilMembers")
    public ResponseEntity<?> contactsForCouncilMembers() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chafc", buildCouncilContactList("CHAFC"));
        result.put("chaec", buildCouncilContactList("CHAEC"));
        result.put("chacm", buildCouncilContactList("CHACM"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/missingRecords")
    public ResponseEntity<?> missingRecords() {
        if (appProperties.getBiocacheServicesUrl() == null) {
            return ResponseEntity.ok(Map.of("error", "Biocache services URL not configured"));
        }
        List<Map<String, Object>> mrs = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        for (au.org.ala.collectory.domain.Collection c : collectionRepository.findAll(Sort.by("name"))) {
            if (c.getNumRecordsDigitised() > 0) {
                String url = appProperties.getBiocacheServicesUrl() +
                        "/occurrences/collections/" + c.getUid() + "?pageSize=0";
                int count = 0;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                    if (response != null && response.get("totalRecords") != null) {
                        count = ((Number) response.get("totalRecords")).intValue();
                    }
                } catch (Exception e) {
                    log.warn("Failed to lookup record count for {}: {}", c.getUid(), e.getMessage());
                }
                if (count == 0 || ((double) count / c.getNumRecordsDigitised()) < 0.7) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("collection", collectionService.buildSummary(c));
                    entry.put("biocacheCount", count);
                    entry.put("claimed", c.getNumRecordsDigitised());
                    mrs.add(entry);
                }
            }
        }
        return ResponseEntity.ok(Map.of("mrs", mrs));
    }

    @GetMapping("/collectionSpecimenData")
    public ResponseEntity<?> collectionSpecimenData() {
        List<Map<String, Object>> statistics = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        for (au.org.ala.collectory.domain.Collection c : collectionRepository.findAll(Sort.by("name"))) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("uid", c.getUid());
            rec.put("name", c.getName());
            rec.put("acronym", c.getAcronym());
            rec.put("numRecords", c.getNumRecords());
            rec.put("numRecordsDigitised", c.getNumRecordsDigitised());
            if (appProperties.getBiocacheServicesUrl() != null) {
                try {
                    String url = appProperties.getBiocacheServicesUrl() +
                            "/occurrences/search?pageSize=0&q=" + c.getUid();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                    if (response != null && response.get("totalRecords") != null) {
                        rec.put("numBiocacheRecords", response.get("totalRecords"));
                    }
                } catch (Exception e) {
                    log.warn("Failed to lookup biocache count for {}: {}", c.getUid(), e.getMessage());
                }
            }
            statistics.add(rec);
        }
        return ResponseEntity.ok(Map.of("statistics", statistics));
    }

    @GetMapping("/map")
    public ResponseEntity<?> mapLocations() {
        List<Map<String, Object>> locations = new ArrayList<>();
        collectionRepository.findAll(Sort.by("name")).forEach(c -> {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("name", c.getName());
            loc.put("link", c.getUid());
            double lat = (c.getLatitude() == null || c.getLatitude() == 0.0) ? -1 : c.getLatitude();
            double lon = (c.getLongitude() == null || c.getLongitude() == 0.0) ? -1 : c.getLongitude();
            loc.put("latitude", lat);
            loc.put("longitude", lon);
            // Build street address for geocoding fallback
            String street = "";
            if (c.getAddress() != null) {
                StringBuilder sb = new StringBuilder();
                if (c.getAddress().getStreet() != null) sb.append(c.getAddress().getStreet());
                if (c.getAddress().getCity() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(c.getAddress().getCity());
                }
                if (c.getAddress().getState() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(c.getAddress().getState());
                }
                if (c.getAddress().getCountry() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(c.getAddress().getCountry());
                }
                street = sb.toString();
            }
            loc.put("streetAddress", street);
            locations.add(loc);
        });
        return ResponseEntity.ok(Map.of("locations", locations));
    }

    @GetMapping("/providerRecordsData")
    public ResponseEntity<?> providerRecordsData() {
        List<Map<String, Object>> statistics = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        for (DataProvider dp : dataProviderRepository.findAll(Sort.by("name"))) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("uid", dp.getUid());
            rec.put("name", dp.getName());
            rec.put("acronym", dp.getAcronym());
            if (appProperties.getBiocacheServicesUrl() != null) {
                try {
                    String url = appProperties.getBiocacheServicesUrl() +
                            "/occurrences/search?pageSize=0&q=" + dp.getUid();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                    if (response != null && response.get("totalRecords") != null) {
                        rec.put("numBiocacheRecords", response.get("totalRecords"));
                    }
                } catch (Exception e) {
                    log.warn("Failed to lookup biocache count for {}: {}", dp.getUid(), e.getMessage());
                }
            }
            statistics.add(rec);
        }
        return ResponseEntity.ok(Map.of("statistics", statistics));
    }

    // --- Helpers ---

    private List<Map<String, String>> buildNetworkMembers(String networkCode) {
        List<Map<String, String>> members = new ArrayList<>();
        collectionRepository.findAllByNetworkMembershipContainingIgnoreCase(networkCode)
                .forEach(c -> members.add(Map.of("uid", c.getUid(), "name", c.getName(), "type", "Collection")));
        institutionRepository.findAllByNetworkMembershipContainingIgnoreCase(networkCode)
                .forEach(i -> members.add(Map.of("uid", i.getUid(), "name", i.getName(), "type", "Institution")));
        members.sort(Comparator.comparing(m -> m.get("name"), String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    private List<Map<String, Object>> buildCouncilContactList(String networkCode) {
        return collectionRepository.findAllByNetworkMembershipContainingIgnoreCase(networkCode).stream()
                .sorted(Comparator.comparing(ProviderGroup::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", c.getUid());
                    entry.put("name", c.getName());
                    // Find primary contact
                    List<ContactFor> contacts = contactForRepository.findAllByEntityUid(c.getUid());
                    contacts.stream()
                            .filter(ContactFor::isPrimaryContact)
                            .findFirst()
                            .ifPresent(cf -> {
                                Contact contact = cf.getContact();
                                if (contact != null) {
                                    entry.put("email", contact.getEmail());
                                    entry.put("contact", contact.buildName());
                                }
                            });
                    return entry;
                }).collect(Collectors.toList());
    }


    private List<Map<String, Object>> buildContactsModel(List<? extends ProviderGroup> entities) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProviderGroup pg : entities) {
            List<ContactFor> contacts = contactForRepository.findAllByEntityUid(pg.getUid());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uid", pg.getUid());
            entry.put("name", pg.getName());
            List<Map<String, Object>> contactList = contacts.stream().map(cf -> {
                Map<String, Object> m = new LinkedHashMap<>();
                Contact c = cf.getContact();
                if (c != null) {
                    m.put("name", c.buildName());
                    m.put("email", c.getEmail());
                    m.put("role", cf.getRole());
                    m.put("primaryContact", cf.isPrimaryContact());
                }
                return m;
            }).collect(Collectors.toList());
            entry.put("contacts", contactList);
            result.add(entry);
        }
        return result;
    }
}
