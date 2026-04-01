package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.dto.*;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.service.*;
import au.org.ala.collectory.util.JSONHelper;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LookupController {

    private final IdGeneratorService idGeneratorService;
    private final ProviderGroupService providerGroupService;
    private final DataResourceService dataResourceService;
    private final CollectionService collectionService;
    private final InstitutionService institutionService;
    private final AppProperties appProperties;

    private final DataResourceRepository dataResourceRepository;
    private final CollectionRepository collectionRepository;
    private final InstitutionRepository institutionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataHubRepository dataHubRepository;
    private final ProviderMapRepository providerMapRepository;
    private final ProviderCodeRepository providerCodeRepository;
    private final TempDataResourceRepository tempDataResourceRepository;

    // L11: also map without /ws/ prefix for backward compat
    @GetMapping({"/ws/lookup/inst/{inst}/coll/{coll}", "/lookup/inst/{inst}/coll/{coll}"})
    public ResponseEntity<?> collection(            @PathVariable String inst,
            @PathVariable String coll) {
        // Look up by institution code + collection code via ProviderMap
        List<ProviderMap> maps = providerMapRepository.findAll();
        for (ProviderMap pm : maps) {
            if (pm.getCollection() == null) continue;
            boolean instMatch = pm.getInstitutionCodes() != null &&
                    pm.getInstitutionCodes().stream().anyMatch(pc -> inst.equals(pc.getCode()));
            boolean collMatch = pm.isMatchAnyCollectionCode() ||
                    (pm.getCollectionCodes() != null &&
                            pm.getCollectionCodes().stream().anyMatch(pc -> coll.equals(pc.getCode())));
            if (instMatch && collMatch) {
                CollectionSummary summary = collectionService.buildSummary(pm.getCollection());
                return ResponseEntity.ok(summary);
            }
        }
        return ResponseEntity.ok(Collections.emptyMap());
    }

    @GetMapping("/ws/lookup/institution/{id}")
    public ResponseEntity<?> institution(@PathVariable String id) {
        ProviderGroup pg = resolveEntity(id, "in");
        if (pg instanceof Institution inst) {
            return ResponseEntity.ok(institutionService.buildSummary(inst));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/ws/lookup/dataProvider/{id}")
    public ResponseEntity<?> dataProvider(@PathVariable String id) {
        ProviderGroup pg = resolveEntity(id, "dp");
        if (pg instanceof DataProvider dp) {
            DataProviderSummary summary = new DataProviderSummary();
            summary.setId(dp.getId());
            summary.setUid(dp.getUid());
            summary.setName(dp.getName());
            summary.setAcronym(dp.getAcronym());
            summary.setUri(dp.buildUri(appProperties.getServerUrl()));
            return ResponseEntity.ok(summary);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/ws/lookup/dataResource/{id}")
    public ResponseEntity<?> dataResource(@PathVariable String id) {
        ProviderGroup pg = resolveEntity(id, "dr");
        if (pg instanceof DataResource dr) {
            return ResponseEntity.ok(dataResourceService.buildSummary(dr));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * L12 + L13: Summary endpoint supports:
     * - UID (co/in/dr/dp/dh prefix)
     * - drt prefix → TempDataResource lookup
     * - LSID (urn:lsid: prefix) → findByGuid
     * - numeric database id → collection fallback
     * - acronym → collection fallback
     *
     * Gap 2: also maps /lookup/summary/{id} (without /ws/) per Grails UrlMappings line 190.
     */
    @GetMapping({"/ws/lookup/summary/{id}", "/lookup/summary/{id}"})
    public ResponseEntity<?> summary(@PathVariable String id) {
        // L13: drt-prefixed UIDs → TempDataResource
        if (id.startsWith("drt")) {
            TempDataResource tdr = tempDataResourceRepository.findByUid(id).orElse(null);
            if (tdr != null) {
                // Match Grails TempDataResource.buildSummary() output exactly:
                // [name, uid, email, firstName, lastName, alaId, dateCreated, lastUpdated, numberOfRecords]
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", tdr.getName());
                m.put("uid", tdr.getUid());
                m.put("email", tdr.getEmail());
                m.put("firstName", tdr.getFirstName());
                m.put("lastName", tdr.getLastName());
                m.put("alaId", tdr.getAlaId());
                m.put("dateCreated", tdr.getDateCreated());
                m.put("lastUpdated", tdr.getLastUpdated());
                m.put("numberOfRecords", tdr.getNumberOfRecords());
                return ResponseEntity.ok(m);
            }
            return ResponseEntity.ok(Map.of("error", "unable to find an entity for id = " + id));
        }

        // Standard UID lookup
        ProviderGroup pg = providerGroupService.get(id);

        // L12: if not found by UID, try LSID / numeric id / acronym (collection fallback)
        if (pg == null) {
            pg = findCollection(id);
        }

        if (pg == null) {
            return ResponseEntity.ok(Map.of("error", "unable to find an entity for id = " + id));
        }

        if (pg instanceof au.org.ala.collectory.domain.Collection co) {
            return ResponseEntity.ok(collectionService.buildSummary(co));
        } else if (pg instanceof Institution inst) {
            return ResponseEntity.ok(institutionService.buildSummary(inst));
        } else if (pg instanceof DataResource dr) {
            return ResponseEntity.ok(dataResourceService.buildSummary(dr));
        } else if (pg instanceof DataProvider dp) {
            DataProviderSummary summary = new DataProviderSummary();
            summary.setId(dp.getId());
            summary.setUid(dp.getUid());
            summary.setName(dp.getName());
            summary.setAcronym(dp.getAcronym());
            summary.setUri(dp.buildUri(appProperties.getServerUrl()));
            return ResponseEntity.ok(summary);
        } else {
            return ResponseEntity.ok(Map.of("uid", pg.getUid(), "name", pg.getName()));
        }
    }

    @GetMapping("/ws/lookup/name/{id}")
    public ResponseEntity<?> name(@PathVariable String id) {
        ProviderGroup pg = providerGroupService.get(id);
        if (pg == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("name", pg.getName()));
    }

    @GetMapping("/ws/lookup/taxonomyCoverageHints/{id}")
    public ResponseEntity<?> taxonomyCoverageHints(@PathVariable String id) {
        ProviderGroup pg = providerGroupService.get(id);
        if (pg == null) return ResponseEntity.notFound().build();
        Object hints = JSONHelper.taxonomyHints(pg.getTaxonomyHints());
        return ResponseEntity.ok(hints != null ? hints : Collections.emptyList());
    }

    @GetMapping("/ws/downloadLimits")
    public ResponseEntity<?> downloadLimits() {
        List<DataResource> resources = dataResourceRepository.findAll();
        List<Map<String, Object>> result = resources.stream()
                .filter(dr -> dr.getDownloadLimit() > 0)
                .map(dr -> Map.<String, Object>of(
                        "uid", dr.getUid(),
                        "name", dr.getName(),
                        "downloadLimit", dr.getDownloadLimit()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * L8 + L9: Citations endpoint.
     * - POST with JSON body: list of UIDs
     * - GET with ?include=uid1,uid2,... (comma-separated) or ?include=all
     * - GET /ws/citations/{include} path-variable form (Grails UrlMappings: /ws/citations/$include?)
     * - Response format based on Accept header or ?type param:
     *   - text/csv or type=csv  → CSV (RFC-4180, opencsv)
     *   - text/tsv or type=tsv  → TSV
     *   - default              → JSON
     */
    @RequestMapping(value = {"/ws/citations", "/ws/citations/{includePathVar}"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> citations(
            @RequestBody(required = false) List<String> bodyUids,
            @PathVariable(name = "includePathVar", required = false) String includePathVar,
            @RequestParam(required = false) String include,
            @RequestParam(required = false) String type,
            HttpServletRequest request) throws IOException {

        // Path variable takes precedence (Grails /ws/citations/$include? style)
        String effectiveInclude = (includePathVar != null && !includePathVar.isBlank()) ? includePathVar : include;

        // Resolve the UID list
        List<String> uids;
        if (bodyUids != null && !bodyUids.isEmpty()) {
            uids = bodyUids;
        } else if (effectiveInclude != null && !effectiveInclude.isBlank()) {
            if ("all".equalsIgnoreCase(effectiveInclude)) {
                uids = dataResourceRepository.findAll(org.springframework.data.domain.Sort.by("uid"))
                        .stream().map(DataResource::getUid).collect(Collectors.toList());
            } else {
                uids = Arrays.asList(effectiveInclude.split(","));
            }
        } else {
            // no UIDs provided — return empty JSON list
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("[]");
        }

        // Determine format: explicit ?type param wins, then Accept header
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        boolean wantCsv = "csv".equalsIgnoreCase(type)
                || (accept != null && (accept.contains("text/csv") || accept.contains("text/plain")));
        boolean wantTsv = "tsv".equalsIgnoreCase(type)
                || (accept != null && accept.contains("text/tsv"));

        if (wantTsv) {
            // TSV format
            StringBuilder sb = new StringBuilder();
            sb.append("Resource name\tCitation\tRights\tMore information\tData generalizations\tInformation withheld\tDownload limit\tUID");
            for (String uid : uids) {
                uid = uid.trim();
                if (uid.startsWith("drt")) {
                    TempDataResource tdr = tempDataResourceRepository.findByUid(uid).orElse(null);
                    if (tdr != null) {
                        sb.append("\n").append(buildTsvCitation(tdr));
                    }
                } else {
                    DataResource dr = dataResourceRepository.findByUid(uid).orElse(null);
                    if (dr != null) {
                        sb.append("\n").append(buildTsvCitation(dr));
                    }
                }
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/tsv; charset=UTF-8")
                    .body(sb.toString());
        }

        if (wantCsv) {
            // CSV format using opencsv
            StringWriter sw = new StringWriter();
            CSVWriter csv = new CSVWriter(sw);
            csv.writeNext(new String[]{"Resource name", "Citation", "Rights", "More information",
                    "Data generalizations", "Information withheld", "Download limit", "UID", "DOI"});
            for (String uid : uids) {
                uid = uid.trim();
                if (uid.startsWith("drt")) {
                    TempDataResource tdr = tempDataResourceRepository.findByUid(uid).orElse(null);
                    if (tdr != null) {
                        csv.writeNext(buildCsvCitationArray(tdr));
                    }
                } else {
                    DataResource dr = dataResourceRepository.findByUid(uid).orElse(null);
                    if (dr != null) {
                        csv.writeNext(buildCsvCitationArray(dr));
                    }
                }
            }
            csv.close();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                    .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                    .body(sw.toString());
        }

        // Default: JSON
        List<Map<String, Object>> result = new ArrayList<>();
        for (String uid : uids) {
            uid = uid.trim();
            Map<String, Object> m = new LinkedHashMap<>();
            if (uid.startsWith("drt")) {
                TempDataResource tdr = tempDataResourceRepository.findByUid(uid).orElse(null);
                if (tdr != null) {
                    m.put("uid", tdr.getUid());
                    m.put("name", tdr.getName());
                    m.put("citation", "This is a temporary data set");
                    m.put("rights", "No explicit rights");
                    result.add(m);
                }
            } else {
                DataResource dr = dataResourceRepository.findByUid(uid).orElse(null);
                if (dr != null) {
                    m.put("uid", dr.getUid());
                    m.put("name", dr.getName());
                    m.put("citation", dr.getCitation());
                    m.put("rights", dr.getRights());
                    m.put("licenseType", dr.getLicenseType());
                    m.put("licenseVersion", dr.getLicenseVersion());
                    if (dr.getDataProvider() != null) {
                        m.put("dataProviderUid", dr.getDataProvider().getUid());
                        m.put("dataProviderName", dr.getDataProvider().getName());
                    }
                    if (dr.getGbifDoi() != null) {
                        m.put("DOI", dr.getGbifDoi());
                    }
                    result.add(m);
                }
            }
        }
        // Serialize to JSON via Jackson
        StringWriter sw = new StringWriter();
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(sw, result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .body(sw.toString());
    }

    /** L10: List all DataResources as CSV, optionally filtered by resourceType. */
    @GetMapping(value = "/ws/lookup/listResources", produces = "text/csv")
    public ResponseEntity<String> listResources(
            @RequestParam(name = "id", required = false) String resourceType) throws IOException {
        List<DataResource> list = (resourceType != null && !resourceType.isBlank())
                ? dataResourceRepository.findAllByResourceType(resourceType,
                        org.springframework.data.domain.Sort.by("name"))
                : dataResourceRepository.findAll(org.springframework.data.domain.Sort.by("name"));

        StringWriter sw = new StringWriter();
        CSVWriter csv = new CSVWriter(sw);
        csv.writeNext(new String[]{"uid", "name"});
        for (DataResource dr : list) {
            csv.writeNext(new String[]{dr.getUid(), dr.getName()});
        }
        csv.close();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(sw.toString());
    }

    @GetMapping({"/ws/find/guid", "/ws/lookup/findResourceByGuid"})
    public ResponseEntity<?> findResourceByGuid(@RequestParam String guid) {
        DataResource dr = dataResourceRepository.findByGuid(guid).orElse(null);
        if (dr == null) return ResponseEntity.ok(Collections.emptyList());
        return ResponseEntity.ok(List.of(dataResourceService.buildSummary(dr)));
    }

    // --- UID generation ---

    @GetMapping("/ws/generateCollectionUid")
    public ResponseEntity<?> generateCollectionUid() {
        return ResponseEntity.ok(Map.of("uid", idGeneratorService.getNextCollectionId()));
    }

    @GetMapping("/ws/generateInstitutionUid")
    public ResponseEntity<?> generateInstitutionUid() {
        return ResponseEntity.ok(Map.of("uid", idGeneratorService.getNextInstitutionId()));
    }

    @GetMapping("/ws/generateDataProviderUid")
    public ResponseEntity<?> generateDataProviderUid() {
        return ResponseEntity.ok(Map.of("uid", idGeneratorService.getNextDataProviderId()));
    }

    @GetMapping("/ws/generateDataResourceUid")
    public ResponseEntity<?> generateDataResourceUid() {
        return ResponseEntity.ok(Map.of("uid", idGeneratorService.getNextDataResourceId()));
    }

    @GetMapping("/ws/generateDataHubUid")
    public ResponseEntity<?> generateDataHubUid() {
        return ResponseEntity.ok(Map.of("uid", idGeneratorService.getNextDataHubId()));
    }

    // --- Citation helpers ---

    /** Build a TSV row for a DataResource. */
    private String buildTsvCitation(DataResource dr) {
        String link = appProperties.getServerUrl() + "/public/show/" + dr.getUid();
        return dr.getName() + "\t"
                + nullToEmpty(dr.getCitation()) + "\t"
                + nullToEmpty(dr.getRights()) + "\t"
                + link + "\t"
                + nullToEmpty(dr.getDataGeneralizations()) + "\t"
                + nullToEmpty(dr.getInformationWithheld()) + "\t"
                + (dr.getDownloadLimit() > 0 ? dr.getDownloadLimit() : "") + "\t"
                + dr.getUid();
    }

    /** Build a TSV row for a TempDataResource. */
    private String buildTsvCitation(TempDataResource tdr) {
        String link = appProperties.getServerUrl() + "/public/show/" + tdr.getUid();
        return tdr.getName() + "\t"
                + "This is a temporary data set\t"
                + "No explicit rights\t"
                + link + "\t\t\t\t"
                + tdr.getUid();
    }

    /** Build a CSV array row for a DataResource. */
    private String[] buildCsvCitationArray(DataResource dr) {
        String link = appProperties.getServerUrl() + "/public/show/" + dr.getUid();
        return new String[]{
                dr.getName(),
                nullToEmpty(dr.getCitation()),
                nullToEmpty(dr.getRights()),
                link,
                nullToEmpty(dr.getDataGeneralizations()),
                nullToEmpty(dr.getInformationWithheld()),
                dr.getDownloadLimit() > 0 ? String.valueOf(dr.getDownloadLimit()) : "",
                dr.getUid(),
                nullToEmpty(dr.getGbifDoi())
        };
    }

    /** Build a CSV array row for a TempDataResource. */
    private String[] buildCsvCitationArray(TempDataResource tdr) {
        String link = appProperties.getServerUrl() + "/public/show/" + tdr.getUid();
        return new String[]{
                tdr.getName(),
                "This is a temporary data set",
                "No explicit rights",
                link,
                "", "", "", tdr.getUid(), ""
        };
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    // --- Entity resolution helpers ---

    private ProviderGroup resolveEntity(String id, String defaultPrefix) {
        // Try as UID first
        if (id.length() > 2 && id.substring(0, 2).matches("in|co|dp|dr|dh")) {
            return providerGroupService.get(id);
        }
        // Try as LSID/GUID
        if (id.startsWith("urn:lsid:")) {
            return findByGuid(id);
        }
        // Try as database ID
        try {
            Long dbId = Long.parseLong(id);
            return switch (defaultPrefix) {
                case "in" -> institutionRepository.findById(dbId).orElse(null);
                case "co" -> collectionRepository.findById(dbId).orElse(null);
                case "dp" -> dataProviderRepository.findById(dbId).orElse(null);
                case "dr" -> dataResourceRepository.findById(dbId).orElse(null);
                case "dh" -> dataHubRepository.findById(dbId).orElse(null);
                default -> null;
            };
        } catch (NumberFormatException e) {
            // Try as GUID
            return findByGuid(id);
        }
    }

    /**
     * L12: Find a collection by LSID, UID, numeric db id, or acronym.
     * Used as fallback in summary() for non-UID identifiers.
     */
    private ProviderGroup findCollection(String id) {
        if (id == null) return null;
        // Try LSID
        if (id.startsWith("urn:lsid:")) {
            Optional<? extends ProviderGroup> r = collectionRepository.findByGuid(id);
            if (r.isPresent()) return r.get();
            r = institutionRepository.findByGuid(id);
            if (r.isPresent()) return r.get();
            r = dataResourceRepository.findByGuid(id);
            return r.orElse(null);
        }
        // Try numeric database id → collection
        try {
            Long dbId = Long.parseLong(id);
            return collectionRepository.findById(dbId).orElse(null);
        } catch (NumberFormatException ignored) {}
        // Try acronym → collection
        return collectionRepository.findByAcronym(id).orElse(null);
    }

    private ProviderGroup findByGuid(String guid) {
        Optional<? extends ProviderGroup> result = dataResourceRepository.findByGuid(guid);
        if (result.isPresent()) return result.get();
        result = collectionRepository.findByGuid(guid);
        if (result.isPresent()) return result.get();
        result = institutionRepository.findByGuid(guid);
        if (result.isPresent()) return result.get();
        return null;
    }
}
