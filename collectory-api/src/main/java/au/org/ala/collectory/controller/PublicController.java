package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.service.ProviderGroupService;
import au.org.ala.collectory.util.JSONHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/ws/public", "/public"})
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PublicController {

    private final InstitutionRepository institutionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final CollectionRepository collectionRepository;
    private final DataHubRepository dataHubRepository;
    private final ContactForRepository contactForRepository;
    private final AppProperties appProperties;
    private final ProviderGroupService providerGroupService;

    @GetMapping({"/mapFeatures", "/mapFeatures.json"})
    public ResponseEntity<?> mapFeatures() {
        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type", "FeatureCollection");

        List<Map<String, Object>> features = new ArrayList<>();

        // Group collections by institution (matching Grails mapFeatures behaviour)
        // Get distinct institutions that have collections
        List<au.org.ala.collectory.domain.Collection> allCollections = collectionRepository.findAll();
        Map<Long, List<au.org.ala.collectory.domain.Collection>> collectionsByInst = new LinkedHashMap<>();
        List<au.org.ala.collectory.domain.Collection> orphanCollections = new ArrayList<>();

        for (au.org.ala.collectory.domain.Collection co : allCollections) {
            if (co.getInstitution() != null) {
                collectionsByInst
                        .computeIfAbsent(co.getInstitution().getId(), k -> new ArrayList<>())
                        .add(co);
            } else {
                orphanCollections.add(co);
            }
        }

        // Add institutions with their collections
        for (var entry : collectionsByInst.entrySet()) {
            Institution inst = entry.getValue().get(0).getInstitution();
            List<au.org.ala.collectory.domain.Collection> collections = entry.getValue();
            collections.sort(Comparator.comparing(ProviderGroup::getName, String.CASE_INSENSITIVE_ORDER));

            double lat = (inst.getLatitude() == null || inst.getLatitude() == 0.0) ? -1 : inst.getLatitude();
            double lon = (inst.getLongitude() == null || inst.getLongitude() == 0.0) ? -1 : inst.getLongitude();

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");

            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", List.of(lon, lat));
            feature.put("geometry", geometry);

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", inst.getName());
            properties.put("entityType", "institution");
            properties.put("uid", inst.getUid());
            properties.put("instName", inst.getName());
            properties.put("instUid", inst.getUid());
            if (inst.getAcronym() != null) properties.put("acronym", inst.getAcronym());
            properties.put("isMappable", inst.canBeMapped());
            properties.put("desc", inst.makeAbstract());
            properties.put("collectionCount", collections.size());
            properties.put("collections", collections.stream().map(co -> {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("uid", co.getUid());
                cm.put("name", co.getName());
                return cm;
            }).collect(Collectors.toList()));
            feature.put("properties", properties);

            features.add(feature);
        }

        // Add orphan collections (no institution)
        for (au.org.ala.collectory.domain.Collection co : orphanCollections) {
            double lat = (co.getLatitude() == null || co.getLatitude() == 0.0) ? -1 : co.getLatitude();
            double lon = (co.getLongitude() == null || co.getLongitude() == 0.0) ? -1 : co.getLongitude();

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            feature.put("geometry", Map.of("type", "Point", "coordinates", List.of(lon, lat)));

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", co.getName());
            properties.put("entityType", "collection");
            properties.put("uid", co.getUid());
            properties.put("isMappable", co.canBeMapped());
            properties.put("desc", co.makeAbstract());
            feature.put("properties", properties);

            features.add(feature);
        }

        featureCollection.put("features", features);
        return ResponseEntity.ok(featureCollection);
    }

    @GetMapping("/resources")
    public ResponseEntity<?> resources() {
        List<DataResource> all = dataResourceRepository.findAll();
        List<Map<String, Object>> result = all.stream()
                .filter(dr -> !"declined".equals(dr.getStatus()) && !Boolean.TRUE.equals(dr.getIsPrivate()))
                .sorted(Comparator.comparing(DataResource::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::buildResourceEntry)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/condensed")
    public ResponseEntity<?> condensed() {
        List<DataResource> all = dataResourceRepository.findAll();
        List<Map<String, Object>> result = all.stream()
                .filter(dr -> !"declined".equals(dr.getStatus()) && !Boolean.TRUE.equals(dr.getIsPrivate()))
                .sorted(Comparator.comparing(DataResource::getName, String.CASE_INSENSITIVE_ORDER))
                .map(dr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uid", dr.getUid());
                    m.put("name", dr.getName());
                    m.put("resourceType", dr.getResourceType());
                    m.put("licenseType", dr.getLicenseType());
                    m.put("status", dr.getStatus());
                    m.put("websiteUrl", dr.getWebsiteUrl());
                    m.put("contentTypes", dr.getContentTypes());
                    // Institution name: use acronym if name > 36 chars and acronym exists
                    Institution inst = dr.getInstitution();
                    String instName = null;
                    if (inst != null) {
                        String iName = inst.getName();
                        String iAcronym = inst.getAcronym();
                        instName = (iName != null && iName.length() > 36 && iAcronym != null && !iAcronym.isEmpty())
                                ? iAcronym : iName;
                    }
                    m.put("institution", instName);
                    m.put("lastUpdated", dr.getLastUpdated());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dataSetSearch")
    public ResponseEntity<?> dataSetSearch(@RequestParam(required = false) String q) {
        if (q == null || q.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        String pattern = "%" + q.toLowerCase() + "%";
        List<DataResource> all = dataResourceRepository.findAll();
        List<String> uids = all.stream()
                .filter(dr -> !"declined".equals(dr.getStatus()) && !Boolean.TRUE.equals(dr.getIsPrivate()))
                .filter(dr -> dr.getName() != null && dr.getName().toLowerCase().contains(q.toLowerCase()))
                .map(DataResource::getUid)
                .collect(Collectors.toList());
        return ResponseEntity.ok(uids);
    }

    @GetMapping("/downloadDataSets")
    public void downloadDataSets(
            @RequestParam(required = false) String resourceType,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=datasets.csv");
        var writer = response.getWriter();
        writer.println("name,dataPartner,institution,resourceType,licenseType,licenseVersion,rights,uri,status,contact,verified");

        List<DataResource> all = dataResourceRepository.findAll();
        all.stream()
                .filter(dr -> !"declined".equals(dr.getStatus()) && !Boolean.TRUE.equals(dr.getIsPrivate()))
                .filter(dr -> resourceType == null || resourceType.equals(dr.getResourceType()))
                .sorted(Comparator.comparing(DataResource::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(dr -> {
                    String dpName = dr.getDataProvider() != null ? dr.getDataProvider().getName() : "";
                    String instName = dr.getInstitution() != null ? dr.getInstitution().getName() : "";
                    String uri = dr.buildUri(appProperties.getServerUrl());
                    // Primary contact name
                    String contactName = contactForRepository.findAllByEntityUid(dr.getUid()).stream()
                            .filter(ContactFor::isPrimaryContact)
                            .findFirst()
                            .map(cf -> cf.getContact() != null ? cf.getContact().buildName() : "")
                            .orElse("");
                    writer.println(String.join(",",
                            csvEscape(dr.getName()),
                            csvEscape(dpName),
                            csvEscape(instName),
                            csvEscape(dr.getResourceType()),
                            csvEscape(dr.getLicenseType()),
                            csvEscape(dr.getLicenseVersion()),
                            csvEscape(dr.getRights()),
                            csvEscape(uri),
                            csvEscape(dr.getStatus()),
                            csvEscape(contactName),
                            "no"));  // isVerified not currently modelled
                });
        writer.flush();
    }

    @GetMapping("/biocacheRecords")
    public ResponseEntity<?> biocacheRecords(
            @RequestParam String uid,
            @RequestParam(defaultValue = "decade") String facet) {
        if (appProperties.getBiocacheServicesUrl() == null) {
            return ResponseEntity.ok(Map.of("error", "Biocache services URL not configured"));
        }
        try {
            String url = appProperties.getBiocacheServicesUrl() +
                    "/occurrences/search?q=" + uid + "&facets=" + facet + "&pageSize=0&facet=on&flimit=-1";
            RestTemplate restTemplate = new RestTemplate();
            Object result = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Failed to query biocache: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("error", "Failed to query biocache: " + e.getMessage()));
        }
    }

    // --- Helpers ---

    private Map<String, Object> buildFeature(ProviderGroup pg, String entityType) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        geometry.put("coordinates", List.of(pg.getLongitude(), pg.getLatitude()));
        feature.put("geometry", geometry);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", pg.getName());
        properties.put("uid", pg.getUid());
        properties.put("entityType", entityType);
        properties.put("uri", pg.buildUri(appProperties.getServerUrl()));
        if (pg.getAcronym() != null) properties.put("acronym", pg.getAcronym());
        feature.put("properties", properties);

        return feature;
    }

    private Map<String, Object> buildResourceEntry(DataResource dr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uid", dr.getUid());
        m.put("name", dr.getName());
        m.put("uri", dr.buildUri(appProperties.getServerUrl()));
        m.put("resourceType", dr.getResourceType());
        m.put("status", dr.getStatus());
        m.put("contentTypes", dr.getContentTypes() != null ?
                JSONHelper.parseJson(dr.getContentTypes()) : Collections.emptyList());
        if (dr.getDataProvider() != null) {
            m.put("dataProvider", Map.of(
                    "uid", dr.getDataProvider().getUid(),
                    "name", dr.getDataProvider().getName()));
        }
        m.put("licenseType", dr.getLicenseType());
        m.put("licenseVersion", dr.getLicenseVersion());
        return m;
    }

    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
