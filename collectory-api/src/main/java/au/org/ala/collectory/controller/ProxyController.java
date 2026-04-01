package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Proxies requests to external ALA services (biocache, logger) to avoid CORS issues
 * when the SPA frontend runs on a different origin.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProxyController {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/ws/proxy/biocache/**")
    public ResponseEntity<?> proxyBiocache(HttpServletRequest request) {
        String biocacheUrl = appProperties.getBiocacheServicesUrl();
        if (biocacheUrl == null || biocacheUrl.isBlank()) {
            return ResponseEntity.badRequest().body("Biocache services URL not configured");
        }
        String path = request.getRequestURI().replaceFirst("/ws/proxy/biocache", "");
        String query = request.getQueryString();
        String url = biocacheUrl + path + (query != null ? "?" + query : "");
        try {
            Object result = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Biocache proxy error for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/ws/proxy/logger/**")
    public ResponseEntity<?> proxyLogger(HttpServletRequest request) {
        String loggerUrl = appProperties.getLoggerUrl();
        if (loggerUrl == null || loggerUrl.isBlank()) {
            return ResponseEntity.badRequest().body("Logger URL not configured");
        }
        String path = request.getRequestURI().replaceFirst("/ws/proxy/logger", "");
        String query = request.getQueryString();
        String url = loggerUrl + path + (query != null ? "?" + query : "");
        try {
            Object result = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Logger proxy error for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(java.util.Map.of("error", e.getMessage()));
        }
    }
}
