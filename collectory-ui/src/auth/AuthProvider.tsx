import { AuthProvider as OidcProvider } from 'react-oidc-context';
import type { WebStorageStateStore } from 'oidc-client-ts';
import type { ReactNode } from 'react';

// OIDC configuration - values should come from environment or config endpoint
const oidcConfig = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY || 'https://auth-test.ala.org.au/cas/oidc',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID || 'collectory-spa',
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  scope: import.meta.env.VITE_OIDC_SCOPE || 'openid profile email ala/attrs ala/roles',
  response_type: 'code',
  // Store tokens in sessionStorage for security
  userStore: undefined as unknown as WebStorageStateStore,
  onSigninCallback: () => {
    // Remove OIDC query params from URL after sign-in
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

interface Props {
  children: ReactNode;
}

export function AuthProvider({ children }: Props) {
  return <OidcProvider {...oidcConfig}>{children}</OidcProvider>;
}
