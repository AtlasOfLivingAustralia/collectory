# Collectory

ALA Collectory — biodiversity collections metadata registry for the Atlas of Living Australia.

See [MIGRATION_PLAN.md](MIGRATION_PLAN.md) for the full migration plan (GORM→JPA mappings, controller migration map, phased implementation checklist, verification strategy).

## Project Overview

This is a **monorepo** with three codebases:

| Directory | Stack | Purpose |
|-----------|-------|---------|
| `grails-app/` + `src/` | Grails 6.2.2 / Groovy / MySQL 8 | Legacy app (reference only — do NOT modify) |
| `collectory-api/` | Spring Boot 3.2.5 / Java 21 / PostgreSQL 17 / Maven | New backend API (1:1 rewrite of Grails) |
| `collectory-ui/` | React 19 / TypeScript 5.6 / Vite 6 | New SPA frontend (replaces 145 GSP views) |

The migration is a **faithful 1:1 rewrite** — every endpoint, response shape, and behavior must match the Grails app exactly. This is not a redesign.

## Git

- **Main branch**: `develop`
- **Work branch**: `feat/spa` (full-stack migration)
- **Bootstrap upgrade branch**: `feat/bootstrap-v5` (completed)

## Development Setup

### Backend (collectory-api)

```bash
# Start PostgreSQL
cd collectory-api && docker compose up -d

# Build and run
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or just compile
mvn compile
```

- Runs on `http://localhost:8080`
- PostgreSQL on port 5432 (user: `collectory_user`, pass: `password`, db: `collectory`)
- Flyway migrations in `src/main/resources/flyway/`

### Frontend (collectory-ui)

```bash
cd collectory-ui
npm install
npm run dev
```

- Runs on `http://localhost:3000`
- Vite proxies `/ws`, `/rif-cs`, `/feed`, `/eml`, `/data`, `/upload` to `http://localhost:8080`
- OIDC config in `.env.development`

### Legacy Grails app (reference only)

```bash
./gradlew bootRun
```

## Key Conventions

### Backend

- **Entities**: JPA entities in `domain/`, inheriting from `ProviderGroup` (`@MappedSuperclass`)
- **Repositories**: Spring Data JPA in `repository/` — use derived query methods, `@Query` for complex queries
- **Services**: Business logic in `service/` — `@Transactional` on service methods
- **Controllers**: REST controllers in `controller/` — `@Transactional(readOnly = true)` at class level for lazy loading, `@Transactional` on write methods
- **Lombok**: Use `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` — version pinned to 1.18.38 for Java 21 compat
- **Config**: `AppProperties.java` with `@ConfigurationProperties`, accessed via `@Value` or injection
- **Auth**: ALA's `ala-ws-spring-security` for JWT validation. `PermissionChecker` interceptor with `@PermissionRequired` annotation
- **Caching**: `ConcurrentMapCacheManager` — all cache names must be registered in `CacheConfig.java`
- **External service calls**: Use `ProxyController` to avoid CORS — frontend calls `/ws/proxy/biocache` and `/ws/proxy/logger` instead of external URLs directly

### Frontend

- **Routing**: React Router v7 in `routes.tsx` — lazy-loaded components with `withSuspense()`
- **API calls**: `apiClient` (Axios instance in `api/client.ts`) with base URL `/ws` and JWT interceptor
- **Server state**: TanStack Query v5 — `useQuery` for reads, `useMutation` for writes
- **Forms**: React Hook Form + Zod for validation
- **Auth**: `react-oidc-context` + `oidc-client-ts`. Roles extracted in `useAuth.ts` from multiple OIDC claim names. `ProtectedRoute` component wraps auth-required pages
- **Role hierarchy**: `ROLE_ADMIN` implies `ROLE_EDITOR`. `ProtectedRoute` defaults to `ROLE_EDITOR` if no `requiredRole` prop
- **i18n**: `react-i18next` — keys from Grails `messages*.properties` files, converted to JSON
- **CSS**: Bootstrap 5 classes + Font Awesome 4.7 (`fa fa-*` icons)
- **Layout**: `AlaLayout` loads ALA common header/footer HTML fragments

## Critical Rules

### Always cross-reference the Grails source

Before adding or modifying any Spring Boot endpoint:

1. Check `grails-app/controllers/au/org/ala/collectory/UrlMappings.groovy` for the route
2. Read the corresponding Grails controller action to understand exact behavior
3. Match the response format, status codes, and edge cases

Key Grails files: `DataController.groovy`, `ProviderGroupController.groovy`, `LookupController.groovy`, `PublicController.groovy`, `ManageController.groovy`, `ReportsController.groovy`, `CollectoryAuthService.groovy`

### Don't invent endpoints

Every `/ws/*` endpoint must have a corresponding route in the Grails `UrlMappings.groovy` or controller. Don't create endpoints based on frontend expectations — verify they existed in Grails first.

### Lazy loading

All controllers that access entity relationships MUST have `@Transactional(readOnly = true)` at class level. Without this, Hibernate lazy-loaded collections throw `LazyInitializationException` because entities become detached between service and controller transactions.

### Cache names

Any `@Cacheable("name")` annotation requires the cache name to be registered in `CacheConfig.java`'s `ConcurrentMapCacheManager` constructor.

## Project Structure

### Backend (collectory-api/src/main/java/au/org/ala/collectory/)

```
CollectoryApplication.java          # Entry point
config/                             # AppProperties, SecurityConfig, CacheConfig, WebConfig, CorsConfig
controller/                         # REST controllers (DataController, LookupController, PublicController, etc.)
domain/                             # JPA entities (Collection, Institution, DataResource, etc.)
dto/                                # Data transfer objects
repository/                         # Spring Data JPA repositories
service/                            # Business logic (CrudService, ProviderGroupService, GbifService, etc.)
security/                           # Auth filters, permission checker
resources/                          # Connection profiles, adapters
util/                               # JSONHelper, Utilities
exception/                          # Custom exceptions
```

### Frontend (collectory-ui/src/)

```
main.tsx                            # Entry point with providers
App.tsx                             # Root component
routes.tsx                          # All route definitions (lazy-loaded)
api/client.ts                       # Axios instance with JWT
api/types.ts                        # TypeScript interfaces
api/endpoints/                      # API endpoint functions
auth/                               # AuthProvider, useAuth, ProtectedRoute
i18n/                               # i18next config + locale JSONs
layouts/AlaLayout.tsx               # ALA header/footer
pages/                              # Page components (public show pages, admin, manage, reports)
components/                         # Shared components (public partials, common UI)
hooks/                              # Custom React hooks
```

## Route Ownership

| URL Pattern | Handled By |
|-------------|-----------|
| `/ws/*` | Spring Boot API (REST) |
| `/rif-cs`, `/feed` | Spring Boot API (XML/RSS) |
| `/eml/*` | Spring Boot API (XML) |
| `/sitemap*.xml` | Spring Boot API (XML) |
| `/data/*`, `/upload/*` | Spring Boot API (file serving) |
| Everything else | React SPA (client-side routing) |

## Testing

### Backend
```bash
cd collectory-api && mvn test          # Unit tests
cd collectory-api && mvn verify        # Integration tests (Testcontainers PostgreSQL)
```

### Frontend
```bash
cd collectory-ui && npm test           # Vitest unit tests
cd collectory-ui && npx tsc --noEmit   # TypeScript check
cd collectory-ui && npm run test:e2e   # Playwright E2E tests
```
