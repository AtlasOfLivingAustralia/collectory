/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package au.org.ala.collectory.controller;

import au.org.ala.collectory.dto.UserInfoDto;
import au.org.ala.collectory.service.SessionAuthService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * Session-based auth endpoints consumed by {@code @ala/common-ui}:
 * <ul>
 *   <li>GET /session  — returns {@link UserInfoDto} JSON</li>
 *   <li>GET /login    — initiates OIDC PKCE login redirect</li>
 *   <li>GET /callback — handles OIDC provider callback, exchanges code for tokens</li>
 *   <li>GET /logout   — revokes tokens, clears cookies, redirects to provider logout</li>
 * </ul>
 *
 * <p>Mirrors {@code search-service}'s {@code AuthController}.
 */
@Hidden
@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class AuthController {

    private final SessionAuthService sessionAuthService;

    @Value("#{'${security.cors.origins}'.split(',')}")
    private List<String> corsOrigins;

    /**
     * Called by {@code @ala/common-ui}'s {@code checkLoginState()}.
     * Returns user info if a valid session exists, or {@code {authenticated: false}}.
     */
    @GetMapping(path = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public UserInfoDto session(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Origin", required = false) String origin) {

        boolean allowedOrigin = origin != null && corsOrigins.stream()
                .anyMatch(o -> origin.equals(o) || origin.startsWith(o + "/"));
        if (!allowedOrigin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }

        // Do not create a new session — only read an existing one
        HttpSession session = request.getSession(false);
        if (session == null) {
            return UserInfoDto.builder().authenticated(false).build();
        }

        String secret = sessionAuthService.getSecret(request);
        if (secret == null) {
            return UserInfoDto.builder().authenticated(false).build();
        }

        String secretDebug = sessionAuthService.getSecretDebug(request);
        UserInfoDto userInfo = sessionAuthService.getTokenInfo(response, session, secret, origin, secretDebug);
        return userInfo != null ? userInfo : UserInfoDto.builder().authenticated(false).build();
    }

    /**
     * Initiates OIDC login. Browser is redirected here when the user clicks Login.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @RequestParam String path,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!sessionAuthService.isAllowedRedirectOnly(path)) {
            log.warn("Blocked login redirect: returnPath={}", path);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            String origin = "login";
            String url = sessionAuthService.getLoginPath(response, session, path, sessionAuthService.getSecret(request), origin);
            if (url == null) {
                log.warn("Failed to generate login path");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", url);
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate PKCE code verifier/challenge", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * OIDC provider callback — exchanges the authorisation code for tokens.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        String secret = sessionAuthService.getSecret(request);
        String origin = "callback";

        if (StringUtils.isNotEmpty(secret) && sessionAuthService.isSessionLoggedIn(response, session, secret, origin)) {
            String returnPath = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", returnPath);
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        }

        try {
            secret = sessionAuthService.generateAndSetSecret(response, origin);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate session secret", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        String returnPath = sessionAuthService.validateStateAndGetReturnPath(response, session, secret, code, state, origin);
        if (returnPath == null) {
            log.warn("Possibly invalid or expired state parameter: {}", state);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", returnPath);
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    /**
     * Called by {@code @ala/common-ui}'s {@code handleLogout()}.
     * Revokes tokens, clears session cookies and redirects to the OIDC logout endpoint.
     */
    @GetMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestParam String path,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!sessionAuthService.isAllowedRedirectOnly(path)) {
            log.warn("Blocked logout redirect: returnPath={}", path);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String logoutPath = sessionAuthService.logoutPath(path, session, request, response);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", logoutPath);
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }
}
