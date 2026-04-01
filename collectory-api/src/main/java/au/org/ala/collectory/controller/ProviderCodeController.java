package au.org.ala.collectory.controller;

import au.org.ala.collectory.domain.ProviderCode;
import au.org.ala.collectory.repository.ProviderCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ProviderCodeController {

    private final ProviderCodeRepository providerCodeRepository;

    @GetMapping("/ws/providerCode")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(providerCodeRepository.findAll(Sort.by("code")));
    }

    @GetMapping("/ws/providerCode/{id}")
    public ResponseEntity<?> show(@PathVariable Long id) {
        return providerCodeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/ws/providerCode")
    public ResponseEntity<?> create(@RequestBody ProviderCode providerCode) {
        providerCode.setId(null);
        return ResponseEntity.ok(providerCodeRepository.save(providerCode));
    }

    @PutMapping("/ws/providerCode/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        ProviderCode pc = providerCodeRepository.findById(id).orElse(null);
        if (pc == null) return ResponseEntity.notFound().build();
        if (updates.containsKey("code")) pc.setCode((String) updates.get("code"));
        return ResponseEntity.ok(providerCodeRepository.save(pc));
    }

    @DeleteMapping("/ws/providerCode/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ProviderCode pc = providerCodeRepository.findById(id).orElse(null);
        if (pc == null) return ResponseEntity.notFound().build();
        try {
            providerCodeRepository.delete(pc);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        } catch (Exception e) {
            log.error("Could not delete providerCode {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not delete — it may be in use"));
        }
    }
}
