package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves static files from the data and upload directories.
 * These directories contain entity images (logos, collection images)
 * and uploaded files (DwC archives, etc.).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FileController {

    private final AppProperties appProperties;

    /**
     * Serves files from the data/images repository.
     * URL pattern: /data/{entityType}/{filename}
     * e.g. /data/collection/image.jpg, /data/institution/logo.png
     */
    @GetMapping("/data/**")
    public ResponseEntity<Resource> serveDataFile(HttpServletRequest request) {
        return serveFile(request, "/data/", appProperties.getRepositoryLocationImages());
    }

    /**
     * Serves files from the upload directory.
     * URL pattern: /upload/{filename}
     */
    @GetMapping("/upload/**")
    public ResponseEntity<Resource> serveUploadFile(HttpServletRequest request) {
        return serveFile(request, "/upload/", appProperties.getUploadFilePath());
    }

    private ResponseEntity<Resource> serveFile(HttpServletRequest request, String prefix, String basePath) {
        String requestPath = request.getRequestURI();
        // Strip context path and prefix
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            requestPath = requestPath.substring(contextPath.length());
        }
        String relativePath = requestPath.substring(prefix.length());

        // Security: prevent directory traversal
        Path resolved = Path.of(basePath).resolve(relativePath).normalize();
        if (!resolved.startsWith(Path.of(basePath).normalize())) {
            log.warn("Directory traversal attempt blocked: {}", relativePath);
            return ResponseEntity.notFound().build();
        }

        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String contentType = Files.probeContentType(resolved);
            MediaType mediaType = contentType != null
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;

            Resource resource = new FileSystemResource(resolved);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(resource);
        } catch (IOException e) {
            log.error("Error serving file: {}", resolved, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
