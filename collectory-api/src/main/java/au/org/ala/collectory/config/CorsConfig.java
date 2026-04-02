package au.org.ala.collectory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration.
 *
 * <p>Two policies are applied:
 * <ul>
 *   <li><b>Session endpoints</b> ({@code /session}, {@code /login}, {@code /callback},
 *       {@code /logout}) — explicit allowed origins read from
 *       {@code security.cors.origins}, with {@code allowCredentials=true} so that
 *       the browser sends the {@code session_secret} HttpOnly cookie.</li>
 *   <li><b>All other paths</b> — wildcard origin ({@code *}) with no credentials,
 *       preserving the previous behaviour for the REST API.</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    @Value("${security.cors.origins:http://localhost:3000}")
    private String corsOriginsProperty;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // ── Session endpoints — credentials allowed ────────────────────────────
        // allowCredentials=true requires explicit origins (wildcard is forbidden by the spec)
        List<String> allowedOrigins = Arrays.asList(corsOriginsProperty.split(","));
        CorsConfiguration sessionCors = new CorsConfiguration();
        sessionCors.setAllowedOrigins(allowedOrigins);
        sessionCors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        sessionCors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Origin"));
        sessionCors.setAllowCredentials(true);
        source.registerCorsConfiguration("/session", sessionCors);
        source.registerCorsConfiguration("/login", sessionCors);
        source.registerCorsConfiguration("/callback", sessionCors);
        source.registerCorsConfiguration("/logout", sessionCors);

        // ── REST API — wildcard origin, no credentials ─────────────────────────
        CorsConfiguration apiCors = new CorsConfiguration();
        apiCors.setAllowedOrigins(List.of("*"));
        apiCors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS"));
        apiCors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        source.registerCorsConfiguration("/**", apiCors);

        return source;
    }
}
