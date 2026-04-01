package au.org.ala.collectory.controller;

import au.org.ala.collectory.domain.ProviderCode;
import au.org.ala.collectory.domain.ProviderMap;
import au.org.ala.collectory.repository.CollectionRepository;
import au.org.ala.collectory.repository.InstitutionRepository;
import au.org.ala.collectory.repository.ProviderCodeRepository;
import au.org.ala.collectory.repository.ProviderMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProviderMapController {

    private final ProviderMapRepository providerMapRepository;
    private final ProviderCodeRepository providerCodeRepository;
    private final CollectionRepository collectionRepository;
    private final InstitutionRepository institutionRepository;

    private Map<String, Object> toDto(ProviderMap pm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pm.getId());
        m.put("version", pm.getVersion());
        m.put("exact", pm.isExact());
        m.put("matchAnyCollectionCode", pm.isMatchAnyCollectionCode());
        m.put("warning", pm.getWarning());
        m.put("dateCreated", pm.getDateCreated());
        m.put("lastUpdated", pm.getLastUpdated());
        if (pm.getCollection() != null) {
            m.put("collection", Map.of("uid", pm.getCollection().getUid(), "name", pm.getCollection().getName() != null ? pm.getCollection().getName() : ""));
        } else {
            m.put("collection", null);
        }
        if (pm.getInstitution() != null) {
            m.put("institution", Map.of("uid", pm.getInstitution().getUid(), "name", pm.getInstitution().getName() != null ? pm.getInstitution().getName() : ""));
        } else {
            m.put("institution", null);
        }
        m.put("collectionCodes", pm.getCollectionCodes().stream().map(ProviderCode::getCode).sorted().collect(Collectors.toList()));
        m.put("institutionCodes", pm.getInstitutionCodes().stream().map(ProviderCode::getCode).sorted().collect(Collectors.toList()));
        return m;
    }

    @GetMapping("/ws/providerMap")
    public ResponseEntity<?> list() {
        List<Map<String, Object>> result = providerMapRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ws/providerMap/{id}")
    public ResponseEntity<?> show(@PathVariable Long id) {
        return providerMapRepository.findById(id)
                .map(pm -> ResponseEntity.ok(toDto(pm)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PostMapping("/ws/providerMap")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        ProviderMap pm = new ProviderMap();
        applyBody(pm, body);
        return ResponseEntity.ok(toDto(providerMapRepository.save(pm)));
    }

    @Transactional
    @PutMapping("/ws/providerMap/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ProviderMap pm = providerMapRepository.findById(id).orElse(null);
        if (pm == null) return ResponseEntity.notFound().build();
        applyBody(pm, body);
        return ResponseEntity.ok(toDto(providerMapRepository.save(pm)));
    }

    @Transactional
    @DeleteMapping("/ws/providerMap/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ProviderMap pm = providerMapRepository.findById(id).orElse(null);
        if (pm == null) return ResponseEntity.notFound().build();
        try {
            providerMapRepository.delete(pm);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (Exception e) {
            log.error("Could not delete providerMap {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not delete — it may be in use"));
        }
    }

    private void applyBody(ProviderMap pm, Map<String, Object> body) {
        if (body.containsKey("exact")) pm.setExact(Boolean.TRUE.equals(body.get("exact")));
        if (body.containsKey("matchAnyCollectionCode")) pm.setMatchAnyCollectionCode(Boolean.TRUE.equals(body.get("matchAnyCollectionCode")));
        if (body.containsKey("warning")) pm.setWarning((String) body.get("warning"));
        if (body.containsKey("collectionUid")) {
            String uid = (String) body.get("collectionUid");
            pm.setCollection(uid != null ? collectionRepository.findByUid(uid).orElse(null) : null);
        }
        if (body.containsKey("institutionUid")) {
            String uid = (String) body.get("institutionUid");
            pm.setInstitution(uid != null ? institutionRepository.findByUid(uid).orElse(null) : null);
        }
    }
}
