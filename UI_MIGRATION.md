# Collectory UI Migration Plan

Align `collectory-ui` with the patterns used by `doi-ui` and `@ala/common-ui` from the `atlas-index`
monorepo. The goals are:

1. Use Yarn instead of npm
2. Replace the in-browser OIDC auth (`react-oidc-context`) with the `@ala/common-ui` cookie/session
   auth pattern
3. Replace the custom `AlaLayout` header/footer injection with `@ala/common-ui` components
4. Add the auth endpoints required by `@ala/common-ui` to `collectory-api` (the role that
   `search-service` plays in `atlas-index`)

---

## Architecture overview

### Current state

```
Browser  ──OIDC/PKCE──▶  Cognito/CAS  (access_token stored in sessionStorage)
Browser  ──Bearer JWT──▶  collectory-api  (AlaWebServiceAuthFilter validates JWT)
```

### Target state (matches doi-ui / common-ui pattern)

```
Browser  ──GET /login──▶  collectory-api  ──redirects──▶  Cognito/CAS
Browser  ◀──Set-Cookie──  collectory-api  ◀──code─────────  Cognito/CAS  (PKCE callback)
Browser  ──GET /session─▶  collectory-api  (returns UserInfo JSON, refreshes token)
Browser  ──Bearer JWT───▶  collectory-api /ws/**  (unchanged, AlaWebServiceAuthFilter)
```

Session tokens are encrypted server-side (AES-256) inside an HTTP session. The browser only holds
a `session_secret` HttpOnly cookie and a `ALA-SESSION` status cookie (matches search-service
behaviour exactly).

### What changes

| Area | Current | Target |
|------|---------|--------|
| Package manager | npm | yarn |
| Auth (frontend) | `react-oidc-context` + `oidc-client-ts` | `@ala/common-ui` `checkLoginState` / `UserContext` |
| Auth (backend) | JWT-only, stateless | JWT for `/ws/**` + new session-based `/session`, `/login`, `/callback`, `/logout` |
| Header/Footer/Banner | Custom DOM injection in `AlaLayout.tsx` | `@ala/common-ui` `<Header>`, `<Footer>`, `<Banner>` |
| CSS/JS injection | Custom | `injectCommonInfo()` from `@ala/common-ui` |
| User context | OIDC `user` object, custom `useAuth.ts` | `UserInfo`, `useUser()` from `@ala/common-ui` |
| API Bearer token | From `oidc.user.access_token` via sessionStorage | From `userInfo.accessToken` via `UserContext` |
| Env vars | Mixed `VITE_OIDC_*` / `VITE_ALA_*` | Standardised `VITE_APP_API_URL`, `VITE_COMMON_*` |

### What does NOT change

These are appropriate for collectory-ui's scale and are not part of the `@ala/common-ui` pattern:

