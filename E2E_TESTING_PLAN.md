# E2E Testing Plan for Collectory Migration

> **This is a living document.** Whoever implements this plan should use the checkboxes in the
> [Implementation checklist](#implementation-checklist) section to track progress and update
> this file as decisions are made or the plan evolves.

## Context

The Collectory is being migrated from Grails 6.2.2 to Spring Boot 3.2 + React 19. To ensure
the migration is faithful, we need E2E tests that:

1. Run against the **Grails app** and pass (proving they capture existing behaviour)
2. Run against the **new app** (same tests, different base URL) to verify migration correctness
3. Cover all user-facing functionality: public pages, REST API, feeds, and admin pages

**Key decisions:**
- Auth: M2M (machine-to-machine) JWT tokens only -- no browser-based login tests
- Data: Seed known test entities via `/ws/` POST endpoints
- Location: Top-level `e2e/` directory, independent of both codebases
- M2M credentials: Configurable via env vars (to be set up by the implementer)

---

## Coverage scope

This suite covers **both UI and backend**:

| Layer | What's tested | How |
|-------|--------------|-----|
| **Backend API** | All `/ws/*` REST endpoints -- entity CRUD, contacts, counts, lookup, EML, citations, find, connection parameters, catalogue | Playwright `request` fixture (pure HTTP, no browser) |
| **Frontend UI** | Public show pages for all entity types, home page, datasets listing | Playwright `page` fixture (browser, content-based assertions) |
| **Feeds** | RSS, RIF-CS, sitemap | HTTP + XML validation |
| **Admin endpoints** | Dashboard, entity list pages | API-level checks with Bearer token |

The same tests work against both the Grails app (server-rendered GSP) and the new app
(Spring Boot API + React SPA) because assertions target **content and HTTP responses**, not
DOM structure.

---

## Directory structure

```
e2e/
  package.json                    # @playwright/test + dotenv
  playwright.config.ts            # BASE_URL from env, projects for api/public/admin
  tsconfig.json                   # Strict TS, ESM
  .env.grails                     # BASE_URL=http://localhost:8080
  .env.newapp                     # BASE_URL=http://localhost:8080 (or :3000 for Vite dev)
  global-setup.ts                 # Obtain M2M token + seed test data
  global-teardown.ts              # Clean up seeded test data
  fixtures/
    auth.ts                       # fetchM2MToken() -- calls OIDC token endpoint
    seed-data.ts                  # seedTestData() / cleanupTestData() -- POST/DELETE via /ws/
    test-entities.ts              # Constants: names, acronyms for seeded test entities
  helpers/
    api.ts                        # Thin wrapper: apiGet, apiPost, apiPut, apiDelete with Bearer token
    expect-entity.ts              # Reusable assertion helpers (entity has name, has uid, etc.)
  tests/
    api/
      entity-crud.spec.ts         # GET/POST/PUT/DELETE for all 6 entity types (parameterised)
      entity-contacts.spec.ts     # Contact CRUD on entities
      entity-counts.spec.ts       # /ws/{entity}/count
      connection-params.spec.ts   # /ws/dataResource/{uid}/connectionParameters
      lookup.spec.ts              # /ws/lookup/* endpoints
      eml.spec.ts                 # /ws/eml/{id} -- XML response
      find-entities.spec.ts       # /ws/find/{entity}
      citations.spec.ts           # /ws/citations
      catalogue.spec.ts           # /ws (root catalogue)
    public/
      home.spec.ts                # GET / -- page loads, has map or content
      datasets.spec.ts            # GET /datasets -- listing, pagination
      show-collection.spec.ts     # GET /public/showCollection/{uid}
      show-institution.spec.ts    # GET /public/showInstitution/{uid}
      show-data-resource.spec.ts  # GET /public/showDataResource/{uid}
      show-data-provider.spec.ts  # GET /public/showDataProvider/{uid}
      show-data-hub.spec.ts       # GET /public/showDataHub/{uid}
    feeds/
      rss.spec.ts                 # GET /feed -- valid RSS XML
      rif-cs.spec.ts              # GET /rif-cs -- valid RIF-CS XML
      sitemap.spec.ts             # GET /sitemap.xml -- valid sitemap
    admin/
      manage-dashboard.spec.ts    # GET /admin -- dashboard content (API-level check with token)
      entity-list.spec.ts         # GET /{entity}/list -- all 6 entity types
```

~22 test files. Parameterised tests (e.g. `entity-crud.spec.ts` loops over 6 entity types)
multiply coverage significantly.

---

## Test framework

**Playwright** for everything -- both API and UI tests.

- API tests use `request` fixture (Playwright's `APIRequestContext`) -- no browser needed
- Public page tests use `page` fixture to verify rendered content
- Single dependency, single config, single report format

---

## Authentication strategy

**M2M JWT tokens only** -- no browser login flow.

```
+-------------------+    client_credentials    +----------------+
|  global-setup     | ------------------------ | OIDC provider  |
|  (test startup)   | <-- access_token ------- | (Cognito)      |
+-------------------+                          +----------------+
        |
        | Bearer token stored in env/shared state
        v
+-------------------+    Authorization: Bearer  +----------------+
|  API test specs   | ------------------------ | Grails or      |
|  (Playwright)     | <-- JSON responses ----- | Spring Boot    |
+-------------------+                          +----------------+
```

Both the Grails app (via `ala-ws-security`) and the Spring Boot app (via
`ala-ws-spring-security`) validate the same JWT format, so the same token works for both.

Admin page tests use API-level checks (`request.get()` with Bearer token) rather than
browser-based navigation.

### Environment variables (implementer fills in)

```
E2E_TOKEN_ENDPOINT=https://auth.example.com/oauth2/token
E2E_CLIENT_ID=<m2m-client-id>
E2E_CLIENT_SECRET=<m2m-client-secret>
```

### Auth helper (`fixtures/auth.ts`)

```typescript
export async function fetchM2MToken(): Promise<string> {
  const tokenEndpoint = process.env.E2E_TOKEN_ENDPOINT!;
  const clientId = process.env.E2E_CLIENT_ID!;
  const clientSecret = process.env.E2E_CLIENT_SECRET!;

  const res = await fetch(tokenEndpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'client_credentials',
      client_id: clientId,
      client_secret: clientSecret,
      scope: 'openid ala/attrs ala/roles',
    }),
  });
  const { access_token } = await res.json();
  return access_token;
}
```

---

## Test data strategy

**Seed known entities via the `/ws/` REST API** in `global-setup.ts`.

This is database-agnostic (works with MySQL for Grails, PostgreSQL for Spring Boot) and
ensures predictable values for assertions regardless of what else is in the DB.

### Test entity constants (`fixtures/test-entities.ts`)

```typescript
export const TEST_INSTITUTION = {
  name: 'E2E Test Institution',
  acronym: 'E2ETI',
  pubDescription: 'Institution created by E2E test suite',
};

export const TEST_COLLECTION = {
  name: 'E2E Test Collection',
  acronym: 'E2ETC',
  pubDescription: 'Collection created by E2E test suite',
};

// ... TEST_DATA_PROVIDER, TEST_DATA_RESOURCE, TEST_DATA_HUB, TEST_CONTACT
```

### Seed/cleanup (`fixtures/seed-data.ts`)

```typescript
export async function seedTestData(baseUrl: string, token: string): Promise<SeededUids> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // Create in dependency order: institution -> collection, dataProvider -> dataResource, dataHub
  const inst = await post(`${baseUrl}/ws/institution`, TEST_INSTITUTION, headers);
  const coll = await post(`${baseUrl}/ws/collection`,
    { ...TEST_COLLECTION, institution: { uid: inst.uid } }, headers);
  const dp = await post(`${baseUrl}/ws/dataProvider`, TEST_DATA_PROVIDER, headers);
  const dr = await post(`${baseUrl}/ws/dataResource`,
    { ...TEST_DATA_RESOURCE, dataProvider: { uid: dp.uid } }, headers);
  const dh = await post(`${baseUrl}/ws/dataHub`, TEST_DATA_HUB, headers);

  return { institution: inst.uid, collection: coll.uid, dataProvider: dp.uid,
           dataResource: dr.uid, dataHub: dh.uid };
}

export async function cleanupTestData(baseUrl: string, token: string, uids: SeededUids) {
  // Delete in reverse dependency order
  for (const [entity, uid] of Object.entries(uids).reverse()) {
    await del(`${baseUrl}/ws/${entity}/${uid}`, token);
  }
}
```

UIDs are stored in a JSON file by `global-setup.ts` and read by test specs via a shared fixture.

---

## Playwright configuration

```typescript
import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';

const envFile = process.env.TARGET === 'newapp' ? '.env.newapp' : '.env.grails';
dotenv.config({ path: envFile });

export default defineConfig({
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  timeout: 30_000,
  retries: 1,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    extraHTTPHeaders: {
      Authorization: `Bearer ${process.env.E2E_AUTH_TOKEN || ''}`,
    },
  },

  projects: [
    { name: 'api',    testDir: './tests/api',    use: { ...devices['Desktop Chrome'] } },
    { name: 'public', testDir: './tests/public', use: { ...devices['Desktop Chrome'] } },
    { name: 'feeds',  testDir: './tests/feeds',  use: { ...devices['Desktop Chrome'] } },
    { name: 'admin',  testDir: './tests/admin',  use: { ...devices['Desktop Chrome'] } },
  ],
});
```

### Running tests

```bash
# Against Grails app
cd e2e && TARGET=grails npx playwright test

# Against new app
cd e2e && TARGET=newapp npx playwright test

# Run only API tests (fastest feedback)
cd e2e && TARGET=grails npx playwright test --project=api

# Run only public page tests
cd e2e && TARGET=grails npx playwright test --project=public

# View HTML report
npx playwright show-report
```

---

## Test patterns

### API tests -- content-focused, app-agnostic

```typescript
// tests/api/entity-crud.spec.ts
const ENTITIES = ['collection', 'institution', 'dataProvider', 'dataResource', 'dataHub'];

for (const entity of ENTITIES) {
  test.describe(`/ws/${entity}`, () => {
    test('GET list returns 200 and JSON array', async ({ request }) => {
      const res = await request.get(`/ws/${entity}`);
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(Array.isArray(body)).toBe(true);
    });

    test('GET by UID returns entity with expected fields', async ({ request }) => {
      const uid = seededUids[entity];
      const res = await request.get(`/ws/${entity}/${uid}`);
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.uid).toBe(uid);
      expect(body.name).toBeTruthy();
    });

    test('PUT update returns 200', async ({ request }) => {
      const uid = seededUids[entity];
      const res = await request.put(`/ws/${entity}/${uid}`, {
        data: { pubDescription: 'Updated by E2E test' },
      });
      expect(res.status()).toBe(200);
    });
  });
}
```

### Public page tests -- role/heading-based selectors

```typescript
// tests/public/show-collection.spec.ts
test('collection show page displays entity name and metadata', async ({ page }) => {
  const uid = seededUids.collection;
  await page.goto(`/public/showCollection/${uid}`);

  // Works for both GSP (server-rendered h1) and React (client-rendered h1)
  await expect(page.getByRole('heading', { name: TEST_COLLECTION.name })).toBeVisible();

  // Check key metadata sections exist
  await expect(page.getByText(/Description/i)).toBeVisible();
});
```

### Feed tests -- XML validation

```typescript
// tests/feeds/rss.spec.ts
test('RSS feed returns valid XML with channel element', async ({ request }) => {
  const res = await request.get('/feed');
  expect(res.status()).toBe(200);
  const contentType = res.headers()['content-type'];
  expect(contentType).toContain('xml');
  const body = await res.text();
  expect(body).toContain('<channel>');
  expect(body).toContain('<item>');
});
```

---

## Key files to reference during implementation

| File | Why |
|------|-----|
| `grails-app/controllers/.../UrlMappings.groovy` | Canonical route definitions -- every test URL must trace here |
| `grails-app/controllers/.../DataController.groovy` | REST API response shapes for entity CRUD |
| `grails-app/controllers/.../PublicController.groovy` | Public page actions |
| `grails-app/controllers/.../LookupController.groovy` | Lookup API response shapes |
| `collectory-ui/src/routes.tsx` | React routes -- verify URL parity with Grails |
| `collectory-api/.../config/SecurityConfig.java` | Which endpoints need auth |
| `collectory-api/.../controller/DataController.java` | New API response shapes (must match Grails) |

---

## Implementation checklist

Use this checklist to track progress. Mark items as complete and add notes as needed.

### Phase 1 -- Scaffold
- [ ] Create `e2e/` directory structure
- [ ] Create `package.json` with `@playwright/test` and `dotenv`
- [ ] Create `tsconfig.json`
- [ ] Create `playwright.config.ts`
- [ ] Create `.env.grails` and `.env.newapp` (with placeholder M2M credentials)
- [ ] Set up M2M client in Cognito and fill in credentials
- [ ] Write `fixtures/auth.ts`
- [ ] Write `fixtures/test-entities.ts`
- [ ] Write `fixtures/seed-data.ts`
- [ ] Write `helpers/api.ts`
- [ ] Write `global-setup.ts`
- [ ] Write `global-teardown.ts`
- [ ] Install Playwright and verify setup runs: `npx playwright install chromium`

### Phase 2 -- API tests (backend coverage)
- [ ] `tests/api/entity-crud.spec.ts` -- CRUD for all 6 entity types
- [ ] `tests/api/entity-contacts.spec.ts` -- Contact endpoints on entities
- [ ] `tests/api/entity-counts.spec.ts` -- Count/groupBy endpoints
- [ ] `tests/api/connection-params.spec.ts` -- Connection parameters
- [ ] `tests/api/lookup.spec.ts` -- Lookup endpoints
- [ ] `tests/api/eml.spec.ts` -- EML XML generation
- [ ] `tests/api/find-entities.spec.ts` -- Find/search entities
- [ ] `tests/api/citations.spec.ts` -- Citations endpoint
- [ ] `tests/api/catalogue.spec.ts` -- Root catalogue
- [ ] All API tests pass against Grails app

### Phase 3 -- Public page tests (frontend coverage)
- [ ] `tests/public/home.spec.ts` -- Home page
- [ ] `tests/public/datasets.spec.ts` -- Datasets listing
- [ ] `tests/public/show-collection.spec.ts` -- Collection show page
- [ ] `tests/public/show-institution.spec.ts` -- Institution show page
- [ ] `tests/public/show-data-resource.spec.ts` -- Data resource show page
- [ ] `tests/public/show-data-provider.spec.ts` -- Data provider show page
- [ ] `tests/public/show-data-hub.spec.ts` -- Data hub show page
- [ ] All public page tests pass against Grails app

### Phase 4 -- Feed tests
- [ ] `tests/feeds/rss.spec.ts` -- RSS feed
- [ ] `tests/feeds/rif-cs.spec.ts` -- RIF-CS feed
- [ ] `tests/feeds/sitemap.spec.ts` -- Sitemap XML
- [ ] All feed tests pass against Grails app

### Phase 5 -- Admin tests
- [ ] `tests/admin/manage-dashboard.spec.ts` -- Admin dashboard (API-level)
- [ ] `tests/admin/entity-list.spec.ts` -- Entity list pages (all 6 types)
- [ ] All admin tests pass against Grails app

### Phase 6 -- Baseline verification
- [ ] Full suite passes against the Grails app with zero failures
- [ ] Flaky tests identified and stabilised
- [ ] HTML report reviewed for coverage gaps
- [ ] Document any Grails-specific behaviours observed during test writing

### Phase 7 -- Run against new app
- [ ] Full suite runs against Spring Boot + React app
- [ ] Failures triaged as migration gaps (not test issues)
- [ ] Migration gaps documented and filed as issues

---

## Verification

```bash
# Install
cd e2e && yarn install && npx playwright install chromium

# Start Grails app (in another terminal)
./gradlew bootRun

# Run all tests against Grails
cd e2e && TARGET=grails npx playwright test

# Run only API tests (fastest feedback loop)
cd e2e && TARGET=grails npx playwright test --project=api

# View HTML report
npx playwright show-report
```
