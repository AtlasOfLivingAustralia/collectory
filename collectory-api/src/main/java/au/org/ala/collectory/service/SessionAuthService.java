/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package au.org.ala.collectory.service;

import au.org.ala.collectory.dto.UserInfoDto;
import au.org.ala.collectory.util.CryptoUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core OIDC PKCE flow and token lifecycle management.
 *
 * <p>Mirrors search-service's {@code SessionAuthService} with adaptations for the
 * collectory-api {@code AppProperties} / application.properties naming conventions.
 */
@Slf4j
@Service
public class SessionAuthService {

    private static final String SESSION_SECRET_COOKIE = "session_secret";
    private static final String SESSION_SECRET_DEBUG_COOKIE = "session_secret_debug";
    public static final String SESSION_AUTH_RESPONSE = "auth_response";
    private static final String SESSION_AUTH_DEBUG = "token_debug";
    private static final String SESSION_AUTH_ERROR = "auth_error";
    private static final String PKCE_CODE_VERIFIER = "pkce_code_verifier";

    /** Aligned with ala-security-project callback path convention. */
    private static final String REDIRECT_PATH = "/callback?client_name=OidcClient";

    // Error message constants (returned to client via UserInfoDto.error)
    public static final String SECRET_INVALID = "secret_invalid";
    public static final String INVALID_REFRESH_TOKEN = "invalid_refresh_token";
    public static final String INVALID_ID_TOKEN = "invalid_id_token";
    public static final String INVALID_TOKEN = "invalid_token";
    public static final String EXCHANGE_FAILED = "exchange_failed";

    private static final int MAX_PATH_LENGTH = 200;

    // Cached OIDC discovery document endpoints (never change at runtime)
    private static String OIDC_AUTH_URL;
    private static String TOKEN_ENDPOINT;
    private static String OIDC_LOGOUT_URL;
    private static String REVOKE_ENDPOINT;

    @Value("#{'${openapi.servers}'.split(',')[0]}")
    private String baseUrl;

    @Value("${security.oidc.scope}")
    private String scope;

    @Value("${security.oidc.discovery-uri}")
    private String discoveryUri;

    @Value("${security.oidc.clientId}")
    private String clientId;

    @Value("${security.oidc.secret}")
    private String secret;

    @Value("#{'${security.cors.origins}'.split(',')}")
    private List<String> corsOrigins;

    @Value("${security.oidc.userIdClaim}")
    private String userIdClaim;

    @Value("${security.oidc.roleClaims}")
    private String roleClaims;

    @Value("${security.cookie.domain}")
    private String cookieDomain;

    @Value("${security.login.maxAgeDays}")
    private int loginMaxAge;

    @Value("${security.cookie.name}")
    private String sessionStatusCookie;

    @Value("${security.cookie.debug}")
    private boolean sessionCookieDebug;

    @Value("${security.cookie.rotate}")
    private boolean rotateSessionCookie;

    @Value("${security.oidc.logoutAction}")
    private String logoutAction;

    @PostConstruct
    public void init() {
        cacheAuthUrls();
    }

    // ────────────────────────────────────────────────────────────────────────
    // OIDC discovery
    // ────────────────────────────────────────────────────────────────────────