- Axios (better than raw `fetch` for interceptors and cancellation)
- TanStack Query (critical for this app's data complexity)
- React Hook Form + Zod (forms are a major part of collectory)
- i18next (doi-ui uses react-intl but there's no reason to switch)
- Vitest + Playwright (superior to Jest for Vite projects)

---

## Phase 1 — Switch to Yarn ✅ DONE

**Scope:** `collectory-ui/` only. Zero functional risk.

### Steps

1. ✅ Delete `node_modules/` and `package-lock.json` from `collectory-ui/`
2. ✅ Add `collectory-ui/.yarnrc.yml`:
   ```yaml
   nodeLinker: node-modules
   ```
3. ✅ Add `collectory-ui/.nvmrc` (match `doi-ui`):
   ```
   v20
   ```
4. ✅ Run `yarn install` — produces `yarn.lock`
   - Yarn activated via `corepack` (bundled with Node 22). Version installed: **Yarn 4.13.0**.
5. ✅ Added `collectory-ui/package-lock.json` to root `.gitignore`
   - No CI scripts exist yet to update.
6. ✅ Verified `yarn build` succeeds cleanly.

---

## Phase 2 — Copy `common-ui` into this repo and add as a dependency ✅ DONE

**Scope:** `collectory-ui/` only. Zero functional risk.

The `common-ui` directory is already present at the repo root (`collectory/common-ui/`).

### Steps

1. ✅ Run `yarn install` inside `common-ui/` to install its own dependencies:
   ```bash
   cd common-ui && .yarn/releases/yarn-1.22.22.cjs install --production
   ```
   > Note: `common-ui` ships with Yarn 1.22.22 via `.yarnrc`. Only production deps are needed
   > since `collectory-ui`'s Vite build consumes `common-ui`'s source directly. Installing
   > devDependencies is not required and times out (200MB+).

3. ✅ Added to `collectory-ui/package.json` `dependencies`:
   ```json
   "@ala/common-ui": "file:../common-ui"
   ```

4. ✅ Updated `collectory-ui/vite.config.ts`:
   ```ts
   export default defineConfig({
     // ... existing config ...
     optimizeDeps: {
       exclude: ['@ala/common-ui'],
     },
     server: {
       port: 3000,
       fs: {
         allow: ['..'],   // allow access to ../common-ui
       },
       proxy: {
         // existing proxies unchanged
         '/ws': 'http://localhost:8080',
         // ...
       },
     },
   });
   ```

5. ✅ Run `yarn install` in `collectory-ui/` to resolve the new dependency.
   - 28 new packages added (common-ui transitive deps: `react-bootstrap-typeahead`, `@fortawesome/*`, etc.)

6. ✅ Verified `yarn build` succeeds. `node_modules/@ala/common-ui/src/` is correctly linked.

---

## Phase 3 — Add auth endpoints to `collectory-api` ✅ DONE

**Scope:** `collectory-api/` (Spring Boot). This is the backend work that replaces the role
`search-service` plays in `atlas-index`.

The `@ala/common-ui` auth utilities call three endpoints (documented in `AUTH.md`):
- `GET /session` — returns `UserInfo` JSON (checks cookie → decrypts token → refreshes if needed)
- `GET /login?path=<returnPath>` — initiates OIDC/PKCE login, redirects to provider
- `GET /logout?path=<returnPath>` — revokes token, clears cookies, redirects to provider logout

The `/callback` endpoint is also required to complete the OIDC code exchange.

### 3.1 Dependencies

Add to `collectory-api/pom.xml`. The `ala-ws-security` library (already present) provides
`TokenService` which handles OIDC token exchange and refresh. Add Bouncy Castle for AES-256
token encryption (matches `search-service`'s `CryptoUtil`):

```xml
<!-- Already present – confirms version is sufficient (≥ 4.x for TokenService) -->
<dependency>
    <groupId>au.org.ala.ws</groupId>
    <artifactId>ala-ws-security</artifactId>
    <version>${ala.ws.security.version}</version>
</dependency>

<!-- AES-256 encryption for session tokens -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78.1</version>
</dependency>
```

### 3.2 New configuration properties

Add to `collectory-api/src/main/resources/application.properties` (and environment-specific
overrides):

```properties
# ── OIDC auth flow ────────────────────────────────────────────────────────────
security.oidc.discovery-uri=https://auth.ala.org.au/cas/oidc/.well-known
security.oidc.clientId=collectory
security.oidc.secret=<client-secret>
security.oidc.scope=openid profile email ala/attrs ala/roles
security.oidc.userIdClaim=cognito:username
security.oidc.roleClaims=ala:role
security.oidc.logoutAction=DEFAULT        # DEFAULT for CAS, COGNITO for Cognito

# ── Session cookies ───────────────────────────────────────────────────────────
security.cookie.name=ALA-SESSION          # visible status cookie (not HttpOnly)
security.cookie.domain=ala.org.au         # blank for localhost dev
security.cookie.debug=false
security.cookie.rotate=false
security.login.maxAgeDays=30

# ── CORS origins allowed for /session endpoint ────────────────────────────────
# Must include all deployed collectory-ui origins
security.cors.origins=http://localhost:3000,https://collections.ala.org.au,https://collections.test.ala.org.au
```

Map these into `AppProperties.java` under a `security` nested section.

### 3.3 New source files

Mirror the structure from `search-service`. Create these files in
`collectory-api/src/main/java/au/org/ala/collectory/`:

#### `dto/UserInfoDto.java`

The JSON response returned by `GET /session`. Matches the `UserInfo` type in `@ala/common-ui`:

```java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserInfoDto {
    private boolean authenticated;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String[] roles;
    private String accessToken;
    private Long expiresAt;
    private String error;          // populated on auth failure
}
```

#### `util/CryptoUtil.java`

AES-256 encryption/decryption for storing OIDC tokens in the HTTP session. Copy directly from
`search-service/src/main/java/au/org/ala/search/util/CryptoUtil.java`. No changes required —
it is a stateless utility with no dependencies on search-service domain classes.

#### `service/SessionAuthService.java`

Core OIDC PKCE flow and token lifecycle management. Mirror
`search-service/src/main/java/au/org/ala/search/service/SessionAuthService.java` with the
following adaptations:

- Replace search-service's `AppConfig` / `SecurityProperties` references with
  `collectory-api`'s `AppProperties` equivalents
- Keep `TokenService` usage unchanged (same `ala-ws-security` library)
- Keep `CryptoUtil` usage unchanged

Key methods required:
- `buildLoginRedirect(String returnPath, HttpSession session)` → OIDC auth URL with PKCE
- `handleCallback(String code, String state, HttpSession session, HttpServletResponse response)` → exchange code, encrypt tokens, set cookies
- `getSessionUserInfo(HttpServletRequest request, HttpServletResponse response)` → returns `UserInfoDto`
- `logout(String returnPath, HttpSession session, HttpServletResponse response)` → revoke token, clear cookies

#### `controller/AuthController.java`

```java
@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SessionAuthService sessionAuthService;

    /** Called by @ala/common-ui checkLoginState() */
    @GetMapping("/session")
    public ResponseEntity<UserInfoDto> session(HttpServletRequest request,
                                               HttpServletResponse response) {
        UserInfoDto info = sessionAuthService.getSessionUserInfo(request, response);
        return ResponseEntity.ok(info);
    }

    /** Initiates OIDC login — browser redirects here when user clicks Login */
    @GetMapping("/login")
    public void login(@RequestParam(defaultValue = "/") String path,
                      HttpSession session,
                      HttpServletResponse response) throws IOException {
        String redirectUrl = sessionAuthService.buildLoginRedirect(path, session);
        response.sendRedirect(redirectUrl);
    }

    /** OIDC provider callback — exchanges code for tokens */
    @GetMapping("/callback")
    public void callback(@RequestParam String code,
                         @RequestParam String state,
                         HttpSession session,
                         HttpServletResponse response) throws IOException {
        sessionAuthService.handleCallback(code, state, session, response);
    }

    /** Called by @ala/common-ui handleLogout() */
    @GetMapping("/logout")
    public void logout(@RequestParam(defaultValue = "/") String path,
                       HttpSession session,
                       HttpServletResponse response) throws IOException {
        sessionAuthService.logout(path, session, response);
    }
}
```

### 3.4 Update `SecurityConfig.java`

The current config is fully stateless (`SessionCreationPolicy.STATELESS`). We need two separate
filter chains: one for the auth endpoints (session-based) and one for the API (stateless JWT).

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private AlaWebServiceAuthFilter alaWebServiceAuthFilter;

    /** Chain 1 — Session-based auth endpoints (login/logout/session/callback) */
    @Bean
    @Order(1)
    public SecurityFilterChain authFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/login", "/logout", "/session", "/callback")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .cors(cors -> cors.configurationSource(new CorsConfig().corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Chain 2 — Stateless REST API (unchanged from current) */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(new CorsConfig().corsConfigurationSource()))
            .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny));

        if (alaWebServiceAuthFilter != null) {
            http.addFilterBefore(alaWebServiceAuthFilter, BasicAuthenticationFilter.class);
        }

        http.authorizeHttpRequests(auth -> auth
            // ... all existing rules unchanged ...
        );
        return http.build();
    }
}
```

### 3.5 Verify `/session` CORS

`@ala/common-ui`'s `checkLoginState()` calls `/session` with an `Origin` header from the UI
origin (e.g. `http://localhost:3000`). The `CorsConfig.java` in `collectory-api` must include
the UI origin. Update `security.cors.origins` in `application.properties` (see §3.2 above) and
ensure `CorsConfig.java` reads from `AppProperties`.

