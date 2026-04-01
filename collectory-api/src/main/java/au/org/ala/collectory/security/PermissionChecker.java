package au.org.ala.collectory.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collection;

/**
 * Interceptor that checks {@link PermissionRequired} annotations on controller methods
 * and enforces role-based authorization.
 *
 * <p>Mirrors the Grails {@code PermissionInterceptor} logic:
 * <ul>
 *   <li>If no annotation is present the request proceeds.</li>
 *   <li>If the user is not authenticated a 401 is returned.</li>
 *   <li>The user must hold at least one of the roles declared in {@link PermissionRequired#role()}
 *       (via the Spring Security {@link Authentication} populated by the ALA JWT filter), or
 *       admin role is sufficient for any protected endpoint.</li>
 * </ul>
 */
@Slf4j
@Component
public class PermissionChecker implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Public endpoints — skip all checks
        if (handlerMethod.hasMethodAnnotation(SkipPermissionCheck.class)) {
            return true;
        }

        // Resolve effective annotation (method wins over class)
        PermissionRequired annotation = handlerMethod.getMethodAnnotation(PermissionRequired.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(PermissionRequired.class);
        }

        if (annotation == null) {
            // No permission requirement declared — allow
            return true;
        }

        // Authentication must be present and fully authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            log.debug("Unauthenticated access attempt to {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return false;
        }

        // Admins may access any protected endpoint
        if (hasRole(auth, ROLE_ADMIN)) {
            return true;
        }

        // Check declared role(s)
        String requiredRole = annotation.role();
        if (requiredRole != null && !requiredRole.isEmpty() && hasRole(auth, requiredRole)) {
            return true;
        }

        // Access denied
        log.warn("Access denied for user '{}' to {} {} — required role: {}",
                auth.getName(), request.getMethod(), request.getRequestURI(), requiredRole);
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Access denied. You do not have the required permissions.");
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the {@link Authentication} holds the given role string
     * (case-insensitive comparison; also matches with/without the {@code ROLE_} prefix).
     */
    private boolean hasRole(Authentication auth, String role) {
        if (role == null || role.isEmpty()) return false;
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null) return false;

        String normalised = role.toUpperCase();
        String withPrefix = normalised.startsWith("ROLE_") ? normalised : "ROLE_" + normalised;
        String withoutPrefix = normalised.startsWith("ROLE_") ? normalised.substring(5) : normalised;

        for (GrantedAuthority authority : authorities) {
            String granted = authority.getAuthority().toUpperCase();
            if (granted.equals(withPrefix) || granted.equals(withoutPrefix)) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} when the principal is the anonymous user added by Spring Security. */
    private boolean isAnonymous(Authentication auth) {
        return "anonymousUser".equals(auth.getPrincipal())
                || auth.getAuthorities().stream()
                       .anyMatch(a -> "ROLE_ANONYMOUS".equals(a.getAuthority()));
    }
}
