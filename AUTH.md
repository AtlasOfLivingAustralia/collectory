# Collectory Authentication

This document explains how authentication works in the current system and how it differs from the
pre-migration OIDC approach.

---

## Current architecture — server-side session auth

The browser delegates the entire OIDC flow to `collectory-api`. This matches the pattern used by
`doi-ui` / `search-service` in the `atlas-index` monorepo and is provided by `@ala/common-ui`.

### Flow diagram

```
App loads
    │
    ▼
App.tsx calls checkLoginState()  ──GET /session──▶  collectory-api
                                                          │
                                              reads HttpSession + session_secret cookie
                                              decrypts stored tokens (AES-256)
                                              returns UserInfoDto JSON
                                                    (or { authenticated: false })
                                                          │
    ◀─────────────────────── UserInfo JSON ───────────────┘
    │
    ├─ authenticated=false → unauthenticated state, isLoading resolves
    └─ authenticated=true  → roles, accessToken, expiresAt in React context


User clicks Login
    │
    ▼
handleLogin() redirects browser to:  GET /login?path=<returnUrl>
    │
    ▼
collectory-api generates PKCE code_verifier, stores in HttpSession,
builds Cognito/CAS auth URL, redirects browser there
    │
    ▼
Cognito authenticates user, redirects to:  GET /callback?code=…&state=…
    │
    ▼
collectory-api exchanges code for tokens (server-to-server HTTP call),
encrypts tokens with AES-256, stores in HttpSession,
sets HttpOnly session_secret cookie + ALA-SESSION status cookie,
redirects browser back to <returnUrl>
    │
    ▼
Browser now holds the HttpOnly cookie.
App.tsx calls checkLoginState() again on next visibility / tab focus.
GET /session returns full UserInfo including accessToken.
    │
    ▼
App.tsx calls setAccessToken(userInfo.accessToken).
Axios interceptor attaches it as  Authorization: Bearer <token>
on all /ws/* API requests.
```

### Token refresh

`checkLoginState` (in `common-ui/src/util/auth.tsx`) reads `expiresAt` from the `/session`
response and schedules a `setTimeout` to re-call `/session` before the token expires, with a
random 60–120 second stagger to avoid thundering herd. It also re-runs on
`document.visibilitychange`, so returning to the tab after a long absence triggers an immediate
refresh.

---

## Key source files

### Frontend (`collectory-ui/`)

| File | Role |
|------|------|
| `src/App.tsx` | Calls `checkLoginState` on mount and visibility change; provides `UserContext`; calls `setAccessToken` when `userInfo` changes |
| `src/auth/useAuth.ts` | Thin wrapper over `useUser()` from `@ala/common-ui`; exposes `isAuthenticated`, `isAdmin`, `isEditor`, `login`, `logout`, etc. |
| `src/auth/ProtectedRoute.tsx` | Guards routes; redirects to login if unauthenticated; shows error if role insufficient |
| `src/api/client.ts` | Module-level `_accessToken` variable; `setAccessToken()` export; Axios interceptor attaches Bearer token |

### Common UI (`common-ui/src/`)

| File | Role |
|------|------|
| `util/auth.tsx` | `checkLoginState` — fetches `/session` with `credentials: include`; schedules refresh |
| `util/UserContext.tsx` | `UserInfo` type, `UserContext`, `useUser()` hook |
| `util/auth.tsx` | `handleLogin` — redirects to `/login?path=…`; `handleLogout` — redirects to `/logout?path=…` |

### Backend (`collectory-api/`)

| File | Role |
|------|------|
| `controller/AuthController.java` | `GET /session`, `/login`, `/callback`, `/logout` |
| `service/SessionAuthService.java` | OIDC PKCE flow, token exchange/refresh, AES-256 encryption, cookie management |
| `util/CryptoUtil.java` | AES-256 encrypt/decrypt for tokens stored in `HttpSession` |
| `dto/UserInfoDto.java` | JSON response shape for `GET /session`; matches `UserInfo` type in `@ala/common-ui` |
| `config/SecurityConfig.java` | Two filter chains: `@Order(1)` session-based for auth endpoints, `@Order(2)` stateless JWT for `/ws/**` |

