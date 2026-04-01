package au.org.ala.collectory.controller;

import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.service.DataLoaderService;
import au.org.ala.collectory.service.MetadataService;
import au.org.ala.collectory.service.ProviderGroupService;
import au.org.ala.collectory.service.SitemapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/ws/admin", "/admin"})
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final InstitutionRepository institutionRepository;
    private final CollectionRepository collectionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final ProviderMapRepository providerMapRepository;
    private final ProviderCodeRepository providerCodeRepository;
    private final MetadataService metadataService;
    private final SitemapService sitemapService;
    private final DataLoaderService dataLoaderService;

    @GetMapping("/export")
    public ResponseEntity<?> export(@RequestParam(required = false) String table) {
        if (table != null) {
            return switch (table) {
                case "contact" -> ResponseEntity.ok(contactRepository.findAll());
                case "contactFor" -> ResponseEntity.ok(contactForRepository.findAll());
                case "collection" -> ResponseEntity.ok(collectionRepository.findAll());
                case "institution" -> ResponseEntity.ok(institutionRepository.findAll());
                case "providerCode" -> ResponseEntity.ok(providerCodeRepository.findAll());
                case "providerMap" -> ResponseEntity.ok(providerMapRepository.findAll());
                default -> ResponseEntity.ok(Map.of("error", "no such table"));
            };
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contact", contactRepository.findAll());
        result.put("contactFor", contactForRepository.findAll());
        result.put("collection", collectionRepository.findAll());
        result.put("institution", institutionRepository.findAll());
        result.put("providerCode", providerCodeRepository.findAll());
        result.put("providerMap", providerMapRepository.findAll());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/connectionProfiles")
    public ResponseEntity<?> connectionProfiles() {
        return ResponseEntity.ok(metadataService.getConnectionProfiles());
    }

    @GetMapping("/connectionProfile/{profile}")
    public ResponseEntity<?> connectionProfile(@PathVariable String profile) {
        return ResponseEntity.ok(metadataService.getConnectionProfile(profile));
    }

    @GetMapping("/connectionParameters")
    public ResponseEntity<?> connectionParameters() {
        return ResponseEntity.ok(metadataService.getConnectionParameters());
    }

    @PostMapping("/clearConnectionProfiles")
    public ResponseEntity<?> clearConnectionProfiles() {
        metadataService.clearConnectionProfiles();
        metadataService.clearConnectionParameters();
        return ResponseEntity.ok(Map.of("message", "Done"));
    }

    @PostMapping("/buildSitemap")
    public ResponseEntity<?> buildSitemap() {
        try {
            sitemapService.build();
            return ResponseEntity.ok(Map.of("message", "done"));
        } catch (Exception e) {
            log.error("Error building sitemap: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to build sitemap: " + e.getMessage()));
        }
    }

    /**
     * Idempotent import of DataProviders from a tab-separated file.
     * Mirrors Grails AdminController#importDataProviders.
     *
     * @param filename optional path override; defaults to /data/collectory/bootstrap/data_providers.txt
     */
    @PostMapping("/importDataProviders")
    public ResponseEntity<?> importDataProviders(
            @RequestParam(required = false) String filename) {
        String path = (filename != null && !filename.isEmpty())
                ? filename
                : "/data/collectory/bootstrap/data_providers.txt";
        int before = (int) dataProviderRepository.count();
        Map<String, Integer> result = dataLoaderService.importDataProviders(path);
        int after = (int) dataProviderRepository.count();
        Map<String, Object> response = new LinkedHashMap<>(result);
        response.put("beforeCount", before);
        response.put("afterCount", after);
        return ResponseEntity.ok(response);
    }

    /**
     * Idempotent import of DataResources from a comma-separated file.
     * Mirrors Grails AdminController#importDataResources.
     *
     * @param filename optional path override; defaults to /data/collectory/bootstrap/data_resources.txt
     */
    @PostMapping("/importDataResources")
    public ResponseEntity<?> importDataResources(
            @RequestParam(required = false) String filename) {
        String path = (filename != null && !filename.isEmpty())
                ? filename
                : "/data/collectory/bootstrap/data_resources.txt";
        int before = (int) dataResourceRepository.count();
        Map<String, Integer> result = dataLoaderService.importDataResources(path);
        int after = (int) dataResourceRepository.count();
        Map<String, Object> response = new LinkedHashMap<>(result);
        response.put("beforeCount", before);
        response.put("afterCount", after);
        return ResponseEntity.ok(response);
    }

    /**
     * One-time JSON migration import.
     * The Grails implementation reads /data/collectory/bootstrap/export.json and rebuilds
     * institutions, collections, provider maps and contacts from scratch using raw SQL.
     * This endpoint is intentionally not ported to the Spring Boot API —
     * use Flyway migrations or a data migration script instead.
     */
    @PostMapping("/importJson")
    public ResponseEntity<?> importJson() {
        return ResponseEntity.status(501)
                .body(Map.of("error",
                        "importJson is not supported in the Spring Boot API. " +
                        "Use Flyway migrations or a manual data migration script to seed bootstrap data."));
    }
}
