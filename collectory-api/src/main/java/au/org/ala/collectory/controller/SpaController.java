package au.org.ala.collectory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Catch-all controller that forwards non-API requests to the React SPA's index.html.
 * API endpoints (/ws/*, /rif-cs, /feed, /eml/*, /sitemap*, /data/*, /upload/*) are
 * handled by their own controllers and are NOT forwarded.
 */
@Controller
public class SpaController {

    @GetMapping(value = {
            "/",
            "/datasets",
            "/public/**",
            "/manage/**",
            "/collection/**",
            "/institution/**",
            "/dataProvider/**",
            "/dataResource/**",
            "/dataHub/**",
            "/tempDataResource/**",
            "/contact/**",
            "/reports/**",
            "/admin/gbif/**",
            "/licence/**",
            "/providerCode/**",
            "/providerMap/**",
            "/auditLogEvent/**"
    })
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