---

## Backend endpoints

### `GET /session`

Called by `checkLoginState()` on every app load and tab focus. Returns `UserInfoDto` JSON.

- Requires an `Origin` header matching `security.cors.origins` (validated in `AuthController`)
- Does **not** create a new session — reads `request.getSession(false)`
- If no session or no `session_secret` cookie: returns `{ authenticated: false }`
- Otherwise: decrypts tokens from `HttpSession`, refreshes if near expiry, returns full `UserInfo`

Example response (authenticated):
```json
{
  "authenticated": true,
  "userId": "hamza.javed@csiro.au",
  "email": "Hamza.Javed@csiro.au",
  "firstName": "Hamza",
  "lastName": "Javed",
  "roles": ["ROLE_USER", "ROLE_ADMIN", "ROLE_EDITOR"],
  "accessToken": "<JWT>",
  "expiresAt": 1743600000000
}
```

> Note: `@JsonInclude(NON_EMPTY)` on `UserInfoDto` means empty/null fields are omitted.
> `roles` is absent (not `[]`) when unauthenticated — `@ala/common-ui` handles this gracefully.

### `GET /login?path=<returnUrl>`

Initiates OIDC PKCE login:
1. Validates `path` is an allowed redirect (relative path or same-origin URL)
2. Generates a `code_verifier` (random 32 bytes, base64url-encoded), stores in `HttpSession`
3. Computes `code_challenge = base64url(SHA-256(code_verifier))`
4. Builds the Cognito/CAS auth URL with `response_type=code`, `code_challenge`, `state=base64(path)`
5. Returns HTTP 302 to the auth URL

### `GET /callback?code=…&state=…`

OIDC provider redirects here after authentication:
1. If already logged in (valid `session_secret`), redirects straight to `returnPath`
2. Generates a new `session_secret` (random bytes) and sets it as an `HttpOnly` cookie
3. Retrieves stored `code_verifier` from `HttpSession`
4. POSTs to token endpoint (`grant_type=authorization_code`) — server-to-server
5. Extracts `id_token`, `access_token`, `refresh_token`
6. Decodes `id_token` to get `userId`, `email`, `firstName`, `lastName`, `roles`
   - Roles come from the `ala:role` claim (comma-separated string e.g. `"ROLE_ADMIN,ROLE_USER"`)
   - Falls back to decoding the access token if the ID token claim is empty
7. Encrypts all tokens with AES-256 using `session_secret` as the key material
8. Stores encrypted tokens in `HttpSession`
9. Sets `ALA-SESSION` status cookie (not HttpOnly — visible to JS for header UI)
10. Redirects browser to `returnPath`

### `GET /logout?path=<returnUrl>`

1. Retrieves and decrypts tokens from `HttpSession`
2. Calls the OIDC revoke endpoint to revoke the refresh token
3. Clears `HttpSession`
4. Removes `session_secret` and `ALA-SESSION` cookies
5. Redirects to the OIDC logout URL (or `returnUrl` directly for `DEFAULT` logout action)

---

## Security config — two filter chains

`SecurityConfig.java` defines two separate Spring Security filter chains:

```
Chain 1  @Order(1) — matches /session, /login, /callback, /logout
    SessionCreationPolicy.IF_REQUIRED   (allows HttpSession)
    CSRF disabled
    All requests permitted (auth is handled by SessionAuthService logic)

Chain 2  @Order(2) — matches everything else
    SessionCreationPolicy.STATELESS     (no HttpSession)
    AlaWebServiceAuthFilter validates Bearer JWT on /ws/** write requests
    GET /ws/** — public
    POST/PUT/DELETE/PATCH /ws/** — authenticated
    /ws/admin/** — ROLE_ADMIN
```

---

## Before the migration — in-browser OIDC/PKCE

