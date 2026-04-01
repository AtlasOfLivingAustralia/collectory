package au.org.ala.collectory.controller;

import au.org.ala.collectory.repository.AuditLogEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogEventRepository auditLogEventRepository;

    @GetMapping("/ws/auditLogEvent/{id}")
    public ResponseEntity<?> show(@PathVariable Long id) {
        return auditLogEventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
