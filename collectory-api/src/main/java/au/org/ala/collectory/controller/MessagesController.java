package au.org.ala.collectory.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Exposes the application i18n message keys as raw text/plain, matching the
 * Grails MessagesController behaviour used by the JavaScript i18n library.
 *
 * GET /messages/i18n  →  messages.properties file content as text/plain
 */
@RestController
@Slf4j
public class MessagesController {

    /**
     * Return all message keys from messages.properties as {@code key=value} lines.
     * The response is {@code text/plain; charset=UTF-8} to match the Grails original.
     */
    @GetMapping(value = "/messages/i18n", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> i18n() {
        try {
            ClassPathResource resource = new ClassPathResource("messages.properties");
            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // Sort lines for stable output, matching the Grails implementation
                String[] lines = content.split("\n");
                java.util.Arrays.sort(lines);
                String sorted = String.join("\n", lines);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                        .body(sorted);
            }
        } catch (IOException e) {
            log.warn("Could not read messages.properties: {}", e.getMessage());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                    .body("");
        }
    }
}
