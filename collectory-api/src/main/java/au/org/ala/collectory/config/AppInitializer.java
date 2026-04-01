package au.org.ala.collectory.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Application initializer that validates mandatory configuration at startup.
 * Replaces Grails BootStrap.groovy init logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppInitializer implements ApplicationRunner {

    private final AppProperties config;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Collectory application starting...");

        if (config.getGbifDefaultEntityCountry() == null || config.getGbifDefaultEntityCountry().isBlank()) {
            log.warn("gbifDefaultEntityCountry is not configured. GBIF features may not work correctly.");
        }

        log.info("Collectory application initialized successfully");
    }
}
