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
 * <p>Two filter chains are registered:
 * <ol>
 *   <li><b>authFilterChain</b> (Order 1) — handles session-based auth endpoints
 *       ({@code /session}, {@code /login}, {@code /callback}, {@code /logout}).
 *       Sessions are created on-demand; Spring Security logout handling is disabled
 *       because {@code AuthController} manages it directly.</li>
 *   <li><b>apiFilterChain</b> (Order 2) — handles all remaining requests with the
 *       existing stateless JWT / Bearer-token security model.</li>
 * </ol>
 *
 * <p>JWT / M2M tokens are validated by the ALA {@link AlaWebServiceAuthFilter} inserted
 * only into the API chain (chain 2).  Chain 1 is intentionally JWT-free so that the
 * auth endpoints can be reached before any token exists.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The ALA JWT/token filter — auto-configured by {@code ala-ws-spring-security}.
     * Optional so the application still starts in test environments where pac4j beans are absent.
     */
    @Autowired(required = false)
    private AlaWebServiceAuthFilter alaWebServiceAuthFilter;

    // ────────────────────────────────────────────────────────────────────────
    // Chain 1 — Session-based auth endpoints
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Handles {@code /session}, {@code /login}, {@code /callback}, {@code /logout}.
     * Uses session-scoped storage for PKCE state and encrypted tokens.
     * CSRF is disabled (these endpoints use state-parameter / PKCE for CSRF protection).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authFilterChain(HttpSecurity http, CorsConfig corsConfig) throws Exception {
        http
            .securityMatcher("/session", "/login", "/callback", "/logout")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // Disable Spring Security's built-in logout filter — AuthController handles logout directly
            .logout(logout -> logout.logoutUrl("/never-match"))
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Chain 2 — Stateless REST API (unchanged from previous single-chain design)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Handles all non-auth requests with the existing stateless JWT security model.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, CorsConfig corsConfig) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
            .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny));

        if (alaWebServiceAuthFilter != null) {
            http.addFilterBefore(alaWebServiceAuthFilter, BasicAuthenticationFilter.class);
        }

        http.authorizeHttpRequests(auth -> auth

            // ── Always public ────────────────────────────────────────────────
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

            // ── Admin endpoints — ROLE_ADMIN only ────────────────────────────
            .requestMatchers("/ws/admin/**").hasRole("ADMIN")

            // ── Mutating WS operations — any authenticated user ───────────────
            .requestMatchers(HttpMethod.POST,   "/ws/**").authenticated()
            .requestMatchers(HttpMethod.PUT,    "/ws/**").authenticated()
            .requestMatchers(HttpMethod.DELETE, "/ws/**").authenticated()
            .requestMatchers(HttpMethod.PATCH,  "/ws/**").authenticated()

            // ── Manage endpoints — authenticated ─────────────────────────────
            .requestMatchers("/ws/manage/**").authenticated()

            // ── Everything else — permit ──────────────────────────────────────
            .anyRequest().permitAll()
        );

        return http.build();
    }
}
