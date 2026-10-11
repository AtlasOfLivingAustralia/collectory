package au.org.ala

import java.lang.annotation.*

/**
 * Annotation to validate user login info (roles) and JWT/M2M tokens (scopes) in the request.
 *
 * For JWT authentication:
 * - If scopes are empty, token authentication is denied.
 * - If scopes contain "*", any valid token is accepted.
 * - Otherwise, the token must contain one of the specified scopes.
 */
@Target([ElementType.TYPE, ElementType.METHOD, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface PermissionRequired {
    /** roles in config support multiple values separated by commas or semicolons.
     *
     */
    String[] roles() default []
    /**
     * Only taken into account for JWT authentications.  Combined with security.jwt.scopes
     * scopes in config support multiple values separated by commas or semicolons.
     *
     * If scopes are empty, it DENIES the request.
     * If scopes contain "*", it allows the request if the token is valid.
     * otherwise, it checks if the scopes.
     * @return
     */
    String[] scopes() default []
}