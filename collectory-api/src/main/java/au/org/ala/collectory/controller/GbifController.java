package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.dto.DataSourceConfiguration;
import au.org.ala.collectory.dto.ExternalResourceBean;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import au.org.ala.collectory.resources.gbif.GbifRepatDataSourceAdapter;
import au.org.ala.collectory.service.CollectoryAuthService;
import au.org.ala.collectory.service.ExternalDataService;
import au.org.ala.collectory.service.GbifRegistryService;
import au.org.ala.collectory.service.GbifService;
import au.org.ala.collectory.service.IdGeneratorService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GbifController {

    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final CollectoryAuthService collectoryAuthService;
    private final AppProperties appProperties;
    private final GbifRegistryService gbifRegistryService;
    private final GbifService gbifService;
    private final ExternalDataService externalDataService;
    private final IdGeneratorService idGeneratorService;

    @GetMapping({"/ws/gbif/healthCheck", "/admin/gbif/healthcheck"})
    public ResponseEntity<?> healthCheck() {
        try {
            Map<String, Object> breakdown = gbifRegistryService.generateSyncBreakdown();
            return ResponseEntity.ok(breakdown);
        } catch (Exception e) {
            log.error("Error generating GBIF health check: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate GBIF health check: " + e.getMessage()));
        }
    }

    @GetMapping("/ws/gbif/healthCheckLinked")
    public ResponseEntity<?> healthCheckLinked() {
        // TODO (L14): The Grails version fetched live biocache occurrence counts per data resource
        //  to determine which resources have linked records, providing a richer diff than the plain
        //  sync breakdown. That requires biocache integration which is not yet implemented.
        //  For now, fall back to the same generateSyncBreakdown() output as healthCheck().
        try {
            Map<String, Object> breakdown = gbifRegistryService.generateSyncBreakdown();
            return ResponseEntity.ok(breakdown);
        } catch (Exception e) {
            log.error("Error generating GBIF health check linked: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate GBIF health check linked: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/ws/gbif/downloadCSV", produces = "text/csv")
    public void downloadCSV(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=gbif-healthcheck.csv");
        gbifRegistryService.writeCSVReportForGBIF(response.getOutputStream());
    }

    // L15: Also accept GET for backward compatibility (old callers used GET /admin/gbif/sync).
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST}, path = {"/ws/gbif/syncAllResources", "/admin/gbif/sync"})
    public ResponseEntity<?> syncAllResources() {
        try {
            Map<String, Object> result = gbifRegistryService.updateAllResources();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error syncing all resources: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to sync all resources: " + e.getMessage()));
        }
    }

    @GetMapping("/ws/gbif/scan/{uid}")
    public ResponseEntity<?> scan(@PathVariable String uid) {
        if (uid == null || !uid.startsWith("dp")) {
            return ResponseEntity.badRequest().body(Map.of("error", "No valid UID supplied"));
        }

        DataProvider dataProvider = dataProviderRepository.findByUid(uid).orElse(null);
        if (dataProvider == null) {
            return ResponseEntity.notFound().build();
        }

        // Get resources belonging to this provider
        List<DataResource> resources = dataResourceRepository.findAllByDataProviderUid(uid);
        List<Map<String, Object>> output = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        List<ExternalResourceBean> externalResourceBeans = new ArrayList<>();

        for (DataResource dr : resources) {
            Date lastUpdated = gbifService.getGbifDatasetLastUpdated(dr.getGuid());
            boolean inSync = !(lastUpdated != null && dr.getLastUpdated() != null
                    && lastUpdated.toInstant().isAfter(
                        dr.getLastUpdated().atZone(java.time.ZoneId.systemDefault()).toInstant()));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", dr.getUid());
            m.put("name", dr.getName());
            m.put("lastUpdated", dr.getLastUpdated());
            m.put("guid", dr.getGuid());
            m.put("country", dr.getRepatriationCountry());
            m.put("pubDate", lastUpdated);
            m.put("inSync", inSync);

            if (!inSync && dr.getGuid() != null) {
                m.put("status", "RELOADING");
                ExternalResourceBean bean = new ExternalResourceBean();
                bean.setUid(dr.getUid());
                bean.setGuid(dr.getGuid());
                bean.setName(dr.getName());
                bean.setCountry(dr.getRepatriationCountry());
                bean.setUpdateMetadata(true);
                bean.setUpdateConnection(true);
                externalResourceBeans.add(bean);
                updates.add(m);
            } else {
                m.put("status", "IN_SYNC");
            }
            output.add(m);
        }

        // Build configuration and trigger async update
        DataSourceConfiguration configuration = new DataSourceConfiguration();
        configuration.setAdaptorClass(GbifRepatDataSourceAdapter.class);
        try {
            configuration.setEndpoint(new URL(appProperties.getGbifApiUrl()));
        } catch (Exception e) {
            log.error("Invalid GBIF API URL: {}", appProperties.getGbifApiUrl(), e);
        }
        configuration.setUsername(appProperties.getGbifApiUser());
        configuration.setPassword(appProperties.getGbifApiPassword());
        configuration.setResources(externalResourceBeans);

        String loadGuid = UUID.randomUUID().toString();
        log.info("Reloading process ID {}", loadGuid);
        externalDataService.updateFromExternalSources(configuration, loadGuid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadGuid", loadGuid);
        result.put("trackingUrl", "/ws/manage/externalLoadStatus?loadGuid=" + loadGuid);
        result.put("updates", updates);
        result.put("resources", output);
        return ResponseEntity.ok(result);
    }

    /**
     * Downloads a GBIF Darwin Core Archive from the supplied URL and creates a new DataResource
     * from the embedded EML metadata. Mirrors the legacy Grails {@code downloadGBIFFile} action.
     *
     * @param url the direct download URL of the GBIF archive (e.g. from api.gbif.org)
     */
    @GetMapping("/ws/dataResource/{id}/downloadGBIFFile")
    public ResponseEntity<?> downloadGBIFFile(
            @PathVariable String id,
            @RequestParam String url) {
        log.info("Downloading GBIF archive from URL: {}", url);
        try {
            DataResource dr = gbifService.createGBIFResourceFromArchiveURL(url);
            if (dr != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("dataResourceName", dr.getName());
                result.put("dataResourceUid", dr.getUid());
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.ok(Map.of("success", false));
            }
        } catch (Exception e) {
            log.error("Error downloading GBIF archive: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Returns country map + list of GBIF organizations for a given country code.
     * Mirrors Grails DataProviderController.searchForOrganizations.
     * GET /ws/dataProvider/gbif/searchForOrganizations?country=AU
     */
    @GetMapping("/ws/dataProvider/gbif/searchForOrganizations")
    public ResponseEntity<?> searchForOrganizations(
            @RequestParam(required = false) String country) {
        Map<String, String> countryMap = gbifService.getCountryMap();
        String countryCode = (country != null && !country.isEmpty() && !country.equals("NO_VALUE"))
                ? country
                : null;

        List<Map<String, Object>> organizations = null;
        if (countryCode != null && !countryCode.isEmpty()) {
            List<Map<String, Object>> raw = gbifRegistryService.loadOrganizationsByCountry(countryCode);
            if (raw != null) {
                organizations = new ArrayList<>();
                for (Map<String, Object> org : raw) {
                    Map<String, Object> item = new LinkedHashMap<>(org);
                    String gbifKey = (String) org.get("key");
                    DataProvider existing = gbifKey != null
                            ? dataProviderRepository.findByGbifRegistryKey(gbifKey).orElse(null)
                            : null;
                    if (existing != null) {
                        item.put("uid", existing.getUid());
                        item.put("statusAvailable", false);
                    } else {
                        item.put("uid", null);
                        item.put("statusAvailable", true);
                    }
                    organizations.add(item);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("countryMap", countryMap);
        result.put("country", countryCode);
        result.put("organizations", organizations);
        return ResponseEntity.ok(result);
    }

    /**
     * Imports a single GBIF organization as a DataProvider.
     * Mirrors Grails DataProviderController.importFromOrganization.
     * POST /ws/dataProvider/gbif/importFromOrganization
     */
    @PostMapping("/ws/dataProvider/gbif/importFromOrganization")
    @Transactional
    public ResponseEntity<?> importFromOrganization(
            @RequestParam String organizationKey,
            @RequestParam(required = false) String country) {
        // Check not already imported
        if (dataProviderRepository.findByGbifRegistryKey(organizationKey).isPresent()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Organization already imported"));
        }
        DataProvider dp = new DataProvider();
        dp.setUid(idGeneratorService.getNextDataProviderId());
        dp.setUserLastModified(collectoryAuthService.username());
        dp.setGbifCountryToAttribute(appProperties.getGbifDefaultEntityCountry());
        gbifRegistryService.populateDataProviderFromOrganization(dp, organizationKey);
        dataProviderRepository.save(dp);
        log.info("DataProvider {} created from GBIF organization {}", dp.getUid(), organizationKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("uid", dp.getUid());
        result.put("country", country);
        return ResponseEntity.ok(result);
    }

    /**
     * Imports all GBIF organizations for a country as DataProviders (skipping already imported).
     * Mirrors Grails DataProviderController.importAllFromOrganizations.
     * POST /ws/dataProvider/gbif/importAllFromOrganizations
     */
    @PostMapping("/ws/dataProvider/gbif/importAllFromOrganizations")
    @Transactional
    public ResponseEntity<?> importAllFromOrganizations(
            @RequestParam String country) {
        List<Map<String, Object>> organizations = gbifRegistryService.loadOrganizationsByCountry(country);
        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        for (Map<String, Object> org : organizations) {
            String gbifKey = (String) org.get("key");
            if (gbifKey == null) continue;
            if (dataProviderRepository.findByGbifRegistryKey(gbifKey).isPresent()) {
                skippedCount++;
                continue;
            }
            try {
                DataProvider dp = new DataProvider();
                dp.setUid(idGeneratorService.getNextDataProviderId());
                dp.setUserLastModified(collectoryAuthService.username());
                dp.setGbifCountryToAttribute(appProperties.getGbifDefaultEntityCountry());
                gbifRegistryService.populateDataProviderFromOrganization(dp, gbifKey);
                dataProviderRepository.save(dp);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to import organization {}: {}", gbifKey, e.getMessage());
                errorCount++;
            }
        }
        return ResponseEntity.ok(Map.of(
                "success", successCount,
                "error", errorCount,
                "skipped", skippedCount,
                "country", country));
    }
}