---

## Phase 4 — Replace frontend auth with `@ala/common-ui` ✅ DONE

**Scope:** `collectory-ui/src/auth/` and `collectory-ui/src/App.tsx`.

### 4.1 Remove

- `react-oidc-context` and `oidc-client-ts` from `package.json`
- `src/auth/AuthProvider.tsx` (OIDC `AuthProvider` wrapper)
- `src/pages/OidcCallback.tsx` (OIDC callback page)
- The `/callback` route in `routes.tsx`
- OIDC env vars: `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_SCOPE`,
  `VITE_REDIRECT_URI`

### 4.2 Update `App.tsx`

Replace OIDC provider setup with `UserContext` and `checkLoginState` from `@ala/common-ui`:

```tsx
import { checkLoginState, handleLogin, handleLogout, UserContext, UserInfo } from '@ala/common-ui';
import { useEffect, useRef, useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import { router } from './routes';

export default function App() {
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const refreshTimer = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    checkLoginState(setUserInfo, refreshTimer, import.meta.env.VITE_APP_API_URL);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        checkLoginState(setUserInfo, refreshTimer, import.meta.env.VITE_APP_API_URL);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  return (
    <UserContext.Provider value={{ userInfo, setUserInfo }}>
      <RouterProvider router={router} />
    </UserContext.Provider>
  );
}
```

