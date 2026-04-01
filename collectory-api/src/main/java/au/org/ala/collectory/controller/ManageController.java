package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.dto.DataSourceConfiguration;
import au.org.ala.collectory.dto.ExternalResourceBean;
import au.org.ala.collectory.dto.GBIFActiveLoad;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.resources.DataSourceLoad;
import au.org.ala.collectory.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/ws/manage", "/manage"})
@RequiredArgsConstructor
@Slf4j
public class ManageController {

    private final CollectoryAuthService collectoryAuthService;
    private final ProviderGroupService providerGroupService;
    private final ContactRepository contactRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final AuditLogEventRepository auditLogEventRepository;
    private final AppProperties appProperties;
    private final ExternalDataService externalDataService;
    private final GbifService gbifService;

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        String email = collectoryAuthService.userEmail();
        Map<String, Object> authorised = collectoryAuthService.authorisedForUser(email);
        Contact contact = contactRepository.findByEmail(email).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entities", authorised.get("sorted"));
        if (contact != null) {
            result.put("user", contact);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/show/{uid}")
    public ResponseEntity<?> show(@PathVariable String uid) {
        ProviderGroup instance = providerGroupService.get(uid);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }

        List<AuditLogEvent> changes = auditLogEventRepository.findAllByUri(
                "/" + providerGroupService.entityTypeFromUid(uid) + "/" + uid,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastUpdated"))).getContent();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uid", instance.getUid());
        result.put("name", instance.getName());
        result.put("entityType", providerGroupService.entityTypeFromUid(uid));
        result.put("changes", changes);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/repatriate/config")
    public ResponseEntity<?> repatriateConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gbifApiUrl", appProperties.getGbifApiUrl());
        result.put("defaultCountry", Locale.getDefault().getCountry());
        List<Map<String, Object>> providers = dataProviderRepository.findAll(Sort.by("name")).stream()
                .map(dp -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uid", dp.getUid());
                    m.put("name", dp.getName());
                    return m;
                }).collect(Collectors.toList());
        result.put("dataProviders", providers);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/externalLoad/config")
    public ResponseEntity<?> externalLoadConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gbifApiUrl", appProperties.getGbifApiUrl());
        result.put("defaultCountry", Locale.getDefault().getCountry());
        List<Map<String, Object>> providers = dataProviderRepository.findAll(Sort.by("name")).stream()
                .map(dp -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uid", dp.getUid());
                    m.put("name", dp.getName());
                    return m;
                }).collect(Collectors.toList());
        result.put("dataProviders", providers);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/gbifDatasetDownload/{uid}")
    public ResponseEntity<?> gbifDatasetDownload(@PathVariable String uid) {
        DataResource dr = dataResourceRepository.findByUid(uid).orElse(null);
        if (dr == null) return ResponseEntity.notFound().build();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uid", dr.getUid());
        result.put("guid", dr.getGuid());
        result.put("name", dr.getName());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/searchForResources")
    public ResponseEntity<?> searchForResources(@RequestBody DataSourceConfiguration configuration) {
        log.debug("Searching for resources from external source: {}", configuration);
        List<ExternalResourceBean> resources = externalDataService.searchForDatasets(configuration);
        configuration.setResources(resources);

        DataProvider dataProvider = null;
        if (configuration.getDataProviderUid() != null) {
            dataProvider = dataProviderRepository.findByUid(configuration.getDataProviderUid()).orElse(null);
        }

        List<DataResource> dataResources = dataResourceRepository.findAll(Sort.by("name")).stream()
                .filter(dr -> "records".equals(dr.getResourceType()))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadGuid", UUID.randomUUID().toString());
        result.put("dataResources", dataResources);
        result.put("dataProvider", dataProvider);
        result.put("configuration", configuration);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/searchForRepatResources")
    public ResponseEntity<?> searchForRepatResources(@RequestBody DataSourceConfiguration configuration) {
        log.debug("Searching for repat resources from external source: {}", configuration);

        // Use GBIF API credentials from configuration
        configuration.setUsername(appProperties.getGbifApiUser());
        configuration.setPassword(appProperties.getGbifApiPassword());

        List<ExternalResourceBean> resources = externalDataService.searchForDatasets(configuration);
        configuration.setResources(resources);

        DataProvider dataProvider = null;
        if (configuration.getDataProviderUid() != null) {
            dataProvider = dataProviderRepository.findByUid(configuration.getDataProviderUid()).orElse(null);
        }

        List<DataResource> dataResources = dataResourceRepository.findAll(Sort.by("name")).stream()
                .filter(dr -> "records".equals(dr.getResourceType()))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadGuid", UUID.randomUUID().toString());
        result.put("dataResources", dataResources);
        result.put("dataProvider", dataProvider);
        result.put("configuration", configuration);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/updateFromExternalSources")
    public ResponseEntity<?> updateFromExternalSources(@RequestBody DataSourceConfiguration configuration,
                                                       @RequestParam String loadGuid) {
        log.debug("Update resources from external source: {}", configuration);
        externalDataService.updateFromExternalSources(configuration, loadGuid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadGuid", loadGuid);
        result.put("trackingUrl", "/ws/manage/externalLoadStatus?loadGuid=" + loadGuid);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/loadDataset")
    public ResponseEntity<?> loadDataset(@RequestBody Map<String, Object> params) {
        log.debug("Loading resources from GBIF: {}", params);
        String guid = (String) params.get("guid");
        String repatriationCountry = (String) params.get("repatriationCountry");

        if (guid == null || guid.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No guid provided"));
        }

        GBIFActiveLoad load = gbifService.downloadGbifDataset(guid, repatriationCountry);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetKey", guid);
        result.put("load", load);
        result.put("trackingUrl", "/ws/manage/gbifDatasetLoadStatus?datasetKey=" + guid);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/gbifDatasetLoadStatus")
    public ResponseEntity<?> gbifDatasetLoadStatus(@RequestParam String datasetKey) {
        GBIFActiveLoad gbifSummary = gbifService.getDatasetKeyStatusInfoFor(datasetKey);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetKey", datasetKey);
        result.put("gbifSummary", gbifSummary);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/gbifCountryLoadStatus")
    public ResponseEntity<?> gbifCountryLoadStatus(@RequestParam String country) {
        GBIFActiveLoad gbifSummary = gbifService.getStatusInfoFor(country);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("country", country);
        result.put("gbifSummary", gbifSummary);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/externalLoadStatus")
    public ResponseEntity<?> externalLoadStatus(@RequestParam String loadGuid) {
        DataSourceLoad load = externalDataService.getStatusInfoFor(loadGuid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("load", load);
        result.put("loadGuid", loadGuid);
        return ResponseEntity.ok(result);
    }
}
