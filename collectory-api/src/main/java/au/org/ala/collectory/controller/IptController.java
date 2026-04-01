package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import au.org.ala.collectory.service.CollectoryAuthService;
import au.org.ala.collectory.service.IptService;
import au.org.ala.collectory.service.ProviderGroupService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class IptController {

    private final ProviderGroupService providerGroupService;
    private final CollectoryAuthService collectoryAuthService;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final AppProperties appProperties;
    private final IptService iptService;

    @GetMapping("/ws/ipt/scan/{uid}")
    public ResponseEntity<?> scan(
            @PathVariable String uid,
            @RequestParam(defaultValue = "false") boolean create,
            @RequestParam(defaultValue = "true") boolean check,
            @RequestParam(defaultValue = "catalogNumber") String key,
            @RequestParam(defaultValue = "true") boolean isShareableWithGBIF) {

        if (create) {
            return ResponseEntity.status(403).body(Map.of("error", "Unable to create resources for " + uid));
        }

        DataProvider provider = dataProviderRepository.findByUid(uid).orElse(null);
        if (provider == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unable to get data provider " + uid));
        }

        try {
            String username = collectoryAuthService.username();
            log.debug("Access by user {}", username);
            List<Map<String, Object>> updates = iptService.scan(
                    provider, create, check, key, username, true, isShareableWithGBIF);
            log.info("{} data resources to update for {}", updates.size(), uid);
            return ResponseEntity.ok(updates);
        } catch (Exception e) {
            log.error("Problem scanning IPT endpoint: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Problem scanning data provider " + uid));
        }
    }

    @GetMapping(value = "/ws/ipt/syncReport/{uid}", produces = "text/csv")
    public void syncReport(@PathVariable String uid, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=ipt-sync.csv");

        DataProvider provider = dataProviderRepository.findByUid(uid).orElse(null);

        if (provider == null || provider.getWebsiteUrl() == null) {
            PrintWriter writer = response.getWriter();
            writer.println("EML URL,GUID,Title,Number of records in IPT,Number of records in Atlas,Atlas ID");
            writer.flush();
            return;
        }

        try {
            CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(response.getOutputStream()));

            // Build a lookup map: name -> uid
            Map<String, String> nameToUid = new HashMap<>();
            for (DataResource dr : dataResourceRepository.findAll()) {
                String name = dr.getName();
                if (name != null) {
                    int idx = name.toLowerCase().indexOf("- version");
                    if (idx > 0) {
                        nameToUid.put(name.substring(0, idx).trim().toLowerCase(), dr.getUid());
                    } else {
                        nameToUid.put(name.toLowerCase(), dr.getUid());
                    }
                }
            }

            String[] header = {"EML URL", "GUID", "Title",
                    "Number of records in IPT", "Number of records in Atlas", "Atlas ID"};
            csvWriter.writeNext(header);

            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper objectMapper = new ObjectMapper();

            // Fetch IPT inventory
            String inventoryUrl = provider.getWebsiteUrl() + "/inventory/dataset";
            String inventoryJson = restTemplate.getForObject(inventoryUrl, String.class);
            Map<String, Object> iptInventory = objectMapper.readValue(inventoryJson,
                    new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> registeredResources =
                    (List<Map<String, Object>>) iptInventory.get("registeredResources");

            if (registeredResources != null) {
                for (Map<String, Object> item : registeredResources) {
                    String title = (String) item.get("title");
                    String drUid = title != null ? nameToUid.get(title.toLowerCase()) : null;

                    String atlasCount = "0";
                    String atlasId = "Not registered";
                    if (drUid != null) {
                        atlasId = drUid;
                        try {
                            String countUrl = appProperties.getBiocacheServicesUrl()
                                    + "/occurrences/search?pageSize=0&fq=data_resource_uid:" + drUid;
                            String countJson = restTemplate.getForObject(countUrl, String.class);
                            Map<String, Object> countResult = objectMapper.readValue(countJson,
                                    new TypeReference<>() {});
                            atlasCount = String.valueOf(countResult.getOrDefault("totalRecords", 0));
                        } catch (Exception e) {
                            log.warn("Error fetching record count for {}: {}", drUid, e.getMessage());
                        }
                    }

                    String[] row = {
                            String.valueOf(item.get("eml")),
                            String.valueOf(item.get("gbifKey")),
                            title,
                            String.valueOf(item.get("records")),
                            atlasCount,
                            atlasId
                    };
                    csvWriter.writeNext(row);
                }
            }
            csvWriter.flush();
        } catch (Exception e) {
            log.error("Error generating IPT sync report for {}: {}", uid, e.getMessage(), e);
        }
    }
}