### 4.3 Replace `src/auth/useAuth.ts`

Thin wrapper over `useUser()` from `@ala/common-ui` that keeps the same interface as the
existing hook so that no page components need to change:

```ts
import { handleLogin, handleLogout, useUser } from '@ala/common-ui';

export function useAuth() {
  const { userInfo } = useUser();

  return {
    isAuthenticated: userInfo?.authenticated ?? false,
    isLoading: userInfo === null,          // null = still checking
    user: userInfo,
    roles: userInfo?.roles ?? [],
    isAdmin:  userInfo?.roles?.includes('ROLE_ADMIN') ?? false,
    isEditor: userInfo?.roles?.some(r => r === 'ROLE_EDITOR' || r === 'ROLE_ADMIN') ?? false,
    accessToken: userInfo?.accessToken,
    login:  () => handleLogin(import.meta.env.VITE_APP_API_URL),
    logout: () => handleLogout(import.meta.env.VITE_APP_API_URL, import.meta.env.VITE_APP_BASE_URL),
  };
}
```

### 4.4 Update `src/auth/ProtectedRoute.tsx`

The component logic barely changes — `isAuthenticated`, `isLoading`, and `roles` are still
available from `useAuth()`. The only difference is the semantics of `isLoading`: with OIDC it
reflected an in-progress token exchange; now it reflects `userInfo === null` (the initial state
before the first `/session` response arrives).

Review and test the existing `ProtectedRoute` component — no structural rewrite should be needed.

### 4.5 Update `src/api/client.ts`

The Axios interceptor currently reads the Bearer token from sessionStorage (where OIDC stored
it). Replace that with reading from `UserContext`:

