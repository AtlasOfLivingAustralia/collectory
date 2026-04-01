package au.org.ala.collectory.service;

import org.springframework.stereotype.Service;

/**
 * Stub for {@code InstitutionCodeLoaderService} (Grails equivalent).
 *
 * <p>The original service loaded institution names and codes from an AFD XML file
 * at bootstrap time to help assign institution codes when inserting new institutions.
 * It is NOT used at runtime to serve API requests.
 *
 * <p>This functionality is a one-off data-bootstrap utility and has intentionally
 * not been ported to the Spring Boot application (L17 — LOW priority).
 * If it is ever needed, the AFD XML endpoint URL should be read from application
 * config (e.g. {@code collectory.institution.codeLoaderURL}) and the XML parsed
 * to produce a {@code Map<String, String>} of institutionName → code.
 */
@Service
public class InstitutionCodeLoaderService {

    /**
     * Not implemented. See class javadoc.
     *
     * @throws UnsupportedOperationException always
     */
    public String lookupInstitutionCode(String institutionName) {
        throw new UnsupportedOperationException(
                "InstitutionCodeLoaderService is a bootstrap-only utility and has not been ported.");
    }
}
