package au.org.ala.collectory.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark controller methods that require specific role-based permissions.
 * Used by PermissionChecker interceptor to enforce access control.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionRequired {

    /** The role required (e.g., "ROLE_ADMIN", "ROLE_EDITOR") */
    String role() default "ROLE_EDITOR";

    /** Whether entity-level authorization should also be checked */
    boolean entityAuth() default false;
}
