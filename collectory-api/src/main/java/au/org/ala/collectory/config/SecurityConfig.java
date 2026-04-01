package au.org.ala.collectory.config;

import au.org.ala.ws.security.AlaWebServiceAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Spring Security configuration for the Collectory API.
 *
 * <p>Security model (mirrors the Grails app):
 * <ul>
 *   <li>All {@code GET} requests to public/read endpoints are permitted without authentication.</li>
 *   <li>Write operations ({@code POST}, {@code PUT}, {@code DELETE}) on the data-management
 *       web-service paths ({@code /ws/**}) require authentication.  Fine-grained role checks
 *       are delegated to {@link au.org.ala.collectory.security.PermissionChecker}.</li>
 *   <li>Admin endpoints ({@code /ws/admin/**}) require {@code ROLE_ADMIN}.</li>
 *   <li>Actuator, OpenAPI docs, and static assets are always accessible.</li>
 * </ul>
 *
 * <p>JWT / M2M tokens are validated by the ALA {@link AlaWebServiceAuthFilter} (provided by
 * {@code ala-ws-spring-security}) which is inserted before Spring Security's
 * {@link BasicAuthenticationFilter}.  It populates the {@link org.springframework.security.core.context.SecurityContext}
 * with the user's roles so that both URL-level rules and {@link au.org.ala.collectory.security.PermissionChecker}
 * can consult them.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Order(1)
public class SecurityConfig {

    /**
     * The ALA JWT/token filter — auto-configured by {@code ala-ws-spring-security}'s
     * {@code AlaWsSpringSecurityConfiguration} auto-configuration class.  It is optional
     * here so the application still starts in test environments where pac4j beans are absent.
     */
    @Autowired(required = false)
    private AlaWebServiceAuthFilter alaWebServiceAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // ── CSRF / session ────────────────────────────────────────────────────
        // Stateless REST API — no CSRF needed and no HTTP sessions
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // ── CORS ──────────────────────────────────────────────────────────────
        http.cors(cors -> cors.configurationSource(new CorsConfig().corsConfigurationSource()));

        // ── Frame options ─────────────────────────────────────────────────────
        http.headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny));

        // ── JWT filter (C6) ───────────────────────────────────────────────────
        // Insert the ALA filter that validates Bearer tokens and populates the
        // SecurityContext before Spring Security's own auth filter runs.
        if (alaWebServiceAuthFilter != null) {
            http.addFilterBefore(alaWebServiceAuthFilter, BasicAuthenticationFilter.class);
        }

        // ── URL-level authorization (C5) ──────────────────────────────────────
        http.authorizeHttpRequests(auth -> auth

            // ── Always public ──────────────────────────────────────────────
            // Actuator, OpenAPI, static SPA assets
            .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/favicon.ico",
                    "/error"
            ).permitAll()

            // Public read-only API — no auth required
            .requestMatchers(HttpMethod.GET,
                    "/ws/**",
                    "/public/**",
                    "/dataFeed/**",
                    "/lookup/**"
            ).permitAll()

            // IPT / EML / reports feeds — always public
            .requestMatchers(
                    "/ipt/**",
                    "/eml/**",
                    "/reports/**",
                    "/sitemap/**",
                    "/licence/**"
            ).permitAll()

            // ── Admin endpoints — ROLE_ADMIN only ──────────────────────────
            .requestMatchers("/ws/admin/**").hasRole("ADMIN")

            // ── Mutating WS operations — any authenticated user ─────────────
            // Fine-grained role checking (EDITOR vs ADMIN) is done by
            // PermissionChecker based on @PermissionRequired annotations.
            .requestMatchers(HttpMethod.POST,   "/ws/**").authenticated()
            .requestMatchers(HttpMethod.PUT,    "/ws/**").authenticated()
            .requestMatchers(HttpMethod.DELETE, "/ws/**").authenticated()
            .requestMatchers(HttpMethod.PATCH,  "/ws/**").authenticated()

            // ── Manage endpoints — authenticated ────────────────────────────
            .requestMatchers("/ws/manage/**").authenticated()

            // ── Everything else — permit (read-only public data) ────────────
            .anyRequest().permitAll()
        );

        return http.build();
    }
}