```ts
// Get the store outside of React so the Axios interceptor can access it.
// Use a module-level setter that App.tsx calls when userInfo changes.

let _accessToken: string | undefined;

export function setAccessToken(token: string | undefined) {
  _accessToken = token;
}

// In App.tsx, after userInfo changes:
// useEffect(() => { setAccessToken(userInfo?.accessToken); }, [userInfo]);

apiClient.interceptors.request.use(config => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});
```

---

## Phase 5 — Replace `AlaLayout` with `@ala/common-ui` components ✅ DONE

**Scope:** `collectory-ui/src/layouts/AlaLayout.tsx`.

`AlaLayout.tsx` was completely rewritten — the 390-line hand-rolled implementation (custom `AlaHeader`,
`AlaFooter`, `Banner`, CSS/JS injection) was replaced with the declarative `@ala/common-ui` components:

```tsx
import { Banner, Footer, Header, injectCommonInfo } from '@ala/common-ui';
import buildInfo from '../../package.json';
import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export function AlaLayout() {
  const { isAuthenticated, login, logout } = useAuth();
  const [cssLoaded, setCssLoaded] = useState(false);

  useEffect(() => {
    injectCommonInfo(
      buildInfo,
      import.meta.env.VITE_ENV,
      import.meta.env.VITE_COMMON_JS,
      import.meta.env.VITE_COMMON_CSS,
      setCssLoaded
    );
  }, []);

  if (!cssLoaded) return null;  // prevents flash of unstyled content

  return (
    <>
      <Header
        isLoggedIn={isAuthenticated}
        loginFn={login}
        logoutFn={logout}
        headerUrl={import.meta.env.VITE_COMMON_HEADER_HTML}
      />
      <Banner
        bannerUrl={import.meta.env.VITE_BANNER_MESSAGES_URL}
        scope={import.meta.env.VITE_BANNER_SCOPE}
      />
      <div id="breadcrumb-slot" />
      <main className="container-fluid">
        <Outlet />
      </main>
      <Footer
        isLoggedIn={isAuthenticated}
        loginFn={login}
        logoutFn={logout}
        footerUrl={import.meta.env.VITE_COMMON_FOOTER_HTML}
      />
    </>
  );
}
```

The `#breadcrumb-slot` portal div is preserved — `src/components/public/Breadcrumb.tsx` portals
into it and no changes to that component were needed.

The `useConfig` fallback for header/footer URL (via `config.headerAndFooterBaseUrl`) was removed —
env vars are now always set.

### Type errors fixed in `common-ui`

`collectory-ui`'s stricter `tsconfig.json` (`noUncheckedIndexedAccess`, `strict`) surfaced
pre-existing type errors in `common-ui`'s source that its own lenient config did not catch.
Fixed in `common-ui/src/`:

- `util/utils.tsx` — `elements[i]` non-null assertion in `setClickEventByClassName`
- `components/conservationStatusLabel.tsx` — non-null assertion on `conservationStatuses[status]`
- `components/dualRangeSlider.tsx` — `yearRange` typed as `[number, number]` tuple (was `number[]`);
  `minValueRef`/`maxValueRef` typed as `useRef<number | null>(null)` (was `useRef<number>(null)`)
- `components/header.tsx` — non-null assertions on `getElementsByClassName` index accesses and
  `autocompleteResult[selectedIndex]`

Also added `@types/node` to `collectory-ui` devDependencies to resolve `NodeJS.Timeout` used
in `common-ui/src/util/auth.tsx` and `components/header.tsx`.

---

## Phase 6 — Align environment variables ✅ DONE

Replace all env files under `collectory-ui/config/` to match the `doi-ui`/`common-ui` naming
convention.

### Development (`config/.env.development`)