    public void cacheAuthUrls() {
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(discoveryUri, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> discovery = mapper.readValue(response.getBody(), Map.class);
                OIDC_AUTH_URL = (String) discovery.get("authorization_endpoint");
                TOKEN_ENDPOINT = (String) discovery.get("token_endpoint");
                OIDC_LOGOUT_URL = (String) discovery.get("end_session_endpoint");
                REVOKE_ENDPOINT = (String) discovery.get("revocation_endpoint");
            }
        } catch (Exception e) {
            log.error("Failed to fetch OIDC discovery document from {}", discoveryUri, e);
        }
    }

    public String fetchAuthUrlFromDiscovery() {
        if (OIDC_AUTH_URL == null) cacheAuthUrls();
        return OIDC_AUTH_URL;
    }

    public String fetchTokenEndpointFromDiscovery() {
        if (TOKEN_ENDPOINT == null) cacheAuthUrls();
        return TOKEN_ENDPOINT;
    }

    public String fetchLogoutUrlFromDiscovery() {
        if (OIDC_LOGOUT_URL == null) cacheAuthUrls();
        return OIDC_LOGOUT_URL;
    }

    public String fetchRevokeEndpointFromDiscovery() {
        if (REVOKE_ENDPOINT == null) cacheAuthUrls();
        return REVOKE_ENDPOINT;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Token exchange / refresh
    // ────────────────────────────────────────────────────────────────────────

    public Map<String, Object> exchangeCodeForToken(String code, String codeVerifier) {
        String tokenEndpoint = fetchTokenEndpointFromDiscovery();
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", baseUrl + REDIRECT_PATH);
        params.put("client_id", clientId);
        if (StringUtils.isNotEmpty(secret)) {
            params.put("client_secret", secret);
        }
        params.put("code_verifier", codeVerifier);

        try {
            ResponseEntity<String> response = doPost(tokenEndpoint, params);
            if (response.getStatusCode() == HttpStatus.OK) {
                return new ObjectMapper().readValue(response.getBody(), Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to exchangeCodeForToken", e);
        }
        return null;
    }

    public Map<String, Object> refreshAccessToken(String refreshToken) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("grant_type", "refresh_token");
            params.put("refresh_token", refreshToken);
            params.put("client_id", clientId);

            ResponseEntity<String> response = doPost(fetchTokenEndpointFromDiscovery(), params);
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> tokenResponse = new ObjectMapper().readValue(response.getBody(), Map.class);
                if (!tokenResponse.containsKey("refresh_token")) {
                    tokenResponse.put("refresh_token", refreshToken);
                }
                return tokenResponse;
            }
        } catch (Exception e) {
            log.error("Failed to refreshAccessToken", e);
        }
        return null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Session management
    // ────────────────────────────────────────────────────────────────────────

    public void saveJWTToSession(Map<String, Object> tokenResponse, HttpSession session, String sessionSecret) throws Exception {
        Map<String, Object> saved = new HashMap<>();
        saved.put("access_token", CryptoUtil.encrypt((String) tokenResponse.get("access_token"), sessionSecret));
        saved.put("id_token", CryptoUtil.encrypt((String) tokenResponse.get("id_token"), sessionSecret));
        saved.put("refresh_token", CryptoUtil.encrypt((String) tokenResponse.get("refresh_token"), sessionSecret));
        saved.put("expires_at", System.currentTimeMillis() + ((Number) tokenResponse.get("expires_in")).longValue() * 1000);
        if (sessionCookieDebug) {
            saved.put(SESSION_AUTH_DEBUG, sessionSecret.substring(0, 4) + "-" + System.currentTimeMillis());
        }
        session.setAttribute(SESSION_AUTH_RESPONSE, saved);
        session.removeAttribute(SESSION_AUTH_ERROR);
    }

    public UserInfoDto getTokenInfo(HttpServletResponse response, HttpSession session, String sessionSecret, String origin, String secretDebug) {
        if (StringUtils.isEmpty(sessionSecret)) {
            log.warn("Missing session_secret cookie");
            return UserInfoDto.builder().authenticated(false).error((String) session.getAttribute(SESSION_AUTH_ERROR)).build();
        }
        if (session.getAttribute(SESSION_AUTH_RESPONSE) == null) {
            return UserInfoDto.builder().authenticated(false).error((String) session.getAttribute(SESSION_AUTH_ERROR)).build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> authResponse = (Map<String, Object>) session.getAttribute(SESSION_AUTH_RESPONSE);
            long expiresAt = (Long) authResponse.get("expires_at");
            boolean refreshed = false;

            if (System.currentTimeMillis() > expiresAt - 60000) {
                String refreshToken = null;
                try {
                    refreshToken = (String) authResponse.get("refresh_token");
                    if (refreshToken != null) {
                        refreshToken = CryptoUtil.decrypt(refreshToken, sessionSecret);
                    } else {
                        log.info("Refresh token missing in session auth response");
                    }
                } catch (Exception e) {
                    log.info("Failed to decrypt refresh token (possible race condition); secretDebug: {}, tokenDebug: {}",
                            secretDebug, authResponse.get(SESSION_AUTH_DEBUG), e);
                }

                if (refreshToken == null) {
                    return UserInfoDto.builder().authenticated(false).error(SECRET_INVALID).build();
                }

                Map<String, Object> newTokens = refreshAccessToken(refreshToken);
                if (newTokens == null || newTokens.get("access_token") == null) {
                    log.error("Failed to refresh access token");
                    session.removeAttribute(SESSION_AUTH_RESPONSE);
                    removeSecret(response);
                    session.setAttribute(SESSION_AUTH_ERROR, INVALID_REFRESH_TOKEN);
                    return UserInfoDto.builder().authenticated(false).error(INVALID_REFRESH_TOKEN).build();
                }

                if (rotateSessionCookie) {
                    sessionSecret = generateAndSetSecret(response, origin);
                }

                if (!newTokens.containsKey("refresh_token")) {
                    newTokens.put("refresh_token", refreshToken);
                }
                saveJWTToSession(newTokens, session, sessionSecret);

                @SuppressWarnings("unchecked")
                Map<String, Object> refreshed2 = (Map<String, Object>) session.getAttribute(SESSION_AUTH_RESPONSE);
                authResponse = refreshed2;
                refreshed = true;
            }

            String accessToken = null;
            try {
                accessToken = (String) authResponse.get("access_token");
                if (accessToken != null) {
                    accessToken = CryptoUtil.decrypt(accessToken, sessionSecret);
                } else {
                    log.info("Access token missing in session auth response");
                }
            } catch (Exception e) {
                log.info("Failed to decrypt access token (possible race condition); secretDebug: {}, tokenDebug: {}, wasRefreshed: {}",
                        secretDebug, authResponse.get(SESSION_AUTH_DEBUG), refreshed, e);
            }

            if (accessToken == null) {
                return UserInfoDto.builder().authenticated(false).error(SECRET_INVALID).build();
            }

            String idToken = CryptoUtil.decrypt((String) authResponse.get("id_token"), sessionSecret);
            expiresAt = (Long) authResponse.get("expires_at");

            String[] parts = idToken.split("\\.");
            if (parts.length == 3) {
                String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                Map<String, Object> payload = new ObjectMapper().readValue(payloadJson, new TypeReference<>() {});

                // Extract roles from ID token using configured claim (e.g. 'ala:role').
                // Fall back to access token if the ID token claim is absent (e.g. 'cognito:groups'
                // is only present in the access token in ALA's Cognito setup).
                String[] roles = toStringArray(payload.get(roleClaims));
                if (roles.length == 0) {
                    String[] accessParts = accessToken.split("\\.");
                    if (accessParts.length == 3) {
                        String accessPayloadJson = new String(Base64.getUrlDecoder().decode(accessParts[1]), StandardCharsets.UTF_8);
                        Map<String, Object> accessPayload = new ObjectMapper().readValue(accessPayloadJson, new TypeReference<>() {});
                        roles = toStringArray(accessPayload.get(roleClaims));
                    }
                }
                log.debug("[SessionAuthService] roles extracted for {}: {}", payload.get(userIdClaim), java.util.Arrays.toString(roles));

                return UserInfoDto.builder()
                        .accessToken(accessToken)
                        .expiresAt(expiresAt)
                        .userId((String) payload.get(userIdClaim))
                        .email((String) payload.get("email"))
                        .firstName((String) payload.get("given_name"))
                        .lastName((String) payload.get("family_name"))
                        .roles(roles)
                        .authenticated(true)
                        .build();
            }

            session.setAttribute(SESSION_AUTH_ERROR, INVALID_ID_TOKEN);
        } catch (Exception e) {
            log.error("Error getting token information", e);
            session.setAttribute(SESSION_AUTH_ERROR, INVALID_TOKEN);
        }
        return UserInfoDto.builder().authenticated(false).error((String) session.getAttribute(SESSION_AUTH_ERROR)).build();
    }

    public boolean isSessionLoggedIn(HttpServletResponse response, HttpSession session, String sessionSecret, String origin) {
        return session.getAttribute(SESSION_AUTH_RESPONSE) != null
                && getTokenInfo(response, session, sessionSecret, origin, origin).isAuthenticated();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Login flow
    // ────────────────────────────────────────────────────────────────────────

    public String getLoginPath(HttpServletResponse response, HttpSession session, String path, String sessionSecret, String origin) throws NoSuchAlgorithmException {
        if (StringUtils.isNotEmpty(sessionSecret) && isSessionLoggedIn(response, session, sessionSecret, origin)) {
            return path;
        }
        return buildLoginUrl(session, path);
    }

    private String buildLoginUrl(HttpSession session, String returnPath) throws NoSuchAlgorithmException {
        String codeVerifier = (String) session.getAttribute(PKCE_CODE_VERIFIER);
        if (codeVerifier == null) {
            codeVerifier = generateCodeVerifier();
        }
        session.setAttribute(PKCE_CODE_VERIFIER, codeVerifier);

        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        String constrained = returnPath.length() > MAX_PATH_LENGTH ? returnPath.substring(0, MAX_PATH_LENGTH) : returnPath;
        String pathState = Base64.getUrlEncoder().withoutPadding().encodeToString(constrained.getBytes(StandardCharsets.UTF_8));

        return fetchAuthUrlFromDiscovery()
                + "?response_type=code"
                + "&client_id=" + clientId
                + "&redirect_uri=" + baseUrl + REDIRECT_PATH
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8)
                + "&state=" + pathState
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";
    }

    public String validateStateAndGetReturnPath(HttpServletResponse response, HttpSession session, String sessionSecret, String code, String state, String origin) {
        String returnPath = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);

        if (isSessionLoggedIn(response, session, sessionSecret, origin)) {
            return returnPath;
        }

        String codeVerifier = (String) session.getAttribute(PKCE_CODE_VERIFIER);
        if (codeVerifier == null) {
            log.warn("Missing PKCE code verifier in session");
            session.removeAttribute(SESSION_AUTH_RESPONSE);
            removeSecret(response);
            session.setAttribute(SESSION_AUTH_ERROR, "timed out");
            return returnPath;
        }

        Map<String, Object> tokens = exchangeCodeForToken(code, codeVerifier);
        if (tokens == null || tokens.get("access_token") == null) {
            log.error("Failed to exchange code for access token");
            session.removeAttribute(SESSION_AUTH_RESPONSE);
            removeSecret(response);
            session.setAttribute(SESSION_AUTH_ERROR, EXCHANGE_FAILED);
            return returnPath;
        }

        try {
            saveJWTToSession(tokens, session, sessionSecret);
        } catch (Exception e) {
            log.error("Failed to save tokens to session", e);
            session.removeAttribute(SESSION_AUTH_RESPONSE);
            removeSecret(response);
            session.setAttribute(SESSION_AUTH_ERROR, "save to session failed");
        }

        return returnPath;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Logout
    // ────────────────────────────────────────────────────────────────────────

    public String logoutPath(String path, HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        String sessionSecret = getSecret(request);
        if (sessionSecret == null || session.getAttribute(SESSION_AUTH_RESPONSE) == null) {
            return path;
        }

        String origin = request.getHeader("Origin");
        UserInfoDto userInfo = getTokenInfo(response, session, sessionSecret, origin, getSecretDebug(request));
        if (!userInfo.isAuthenticated()) {
            return path;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> authResponse = (Map<String, Object>) session.getAttribute(SESSION_AUTH_RESPONSE);
        String refreshToken = null;
        String idToken = null;
        try {
            refreshToken = CryptoUtil.decrypt((String) authResponse.get("refresh_token"), sessionSecret);
            idToken = CryptoUtil.decrypt((String) authResponse.get("id_token"), sessionSecret);
        } catch (Exception e) {
            log.info("Failed to decrypt tokens for logout (possible race condition)", e);
            return path;
        }

        // revoke the token
        String revokeUrl = fetchRevokeEndpointFromDiscovery();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("token", refreshToken);
            ResponseEntity<String> revokeResponse;
            if ("COGNITO".equalsIgnoreCase(logoutAction)) {
                // Cognito revocation endpoint requires HTTP Basic auth (client_id:client_secret)
                revokeResponse = doPostWithBasicAuth(revokeUrl, params, clientId, secret);
            } else {
                params.put("token_type_hint", "refresh_token");
                params.put("client_id", clientId);
                if (StringUtils.isNotEmpty(secret)) {
                    params.put("client_secret", secret);
                }
                revokeResponse = doPost(revokeUrl, params);
            }
            if (revokeResponse.getStatusCode() != HttpStatus.OK) {
                log.warn("Failed to revoke token, status: {}", revokeResponse.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to revoke access token", e);
        }

        session.removeAttribute(SESSION_AUTH_RESPONSE);
        removeSecret(response);

        String logoutUrl = fetchLogoutUrlFromDiscovery();
        String encodedRedirect = URLEncoder.encode(path, StandardCharsets.UTF_8);

        if ("COGNITO".equalsIgnoreCase(logoutAction)) {
            return logoutUrl + "?client_id=" + clientId + "&redirect_uri=" + encodedRedirect + "&response_type=code";
        } else {
            return logoutUrl + "?post_logout_redirect_uri=" + encodedRedirect
                    + "&id_token_hint=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Cookie helpers
    // ────────────────────────────────────────────────────────────────────────

    public String getSecret(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        String found = null;
        int count = 0;
        for (Cookie c : cookies) {
            if (SESSION_SECRET_COOKIE.equals(c.getName())) {
                found = c.getValue();
                count++;
            }
        }
        if (sessionCookieDebug && count > 1) {
            log.warn("Multiple {} cookies found: {}", SESSION_SECRET_COOKIE, count);
        }
        return found;
    }

    public String getSecretDebug(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (SESSION_SECRET_DEBUG_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    public String generateAndSetSecret(HttpServletResponse response, String origin) throws NoSuchAlgorithmException {
        String newSecret = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(SecureRandom.getInstanceStrong().generateSeed(32));

        Cookie secretCookie = new Cookie(SESSION_SECRET_COOKIE, newSecret);
        secretCookie.setHttpOnly(true);
        if (!baseUrl.startsWith("http://localhost")) {
            secretCookie.setSecure(true);
        }
        secretCookie.setPath("/");
        secretCookie.setMaxAge(loginMaxAge * 24 * 60 * 60);
        response.addCookie(secretCookie);

        Cookie statusCookie = new Cookie(sessionStatusCookie, "true");
        statusCookie.setHttpOnly(false);
        statusCookie.setPath("/");
        statusCookie.setMaxAge(loginMaxAge * 24 * 60 * 60);
        if (StringUtils.isNotEmpty(cookieDomain) && !baseUrl.startsWith("http://localhost")) {
            statusCookie.setDomain(cookieDomain);
        }
        response.addCookie(statusCookie);

        if (sessionCookieDebug) {
            Cookie debugCookie = new Cookie(SESSION_SECRET_DEBUG_COOKIE,
                    newSecret.substring(0, 4) + '-' + System.currentTimeMillis() + '-'
                            + URLEncoder.encode(origin, StandardCharsets.UTF_8));
            debugCookie.setHttpOnly(false);
            debugCookie.setPath("/");
            debugCookie.setMaxAge(loginMaxAge * 24 * 60 * 60);
            if (StringUtils.isNotEmpty(cookieDomain) && !baseUrl.startsWith("http://localhost")) {
                debugCookie.setDomain(cookieDomain);
            }
            response.addCookie(debugCookie);
        }

        return newSecret;
    }

    public void removeSecret(HttpServletResponse response) {
        Cookie secretCookie = new Cookie(SESSION_SECRET_COOKIE, "");
        secretCookie.setHttpOnly(true);
        if (!baseUrl.startsWith("http://localhost")) {
            secretCookie.setSecure(true);
        }
        secretCookie.setPath("/");
        secretCookie.setMaxAge(0);
        response.addCookie(secretCookie);

        Cookie statusCookie = new Cookie(sessionStatusCookie, "true");
        statusCookie.setHttpOnly(false);
        statusCookie.setPath("/");
        statusCookie.setMaxAge(0);
        if (StringUtils.isNotEmpty(cookieDomain) && !baseUrl.startsWith("http://localhost")) {
            statusCookie.setDomain(cookieDomain);
        }
        response.addCookie(statusCookie);

        if (!sessionCookieDebug) {
            Cookie debugCookie = new Cookie(SESSION_SECRET_DEBUG_COOKIE, "");
            debugCookie.setHttpOnly(false);
            debugCookie.setPath("/");
            debugCookie.setMaxAge(0);
            if (StringUtils.isNotEmpty(cookieDomain) && !baseUrl.startsWith("http://localhost")) {
                debugCookie.setDomain(cookieDomain);
            }
            response.addCookie(debugCookie);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // CORS / redirect guards
    // ────────────────────────────────────────────────────────────────────────

    public boolean isAllowedRedirectOnly(String returnPath) {
        if (StringUtils.isEmpty(returnPath)) return false;
        return corsOrigins.stream().anyMatch(returnPath::startsWith);
    }

    public boolean isAllowedRedirect(String returnPath, String origin) {
        boolean allowed = corsOrigins.stream().anyMatch(returnPath::startsWith);
        boolean allowedOrigin = corsOrigins.stream().anyMatch(o -> origin != null && origin.startsWith(o));
        return allowed && allowedOrigin;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utility
    // ────────────────────────────────────────────────────────────────────────

    public String generateCodeVerifier() {
        byte[] code = new byte[32];
        new SecureRandom().nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    public String[] toStringArray(Object obj) {
        if (obj instanceof String s) return s.split(",");
        if (obj instanceof String[] arr) return arr;
        if (obj instanceof List<?> list) return list.stream().map(Object::toString).toArray(String[]::new);
        return new String[0];
    }

    private ResponseEntity<String> doPost(String url, Map<String, String> params) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!body.isEmpty()) body.append("&");
            body.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return restTemplate.postForEntity(url, new HttpEntity<>(body.toString(), headers), String.class);
    }

    private ResponseEntity<String> doPostWithBasicAuth(String url, Map<String, String> params,
                                                        String basicUser, String basicPass) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(basicUser, basicPass);
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!body.isEmpty()) body.append("&");
            body.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return restTemplate.postForEntity(url, new HttpEntity<>(body.toString(), headers), String.class);
    }
}