Before the `UI_MIGRATION.md` phases were completed, `collectory-ui` used `react-oidc-context` +
`oidc-client-ts`. The **browser itself** ran the entire OIDC flow:

```
1. User clicks Login
2. Browser generates PKCE code_verifier/code_challenge
3. Browser redirects to Cognito/CAS with the challenge
4. Cognito redirects back to /callback  (a React route — OidcCallback.tsx)
5. Browser exchanges the code for tokens directly with Cognito
6. access_token stored in sessionStorage (browser memory)
7. Every API call reads access_token from sessionStorage → Authorization: Bearer <token>
```

Key files that existed before and are now removed:
- `src/auth/AuthProvider.tsx` — wrapped the app in `react-oidc-context`'s `AuthProvider`
- `src/pages/OidcCallback.tsx` — React page that handled the `/callback` OIDC redirect
- The `/callback` route in `routes.tsx`
- `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_SCOPE`, `VITE_REDIRECT_URI` env vars

---

## Side-by-side comparison

| Aspect | Before (OIDC in browser) | After (server session) |
|--------|--------------------------|------------------------|
| PKCE code exchange | Browser → Cognito directly | Server → Cognito (server-to-server) |
| Token storage | `sessionStorage` (browser JS) | `HttpSession` + AES-256 encryption (server) |
| Auth cookie | None | `session_secret` (HttpOnly) + `ALA-SESSION` (visible) |
| `/callback` handler | React page (`OidcCallback.tsx`) | Spring Boot `AuthController.callback()` |
| Token refresh | `oidc-client-ts` automatic | `setTimeout` in `checkLoginState` + `/session` call |
| Bearer token source | `sessionStorage.getItem('oidc_access_token')` | `UserContext.userInfo.accessToken` (from `/session`) |
| Backend session policy | Fully stateless | Session-based for auth endpoints, stateless for `/ws/**` |
| Backend filter chains | One stateless chain | Two chains: `@Order(1)` session, `@Order(2)` JWT |
| Frontend auth library | `react-oidc-context` + `oidc-client-ts` | `@ala/common-ui` `checkLoginState` / `UserContext` |
| Refresh token | Managed by browser library | Stored server-side, used on `/session` calls |

### Why the server-side approach is preferred

- **Tokens never exposed to browser JS** — the `session_secret` cookie is `HttpOnly`; the
  refresh token is never sent to the browser at all. The `accessToken` in `UserInfo` is only
  passed to the frontend for attaching to API calls.
- **Consistent across all ALA apps** — `doi-ui`, `search-service`, and now `collectory` all use
  the same `@ala/common-ui` pattern, meaning auth behaviour and debugging is uniform.
- **Simpler frontend** — no OIDC library, no callback page, no `sessionStorage` management.
  The frontend just calls `/session` on load and the backend handles everything else.

---

## Configuration

### `application.properties` (backend)

```properties
# OIDC provider
security.oidc.discovery-uri=https://auth.ala.org.au/cas/oidc/.well-known
security.oidc.clientId=collectory
security.oidc.secret=<client-secret>              # overridden by external config
security.oidc.scope=openid profile email ala/attrs ala/roles
security.oidc.userIdClaim=cognito:username
security.oidc.roleClaims=ala:role
security.oidc.logoutAction=DEFAULT

# Session cookies
security.cookie.name=ALA-SESSION
security.cookie.domain=                           # empty default; overridden in prod
security.cookie.debug=false
security.cookie.rotate=false
security.login.maxAgeDays=30

# Allowed CORS origins for /session
security.cors.origins=http://localhost:3000,https://collections.ala.org.au
```

`application-dev.properties` overrides `security.cookie.domain=` (empty) to prevent a Tomcat
crash caused by the external config file setting `.ala.org.au` which is invalid on localhost.

### `.env.development` (frontend)

```properties
VITE_APP_API_URL=http://localhost:8080    # backend base URL (auth + /ws/*)
VITE_APP_BASE_URL=http://localhost:3000  # frontend base URL (used in logout redirect)
```
