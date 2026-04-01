package au.org.ala.collectory.controller;

import au.org.ala.collectory.domain.Licence;
import au.org.ala.collectory.repository.LicenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LicenceController {

    private final LicenceRepository licenceRepository;

    /**
     * GET /ws/licence — public endpoint returning all available licences.
     */
    @GetMapping("/ws/licence")
    public ResponseEntity<?> index() {
        List<Map<String, Object>> result = licenceRepository.findAll(Sort.by("name")).stream()
                .map(lic -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", lic.getName());
                    m.put("url", lic.getUrl());
                    m.put("imageUrl", lic.getImageUrl());
                    m.put("acronym", lic.getAcronym());
                    m.put("version", lic.getLicenceVersion());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /ws/licence/{id} — get a single licence by ID.
     */
    @GetMapping("/ws/licence/{id}")
    public ResponseEntity<?> show(@PathVariable Long id) {
        return licenceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /ws/licence — create a new licence.
     */
    @PostMapping("/ws/licence")
    public ResponseEntity<?> create(@RequestBody Licence licence) {
        licence.setId(null);
        Licence saved = licenceRepository.save(licence);
        return ResponseEntity.ok(saved);
    }

    /**
     * PUT /ws/licence/{id} — update an existing licence.
     */
    @PutMapping("/ws/licence/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Licence licence = licenceRepository.findById(id).orElse(null);
        if (licence == null) {
            return ResponseEntity.notFound().build();
        }
        if (updates.containsKey("name")) licence.setName((String) updates.get("name"));
        if (updates.containsKey("url")) licence.setUrl((String) updates.get("url"));
        if (updates.containsKey("acronym")) licence.setAcronym((String) updates.get("acronym"));
        if (updates.containsKey("licenceVersion")) licence.setLicenceVersion((String) updates.get("licenceVersion"));
        if (updates.containsKey("imageUrl")) licence.setImageUrl((String) updates.get("imageUrl"));
        Licence saved = licenceRepository.save(licence);
        return ResponseEntity.ok(saved);
    }

    /**
     * DELETE /ws/licence/{id} — delete a licence.
     */
    @DeleteMapping("/ws/licence/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Licence licence = licenceRepository.findById(id).orElse(null);
        if (licence == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            licenceRepository.delete(licence);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (Exception e) {
            log.error("Could not delete licence {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not delete licence — it may be in use"));
        }
    }
}
