import { useAuth as useOidcAuth } from 'react-oidc-context';
import { useEffect } from 'react';

/**
 * Extract roles from the OIDC user profile. Different providers put roles in
 * different claims — check common locations.
 */
function extractRoles(profile: Record<string, unknown> | undefined): string[] {
  if (!profile) return [];

  // Try common claim names for roles/groups.
  // Prefer ala:role first — it contains ROLE_* prefixed values.
  // cognito:groups returns lowercase names without ROLE_ prefix.
  const candidates = [
    'ala:role',            // ALA-specific (singular) — comma-separated ROLE_* values
    'ala:roles',           // ALA-specific (plural)
    'role',                // ALA CAS / generic OIDC
    'roles',               // Common alternative
    'cognito:groups',      // AWS Cognito (lowercase, no ROLE_ prefix)
    'realm_access',        // Keycloak (nested)
    'groups',              // Generic
    'authorities',         // Spring Security convention
  ];

  for (const claim of candidates) {
    const value = profile[claim];
    if (Array.isArray(value) && value.length > 0) {
      return value as string[];
    }
    if (typeof value === 'string' && value.length > 0) {
      // Could be a comma-separated string
      return value.split(',').map((r) => r.trim());
    }
  }

  // Keycloak nests roles under realm_access.roles
  const realmAccess = profile['realm_access'] as { roles?: string[] } | undefined;
  if (realmAccess?.roles && Array.isArray(realmAccess.roles)) {
    return realmAccess.roles;
  }

  return [];
}

export function useAuth() {
  const auth = useOidcAuth();

  // Store access token for API client interceptor
  useEffect(() => {
    if (auth.user?.access_token) {
      sessionStorage.setItem('oidc_access_token', auth.user.access_token);
    } else {
      sessionStorage.removeItem('oidc_access_token');
    }
  }, [auth.user?.access_token]);

  const roles = extractRoles(auth.user?.profile as Record<string, unknown> | undefined);

  // Log profile claims once on login so role issues can be diagnosed
  useEffect(() => {
    if (auth.isAuthenticated && auth.user?.profile) {
      console.debug('[useAuth] OIDC profile claims:', auth.user.profile);
      console.debug('[useAuth] Extracted roles:', roles);
    }
  }, [auth.isAuthenticated]); // eslint-disable-line react-hooks/exhaustive-deps

  return {
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    user: auth.user,
    login: () => auth.signinRedirect(),
    logout: () => auth.signoutRedirect(),
    roles,
    isAdmin: roles.includes('ROLE_ADMIN'),
    isEditor: roles.includes('ROLE_EDITOR') || roles.includes('ROLE_ADMIN'),
    email: auth.user?.profile?.email as string | undefined,
    userId: auth.user?.profile?.preferred_username as string | undefined,
  };
}