```properties
# Auth — points to collectory-api running locally
VITE_APP_API_URL=http://localhost:8080
VITE_APP_BASE_URL=http://localhost:3000

# Collectory API (same as VITE_APP_API_URL for local dev)
VITE_API_BASE_URL=http://localhost:8080

# Common header/footer/CSS (requires a local static asset server or dev ALA server)
VITE_COMMON_HEADER_HTML=http://localhost:8082/static/common/header.html
VITE_COMMON_FOOTER_HTML=http://localhost:8082/static/common/footer.html
VITE_COMMON_CSS=http://localhost:8082/static/common/common.css
VITE_COMMON_JS=http://localhost:8082/static/common/common.js

# Banner
VITE_BANNER_SCOPE=collectory
VITE_BANNER_MESSAGES_URL=http://localhost:8082/static/common/status.json

# Environment tag (shown in header meta info)
VITE_ENV=local
```

### Staging / Production / Testing

Replace `VITE_OIDC_*` vars with:

```properties
VITE_APP_API_URL=https://collections[-test|-staging].ala.org.au
VITE_APP_BASE_URL=https://collections[-test|-staging].ala.org.au
VITE_COMMON_HEADER_HTML=https://static[-test].ala.org.au/common/header.html
# etc.
```

### `vite-env.d.ts` ✅ DONE

✅ Updated `ImportMetaEnv` interface to declare all new vars (`VITE_APP_API_URL`,
`VITE_APP_BASE_URL`, `VITE_ENV`) and removed the old `VITE_OIDC_*` declarations.

> Note: `VITE_API_BASE_URL` is retained alongside `VITE_APP_API_URL` for now — `client.ts` has
> been updated to use `VITE_APP_API_URL`, but `VITE_API_BASE_URL` remains in env files as a
> transitional reference until Phase 4 removes the old auth layer entirely.

---

## Phase 7 — Config and tooling cleanup ✅ DONE

Small consistency items, all zero-risk:

1. ✅ Added `collectory-ui/.prettierrc.json` matching `doi-ui`:
   ```json
   {
     "semi": true,
     "singleQuote": true,
     "trailingComma": "es5",
     "printWidth": 100
   }
   ```
2. ✅ Added `"check-types": "tsc --noEmit"` script to `package.json` — verified it passes cleanly.
3. ✅ `package-lock.json` added to root `.gitignore` (done in Phase 1).
4. ✅ `yarn.lock` is tracked in version control (not in `.gitignore`).

---

## Execution order and risk summary

| Phase | Scope | Risk | Effort | Status |
|-------|-------|------|--------|--------|
| 1 — Yarn | `collectory-ui` | Zero | 30 min | ✅ Done |
| 2 — `@ala/common-ui` dep | `collectory-ui` | Zero | 1 hr | ✅ Done |
| 6 — Env vars | `collectory-ui` | Low | 30 min | ✅ Done |
| 7 — Config cleanup | `collectory-ui` | Zero | 30 min | ✅ Done |
| 5 — AlaLayout | `collectory-ui` | Medium | 2 hrs | ✅ Done |
| 3 — Auth endpoints | `collectory-api` | Medium | 1–2 days | ✅ Done |
| 4 — Frontend auth | `collectory-ui` | High | 4–6 hrs | ✅ Done |

**Recommended order:** do Phases 1, 2, 6, 7 first (all frontend, zero risk). Then Phase 3
(backend auth endpoints) in isolation so it can be tested via `curl` before the frontend
switches. Then Phases 4 and 5 together since they are coupled.

### Prerequisites before starting Phase 3

Confirm the following before building the backend auth endpoints:

1. The OIDC client (`collectory`) is registered with the identity provider (CAS/Cognito) and has
   a client secret and the `/callback` redirect URI configured.
2. The identity provider supports the PKCE flow (S256 code challenge).
3. The Spring session store is decided: in-memory (`HttpSession` default) works for a single
   instance; a Redis session store is required for multi-instance deployments.
4. `security.cors.origins` in `application.properties` lists all deployed `collectory-ui` origins
   so that the browser's `Origin` header on `/session` requests is accepted.
