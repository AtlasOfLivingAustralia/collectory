# Collectory Migration Progress

## Overview
Migrating the Collectory application from Grails 6.2.2 to Spring Boot 3.2.5 (backend) + React 19 SPA (frontend).

| Aspect | Before | After |
|--------|--------|-------|
| Backend Framework | Grails 6.2.2 | Spring Boot 3.2.5 |
| Language | Groovy | Java 21 |
| ORM | GORM | Spring Data JPA + Hibernate 6 |
| Database | MySQL 8 | PostgreSQL 17 |
| Build | Gradle | Maven |
| DB Migrations | Liquibase (database-migration plugin) | Flyway |
| Auth | ala-auth Grails plugin | ala-ws-spring-security (JWT/OIDC) |
| Frontend | 145 GSP views + jQuery | React 19 SPA |
| UI Framework | Bootstrap 5 (via ala-bootstrap5) | Bootstrap 5 (react-bootstrap) |

## Phase Status

| Phase | Description | Status | Date |
|-------|-------------|--------|------|
| 0 | Project Scaffolding | ✅ Complete | 2026-03-18 |
| 1 | Domain Layer + JPA Entities | ✅ Complete | 2026-03-18 |
| 2 | Core Services | ✅ Complete | 2026-03-18 |
| 3 | REST API Controllers | ✅ Complete | 2026-03-18 |
| 4 | Specialized Services | ✅ Complete | 2026-03-19 |
| 5 | Public React Pages | ⬜ Not started | — |
| 6 | Admin React Pages | ⬜ Not started | — |
| 7 | Manage + Reports React Pages | ⬜ Not started | — |
| 8 | Integration, Testing & Cutover | ⬜ Not started | — |

## Backend File Inventory (97 Java files)

### Config (7 files)
- `CollectoryApplication.java` — @SpringBootApplication entry point
- `AppProperties.java` — @ConfigurationProperties for all collectory.* config
- `AppInitializer.java` — ApplicationRunner for startup validation
- `SecurityConfig.java` — Spring Security filter chain (JWT validation)
- `CorsConfig.java` — CORS configuration
- `CacheConfig.java` — EHCache manager
- `WebConfig.java` — SPA forwarding, static resources
- `ApplicationContextHolder.java` — Static Spring context access for non-managed beans

### Domain Entities (20 files)
- `ProviderGroup.java` — @MappedSuperclass (from Groovy trait)
- `Collection.java`, `Institution.java`, `DataProvider.java`, `DataResource.java`, `DataHub.java`, `TempDataResource.java` — Main entity types
- `Contact.java`, `ContactFor.java` — Contact system
- `Address.java`, `Image.java` — @Embeddable types
- `Attribution.java`, `ExternalIdentifier.java`, `Licence.java` — Supporting entities
- `ProviderCode.java`, `ProviderMap.java` — Code mapping
- `AuditLogEvent.java`, `ActivityLog.java` — Audit
- `Sequence.java` — UID generation sequences
- `Action.java` — Enum

### Repositories (16 files)
One per entity: CollectionRepository, InstitutionRepository, DataProviderRepository, DataResourceRepository, DataHubRepository, TempDataResourceRepository, ContactRepository, ContactForRepository, ExternalIdentifierRepository, LicenceRepository, ProviderCodeRepository, ProviderMapRepository, AuditLogEventRepository, ActivityLogRepository, SequenceRepository, AttributionRepository

### Services (22 files)
**Core (Phase 2):** CrudService, ProviderGroupService, CollectoryAuthService, ContactService, ActivityLogService, CollectionService, InstitutionService, DataResourceService, DataHubService, MetadataService, IsoCodeService, ExternalIdentifierService, MessageSourceCacheService, IdGeneratorService

**Specialized (Phase 4):** EmlImportService, EmlRenderService, GbifService, GbifRegistryService, IptService, ExternalDataService, DataImportService, RifCsService, SitemapService

### Controllers (12 files)
DataController, PublicController, LookupController, ReportsController, ManageController, GbifController, IptController, DataFeedsController, AdminController, SitemapController, ConfigController, SpaController

### DTOs (7 files)
CollectionSummary, InstitutionSummary, DataProviderSummary, DataResourceSummary, ProviderGroupSummary, GBIFActiveLoad, GBIFLoadSummary, ExternalResourceBean, DataSourceConfiguration

### Resources (5 files)
TaskPhase, DataSourceAdapter, DataSourceLoad, GbifDataSourceAdapter, GbifRepatDataSourceAdapter

### Security (3 files)
PermissionChecker, PermissionRequired, SkipPermissionCheck

### Other (3 files)
ExternalResourceException, InvalidUidException, JSONHelper

## Known Gaps

### Not ported (intentional):
- **DataLoaderService** — Legacy CSV/JSON bulk import tool (BCI/AMRRN). Not needed for normal operation.
- **InstitutionCodeLoaderService** — Institution code lookup from external XML. Legacy.
- **AsyncGbifRegistryService** — Async wrapper; Spring @Async replaces it directly on GbifRegistryService.
- **UI controllers** (CollectionController, InstitutionController, etc.) — React SPA handles all UI.
- **Supporting Groovy classes**: Classification, CollectionLocation, ContactRelationship, Utilities, Profile, DarwinCoreFields, MessagePropertiesTrait — functionality either inlined into services or not needed.

### To be addressed:
- `/ws/licence` endpoint — needs a LicenceController
- ProviderGroup trait methods (getContacts, isAuthorised, etc.) — handled by services instead of domain methods
- Entity subclass convenience methods — thinner domain model, logic in services
