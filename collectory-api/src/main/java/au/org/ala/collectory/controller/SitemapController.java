package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SitemapController {

    private final AppProperties appProperties;

    @GetMapping(value = {"/sitemap.xml", "/sitemap{idx}.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Resource> sitemap(@PathVariable(required = false) Integer idx) {
        if (!appProperties.isSitemapEnabled()) {
            return ResponseEntity.notFound().build();
        }

        String dir = appProperties.getSitemapDir();
        File file;
        if (idx == null) {
            file = new File(dir + "/sitemap.xml");
        } else {
            file = new File(dir + "/sitemap" + idx + ".xml");
        }

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(new FileSystemResource(file));
    }
}
