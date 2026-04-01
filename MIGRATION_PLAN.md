# Collectory: Full-Stack Migration Plan
## Grails → Spring Boot (Backend) + GSP → React SPA (Frontend)

## Context

The Collectory is a Grails 6.2.2 application (Java 17, Hibernate 5, MySQL 8) that manages biodiversity collections metadata for the Atlas of Living Australia (ALA). It has 145 GSP views, 27 controllers, 25 services, 20 domain classes, and 24 supporting Groovy classes. The frontend uses jQuery 3.3.1 with Leaflet maps, DataTables, jQuery UI, and Bootstrap 5.

**Additional change**: The database will be migrated from MySQL 8 to PostgreSQL. This requires schema migration scripts and driver/dialect changes.

**Why migrate the full stack**:
- **Backend**: Grails framework ties the app to GORM, GSP, and ALA-specific Grails plugins. A Spring Boot app is more portable, container-friendly, has better tooling/IDE support, and aligns with modern deployment (Docker/K8s). The existing REST API structure stays identical.
- **Frontend**: GSP views are tightly coupled to the Grails server. A React SPA enables independent development, component reuse across ALA apps, and modern tooling.
- **Key constraint**: Every existing endpoint, every piece of functionality must be preserved 1:1.

---

# PART A: BACKEND — Grails to Spring Boot

## A1. Backend Technology Stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Framework | Spring Boot 3.2.x | Aligned with ALA reference project (search-service) |
| Language | Java 21 | Clean break from Groovy; virtual threads support |
| ORM | Spring Data JPA + Hibernate 6 | Replaces GORM |
| Database | PostgreSQL 17 | Replaces MySQL 8; JSONB for complex fields; aligned with ALA stack |
| Build | Maven | Aligned with ALA reference project (search-service uses Maven) |
| DB Migration | Flyway | Aligned with ALA reference; PostgreSQL-native schema |
| Auth | `ala-ws-spring-security:7.3.0-java21` + `ala-ws-security:7.3.0-java21` | ALA's own Spring Security integration with OIDC/JWT — same libs used by search-service |
| API Docs | SpringDoc OpenAPI 2.5.x | Same as search-service |
| Audit | Spring Data JPA Auditing (`@CreatedDate`, `@LastModifiedDate`) + custom `@EntityListener` for audit_log table | Replaces grails audit-logging plugin |
| Caching | Spring Cache + EHCache | Same as search-service |
| Config | Spring externalized config (`spring.config.import`) + `@Value` annotations | Replaces external-config plugin |
| Error tracking | Sentry Spring Boot starter | Direct replacement |
| HTTP Client | Spring RestClient + ALA `WebService` pattern (with `TokenInterceptor` for JWT) | Same pattern as search-service |
| CSV | OpenCSV (keep) | Same dependency |
| Lombok | `@Data`, `@SuperBuilder`, `@Slf4j`, `@NoArgsConstructor` | Same patterns as search-service |
| Testing | JUnit 5 + Mockito + Testcontainers (PostgreSQL) | Same as search-service |
| Container | Docker multi-stage build + docker-compose | Same pattern as search-service |

## A2. Spring Boot Project Structure

**Monorepo layout**: Both `collectory-api/` and `collectory-ui/` live in this same repository alongside the existing Grails code. The Grails code remains untouched during development and is removed at cutover.

**Reference project**: Structure follows patterns from `atlas-index/search-service` — same ALA security libs, same Spring Boot version, same PostgreSQL + Flyway + Lombok + SpringDoc patterns.

```
collectory-api/                          # New directory at repo root
  pom.xml                               # Maven (aligned with search-service)
  Dockerfile                             # Multi-stage build (same pattern as search-service)
  docker-compose.yml                     # PostgreSQL 17 + app
  src/
    main/
      java/au/org/ala/collectory/
        CollectoryApplication.java       # @SpringBootApplication

        config/
          SecurityConfig.java            # Spring Security filter chain (JWT validation)
          CorsConfig.java                # CORS configuration
          AppProperties.java             # @ConfigurationProperties for all config
          CacheConfig.java               # Cache manager setup
          WebConfig.java                 # SPA forwarding, static resources

        domain/                          # JPA entities (from GORM domain classes)
          ProviderGroup.java             # @MappedSuperclass (from trait)
          Institution.java               # @Entity
          Collection.java                # @Entity
          DataProvider.java              # @Entity
          DataResource.java              # @Entity
          DataHub.java                   # @Entity
          TempDataResource.java          # @Entity
          Contact.java                   # @Entity
          ContactFor.java                # @Entity
          Address.java                   # @Embeddable
          Image.java                     # @Embeddable
          Attribution.java               # @Entity
          ExternalIdentifier.java        # @Entity
          Licence.java                   # @Entity
          ProviderCode.java              # @Entity
          ProviderMap.java               # @Entity
          AuditLogEvent.java             # @Entity
          ActivityLog.java               # @Entity
          Sequence.java                  # @Entity

        repository/                      # Spring Data JPA repositories
          CollectionRepository.java      # extends JpaRepository + JpaSpecificationExecutor
          InstitutionRepository.java
          DataProviderRepository.java
          DataResourceRepository.java
          DataHubRepository.java
          TempDataResourceRepository.java
          ContactRepository.java
          ContactForRepository.java
          ExternalIdentifierRepository.java
          LicenceRepository.java
          ProviderCodeRepository.java
          ProviderMapRepository.java
          AuditLogEventRepository.java
          ActivityLogRepository.java
          SequenceRepository.java
          AttributionRepository.java

        service/                         # Business logic (from Grails services)
          CrudService.java               # Core entity CRUD + JSON building
          ProviderGroupService.java      # Generic entity operations
          CollectionService.java
          InstitutionService.java
          DataResourceService.java
          DataProviderService.java       # (new, extracted from controller logic)
          DataHubService.java
          ContactService.java            # (new, extracted from controller logic)
          IdGeneratorService.java        # UID generation
          CollectoryAuthService.java     # Auth/role checking
          ActivityLogService.java        # Audit logging
          EmlImportService.java          # EML XML import
          EmlRenderService.java          # EML XML export
          GbifService.java               # GBIF data loading
          GbifRegistryService.java       # GBIF registration
          IptService.java                # IPT scanning
          ExternalDataService.java       # External resource management
          DataImportService.java
          DataLoaderService.java
          RifCsService.java              # RIF-CS feed generation
          SitemapService.java
          MetadataService.java
          IsoCodeService.java
          ExternalIdentifierService.java
          MessageSourceCacheService.java

        controller/                      # REST controllers
          DataController.java            # /ws/* entity CRUD (main API)
          PublicController.java          # Public JSON endpoints
          LookupController.java          # /ws/lookup/*
          ReportsController.java         # /ws/reports/* (JSON only, no GSP)
          ManageController.java          # /ws/manage/*
          ContactController.java         # /ws/contacts/*
          GbifController.java            # /ws/gbif/*
          IptController.java             # /ws/ipt/*
          DataFeedsController.java       # /rif-cs, /feed
          AdminController.java           # /admin/export
          SitemapController.java
          ConfigController.java          # /ws/config (new)
          FileController.java            # /data/*, /upload/* file serving
          SpaController.java             # Catch-all → index.html

        dto/                             # Data transfer objects
          CollectionSummary.java
          InstitutionSummary.java
          DataProviderSummary.java
          DataResourceSummary.java
          ProviderGroupSummary.java
          ContactRelationship.java
          CollectionLocation.java
          GBIFActiveLoad.java
          GBIFLoadSummary.java
          ExternalResourceBean.java
          DataSourceConfiguration.java

        resources/                       # Connection profiles, adapters
          Profile.java
          DarwinCoreFields.java
          DataSourceAdapter.java
          DataSourceLoad.java
          TaskPhase.java
          GbifDataSourceAdapter.java
          GbifRepatDataSourceAdapter.java

        util/
          JSONHelper.java
          Utilities.java
          OutputFormat.java              # JSON formatting helpers

        exception/
          ExternalResourceException.java
          InvalidUidException.java

        security/
          JwtAuthFilter.java             # Replaces TokenInterceptor
          PermissionChecker.java         # Replaces PermissionInterceptor
          PermissionRequired.java        # Custom annotation (keep same name)
          SkipPermissionCheck.java       # Custom annotation

      resources/
        application.properties           # Ported from Grails config (PostgreSQL, same format as search-service)
        application-dev.properties
        application-prod.properties
        flyway/
          V1__initial_schema.sql         # PostgreSQL-compatible initial schema (ported from initial.sql)
          V2__5_1_0_updates.sql          # Ported from 5.1.0*.sql migrations
        i18n/
          messages.properties            # Copy from grails-app/i18n/
          messages_ca.properties
          ... (18 files)

    test/
      java/au/org/ala/collectory/
        service/
          EmlImportServiceTest.java      # Port from EmlImportServiceSpec.groovy
          IptServiceTest.java            # Port from IptServiceSpec.groovy
        controller/
          DataControllerTest.java        # New integration tests
        repository/
          ... integration tests with Testcontainers
```

## A3. GORM → JPA Migration Map

### Domain Classes (20 files → 20 JPA entities)

| GORM Pattern | JPA Equivalent | Affected Files |
|--------------|----------------|----------------|
| `trait ProviderGroup` | `@MappedSuperclass abstract class ProviderGroup` | All 6 entity types inherit from it |
| `static embedded = ['address', 'logoRef', 'imageRef']` | `@Embedded Address address`, `@Embedded @AttributeOverrides(...) Image logoRef` | All ProviderGroup subtypes |
| `static belongsTo = Institution` | `@ManyToOne @JoinColumn(name="institution_id") Institution institution` | Collection |
| `static hasOne = [providerMap: ProviderMap]` | `@OneToOne(mappedBy="collection") ProviderMap providerMap` | Collection |
| `static hasMany = [collections: Collection]` | `@OneToMany(mappedBy="institution") Set<Collection> collections` | Institution |
| `static hasMany = [externalIdentifiers: ExternalIdentifier]` | `@OneToMany @JoinColumn(name="entity_uid", referencedColumnName="uid")` | All entities |
| Many-to-many with join table (`data_provider_institution`) | `@ManyToMany @JoinTable(name="data_provider_institution")` | DataProvider, DataResource |
| `static constraints { name(blank:false, maxSize:1024) }` | `@NotBlank @Size(max=1024) String name` + Jakarta Bean Validation | All domain classes |
| `static mapping { pubDescription type:'text' }` | `@Column(columnDefinition="text") String pubDescription` | Fields needing TEXT type |
| `static transients = ['creativeCommons', ...]` | `@Transient` annotation on those fields | DataResource, others |
| `static auditable = [ignore: [...]]` | Custom `@EntityListener` that writes to `audit_log` table (same approach as current, simpler than Envers) | 10 auditable entities |
| `dateCreated`, `lastUpdated` auto-timestamps | `@CreatedDate`, `@LastModifiedDate` with `@EntityListeners(AuditingEntityListener.class)` | All entities |
| JSON string fields (connectionParameters, etc.) | `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")` for PostgreSQL JSONB (enables SQL querying of JSON) or plain `String` where querying not needed | DataResource, DataHub, Collection |

### Key Entity Relationship Details

```
Institution (1) ←→ (many) Collection [belongsTo]
Institution (1) ←→ (1) ProviderMap [through Collection]
DataProvider (1) ←→ (many) DataResource [belongsTo]
DataResource (opt) ←→ (1) Institution
DataProvider ←→ (many-to-many) Institution [join: data_provider_institution]
DataProvider ←→ (many-to-many) Collection [join: data_provider_collection]
DataResource ←→ (many-to-many) Institution [join: data_resource_institution]
DataResource ←→ (many-to-many) Collection [join: data_resource_collection]
Contact ←→ (many) ContactFor [entityUid links to any entity's uid]
ProviderMap (1) ←→ (many) ProviderCode
All entities ←→ (many) ExternalIdentifier [via entityUid]
```

### Query Migration (60+ dynamic finders, 8 criteria queries, HQL)

| GORM Pattern | Spring Data JPA Replacement |
|--------------|---------------------------|
| `Collection.findByUid(uid)` | `collectionRepository.findByUid(uid)` — derived query method |
| `Institution.findByUid(uid)` | `institutionRepository.findByUid(uid)` |
| `DataResource.findByGuid(guid)` | `dataResourceRepository.findByGuid(guid)` |
| `ExternalIdentifier.findBySourceAndIdentifier(s, id)` | `externalIdentifierRepository.findBySourceAndIdentifier(s, id)` |
| `Contact.list(params)` | `contactRepository.findAll(PageRequest.of(page, size, sort))` |
| `Collection.count()` | `collectionRepository.count()` |
| `Entity.get(id)` | `repository.findById(id)` |
| `Institution.findAll("from Institution where childInstitutions like ?0", [uid])` | `@Query("SELECT i FROM Institution i WHERE i.childInstitutions LIKE %:uid%")` |
| `ProviderMap.executeQuery("select distinct m from ProviderMap m left join...")` | `@Query` with JPQL or native SQL on repository |
| `Institution.createCriteria().list { fetchMode 'collections', FetchMode.JOIN }` | `@EntityGraph(attributePaths={"collections"})` on repository method |
| `DataResource.findAllWhere([resourceType: type])` | `dataResourceRepository.findAllByResourceType(type)` |
| `entity.save(flush: true)` | `repository.saveAndFlush(entity)` |
| `entity.delete(flush: true)` | `repository.delete(entity); repository.flush()` |
| `Entity.withTransaction { ... }` | `@Transactional` on service method |

### Controller Migration (27 controllers → ~12 REST controllers)

The Grails controllers serve both HTML views and JSON APIs. In Spring Boot, we only need REST controllers (JSON). The 27 Grails controllers consolidate to ~12 because:
- GSP-rendering actions are removed (React handles UI)
- Entity-specific admin controllers (CollectionController, InstitutionController, etc.) merge their REST logic into `DataController`
- `ProviderGroupController` base class logic moves to services

| Grails Controller | Spring Boot Equivalent | Key Changes |
|-------------------|----------------------|-------------|
| `DataController` | `DataController.java` | Keep as-is; main REST API. Replace `render x as JSON` with `@RestController` return types. Replace `params` with `@RequestParam`/`@PathVariable` |
| `PublicController` | `PublicController.java` | JSON-only (condensed, mapFeatures, biocacheRecords proxy). Remove GSP render actions |
| `LookupController` | `LookupController.java` | Already JSON-only |
| `ReportsController` | `ReportsController.java` | Convert all 27 GSP-rendering actions to JSON endpoints |
| `ManageController` | `ManageController.java` | Convert to JSON; remove GSP renders |
| `ContactController` | Merge into `DataController` or keep separate | JSON CRUD only |
| `CollectionController` | Merge admin logic into services + `DataController` | GSP actions removed |
| `InstitutionController` | Same pattern | GBIF registration actions become REST |
| `DataProviderController` | Same pattern | IPT scan returns JSON |
| `DataResourceController` | Same pattern | Upload, contribution, rights become JSON |
| `DataHubController` | Same pattern | Membership management |
| `GbifController` | `GbifController.java` | Keep; JSON only |
| `IptController` | `IptController.java` | Keep; already REST |
| `DataFeedsController` | `DataFeedsController.java` | RIF-CS XML + RSS |
| `AdminController` | `AdminController.java` | Export endpoint |
| `SitemapController` | `SitemapController.java` | XML sitemap |
| `ProviderGroupController` (base) | Logic moves to `ProviderGroupService` | All edit/create/update/delete logic becomes service methods called by DataController |
| `LicenceController` | `LicenceController.java` | Simple CRUD |
| `ProviderCodeController` | `ProviderCodeController.java` | Simple CRUD |
| `ProviderMapController` | `ProviderMapController.java` | Simple CRUD |
| `EntityController` | Merge into `DataController` | showConsumers/showProviders |
| `TempDataResourceController` | Merge into `DataController` | Same patterns as other entities |

### Service Migration (25 services → 25 Spring services)

All services map 1:1. Key changes per service:
- Replace `def grailsApplication` → `@Autowired AppProperties config`
- Replace `def messageSource` → `@Autowired MessageSource messageSource`
- Replace `static transactional = false` → no annotation (Spring default is non-transactional)
- Replace `@grails.gorm.transactions.Transactional` → `@org.springframework.transaction.annotation.Transactional`
- Replace GORM calls with injected repository calls
- Replace `JSONBuilder` → Jackson `ObjectMapper` or return DTOs (auto-serialized)

### Interceptor → Spring Security Migration

Uses ALA's own Spring Security integration (`ala-ws-spring-security`, `ala-ws-security`) — same pattern as search-service.

| Grails Interceptor | Spring Boot Replacement |
|-------------------|----------------------|
| `TokenInterceptor` (order 0) | ALA's `AlaWebServiceAuthFilter` (from `ala-ws-spring-security`) registered before `BasicAuthenticationFilter`. Validates JWT via OIDC discovery URI |
| `PermissionInterceptor` (order 100) | `PermissionChecker` as a `HandlerInterceptor` that reads `@PermissionRequired` annotation and checks `Principal` for roles/scopes (using `AuthService` pattern from search-service) |
| `TempDataInterceptor` | Logic moves inline to controller method |

Security config (following search-service pattern):
```java
@Configuration
@EnableWebSecurity
@ComponentScan(basePackages = {"au.org.ala.ws.security", "au.org.ala.security.common"})
@EnableMethodSecurity
@Order(1)
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
            AlaWebServiceAuthFilter alaAuthFilter) {
        http
          .addFilterBefore(alaAuthFilter, BasicAuthenticationFilter.class)
          .cors(cors -> cors.configurationSource(corsConfig()))
          .csrf(AbstractHttpConfigurer::disable)
          .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
          // Per-endpoint auth checked by PermissionChecker + AuthService
        return http.build();
    }
}
```

Auth service (same pattern as search-service `AuthService`):
```java
@Service
public class AuthService {
    public boolean isAdmin(Principal principal) { /* check roles */ }
    public boolean hasRole(Principal principal, String role) { ... }
    public String getEmail(Principal principal) { ... }
    public String getUserId(Principal principal) { ... }
}
```

### Configuration Migration

| Grails Config | Spring Boot Config |
|--------------|-------------------|
| `grails-app/conf/application.yml` | `src/main/resources/application.properties` — port all properties (same format as search-service) |
| `grails.config.locations: [file:/data/collectory/config/...]` | `spring.config.import: optional:file:/data/collectory/config/collectory-config.properties` |
| `grailsApplication.config.biocacheServicesUrl` | `@Value("${biocacheServicesUrl}") String biocacheServicesUrl` (same pattern as search-service) |
| `grails.cors.*` | `@Bean CorsConfigurationSource corsConfigurationSource()` |
| `dataSource.*` (MySQL) | `spring.datasource.*` (PostgreSQL driver + URL) + `spring.jpa.hibernate.*` |
| `hibernate.cache.*` | `spring.jpa.properties.hibernate.cache.*` |
| `grails.plugin.databasemigration.updateOnStart` | `spring.flyway.locations=classpath:/flyway` + `spring.flyway.enabled=true` |

### Plugin Replacement Map

| Grails Plugin | Spring Boot Replacement |
|--------------|------------------------|
| `ala-auth` (Grails) | `ala-ws-spring-security:7.3.0-java21` + `ala-ws-security:7.3.0-java21` (Spring Boot native ALA auth) |
| `ala-ws-security-plugin` (Grails) | Same `ala-ws-spring-security` — provides `AlaWebServiceAuthFilter`, `AlaAuthClient`, `TokenInterceptor` |
| `ala-ws-plugin` (Grails) | ALA `WebService` class (from ala-ws-security) + Spring `RestClient` |
| `ala-admin-plugin` | Custom admin endpoints (minimal) |
| `ala-bootstrap5` | Not needed (React handles UI) |
| `ala-charts-plugin` | Not needed (React handles UI) |
| `interceptor-annotation-matcher` | Custom `HandlerInterceptor` with reflection |
| `audit-logging` | Custom JPA `@EntityListener` writing to `audit_log` table |
| `external-config` | `spring.config.import` |
| `openapi` | `springdoc-openapi-starter-webmvc-ui:2.5.0` (same as search-service) |
| `sentry` | `sentry-spring-boot-starter` |
| `database-migration` (Liquibase) | Flyway (`spring-boot-starter-flyway`) — aligned with search-service |
| `asset-pipeline` | Not needed (React has its own build) |
| `gsp` | Not needed (no server-rendered views) |
| `scaffolding` | Not needed |

### Bootstrap Logic
The `BootStrap.groovy` init logic maps to:
```java
@Component
class AppInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        // Validate mandatory config
        if (config.getGbifDefaultEntityCountry() == null) {
            throw new IllegalStateException("gbifDefaultEntityCountry is required");
        }
        // MessageSource basenames configured in application.yml
    }
}
```

## A4. Database Migration (MySQL → PostgreSQL)

The database moves from MySQL 8 to PostgreSQL 16. This requires:

### Schema migration approach
1. **Write Flyway migrations** (replacing Liquibase) with PostgreSQL-native DDL:
   - `BIT` → `BOOLEAN`
   - `TINYINT(1)` → `BOOLEAN`
   - `DATETIME` → `TIMESTAMP`
   - `LONGTEXT` / `MEDIUMTEXT` → `TEXT`
   - `AUTO_INCREMENT` → `SERIAL` or `GENERATED ALWAYS AS IDENTITY`
   - MySQL-specific string functions → PostgreSQL equivalents
   - Backtick quoting → double-quote quoting (or remove)
   - `ENGINE=InnoDB` → remove (not applicable)
2. **Data migration**: Use `pgloader` or a custom script to move data from MySQL to PostgreSQL
3. **JPA config**: `spring.datasource.driver-class-name: org.postgresql.Driver`, `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect`

### Key type mappings
| MySQL | PostgreSQL | JPA Annotation |
|-------|-----------|---------------|
| `VARCHAR(n)` | `VARCHAR(n)` | Same |
| `TEXT` / `LONGTEXT` | `TEXT` | `@Column(columnDefinition="text")` |
| `BIT(1)` / `TINYINT(1)` | `BOOLEAN` | `boolean` field |
| `DATETIME` | `TIMESTAMP` | `LocalDateTime` or `Instant` |
| `DOUBLE` | `DOUBLE PRECISION` | `Double` field |
| `BIGINT AUTO_INCREMENT` | `BIGSERIAL` | `@GeneratedValue(strategy=IDENTITY)` |

### What stays the same
- All table names, column names, relationships preserved
- Join tables (`data_provider_institution`, etc.) referenced via `@JoinTable`
- `Sequence` table + `IdGeneratorService` pattern kept (uses `@Transactional(propagation = REQUIRES_NEW)`)
- Flyway manages schema versioning (`spring.flyway.locations=classpath:/flyway`)
- JSON string fields can use PostgreSQL `JSONB` type for better querying (optional enhancement)

### Dependencies
- `org.postgresql:postgresql` (replaces `mysql:mysql-connector-java:8.0.33`)
- Testcontainers PostgreSQL module for tests

---

# PART B: FRONTEND — GSP to React SPA

## B1. Frontend Technology Stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Framework | React 19 + TypeScript 5.x | Type safety across 20+ domain types |
| Build | Vite 6 | Fast HMR, simple config, first-class TS |
| Routing | React Router v7 | URL state, lazy routes, loaders |
| Server state | TanStack Query v5 | Caching, refetch, pagination for `/ws/*` calls |
| Client state | Zustand | Lightweight store for map filters, UI state |
| Forms | React Hook Form + Zod | Schema-driven validation, dynamic fields |
| CSS | Bootstrap 5 + react-bootstrap | Preserves existing look |
| i18n | react-i18next | Converts existing Java `.properties` files |
| Maps | react-leaflet v5 | Direct port of current Leaflet usage |
| Charts | Recharts | Replaces ala-charts-plugin |
| HTTP | Axios | Interceptors for JWT, error handling |
| Auth | oidc-client-ts + react-oidc-context | OIDC PKCE flow for SPAs |
| Tables | TanStack Table v8 | Replaces DataTables.js |
| Testing | Vitest + React Testing Library + Playwright | Unit/component/E2E |

## B2. Frontend Project Structure

```
collectory-ui/                    # New directory at repo root
  package.json
  vite.config.ts
  tsconfig.json
  index.html
  scripts/
    convert-i18n.ts               # Build script: .properties -> JSON
  src/
    main.tsx                      # Entry: providers, i18n init
    App.tsx                       # Root with RouterProvider
    routes.tsx                    # All route definitions (lazy-loaded)
    api/
      client.ts                   # Axios instance + JWT interceptor
      types.ts                    # TS interfaces mirroring JPA entities
      endpoints/                  # One file per API domain
    auth/
      AuthProvider.tsx
      useAuth.ts
      ProtectedRoute.tsx
    i18n/
      index.ts
      locales/                    # Generated JSON (18 languages)
    hooks/                        # React Query hooks per domain
    layouts/
      AlaLayout.tsx               # ALA header/footer loading
      AdminLayout.tsx
    components/
      common/                     # Shared UI primitives
      contacts/                   # Contact CRUD components
      entities/                   # Entity edit forms
      public/                     # Public show pages + partials
      admin/                      # Admin entity views
      manage/                     # Self-service management + GBIF
      reports/                    # All 27 report views
      scaffold/                   # Simple CRUD (licence, providerCode, etc.)
      gbif/                       # GBIF admin pages
```

## B3. Route Structure

### Public (no auth)
| URL | Component | Replaces |
|-----|-----------|----------|
| `/` | `CollectionMap` | `public/map.gsp` + `map.js` |
| `/datasets` | `DatasetList` | `public/dataSets.gsp` + `datasets.js` |
| `/public/show/:id` | `EntityShowRouter` | Detects entity type by UID prefix |
| `/public/showCollection/:id` | `ShowCollection` | `public/showCollection.gsp` |
| `/public/showInstitution/:id` | `ShowInstitution` | `public/showInstitution.gsp` |
| `/public/showDataResource/:id` | `ShowDataResource` | `public/showDataResource.gsp` |
| `/public/showDataProvider/:id` | `ShowDataProvider` | `public/showDataProvider.gsp` |
| `/public/showDataHub/:id` | `ShowDataHub` | `public/showDataHub.gsp` |
| `/public/showTempDataResource/:id` | `ShowTempDataResource` | `public/showTempDataResource.gsp` |
| `/public/listInstitutions` | `InstitutionDirectory` | `public/listInstitutions.gsp` |

### Editor (ROLE_EDITOR + ROLE_ADMIN)
| URL | Component | Replaces |
|-----|-----------|----------|
| `/manage` | `ManageDashboard` | `manage/index.gsp` |
| `/manage/list` | `ManageList` | `manage/list.gsp` |
| `/manage/show/:id` | `ManageShow` | `manage/show.gsp` |
| `/:entityType/list` | `EntityList` | `*/list.gsp` |
| `/:entityType/show/:id` | `EntityAdminShow` | `*/show.gsp` |
| `/:entityType/edit/:id` | `EntityEditForm` | `shared/base.gsp` |
| `/:entityType/create` | `EntityEditForm` | Create mode |
| `/:entityType/:id/description` | `DescriptionForm` | `*/description.gsp` |
| `/:entityType/:id/contacts` | `ContactRoleEditor` | `shared/contactRole.gsp` |
| `/:entityType/:id/images` | `ImagesEditor` | `shared/images.gsp` |
| `/:entityType/:id/location` | `LocationForm` | `shared/location.gsp` |
| `/:entityType/:id/attributions` | `AttributionsEditor` | `shared/attributions.gsp` |
| `/dataResource/:id/contribution` | `ContributionForm` | `dataResource/contribution.gsp` |
| `/dataResource/:id/rights` | `RightsForm` | `dataResource/rights.gsp` |
| `/dataResource/:id/upload` | `FileUpload` | `dataResource/upload.gsp` |
| `/dataResource/:id/gbifUpload` | `GbifUploadForm` | `dataResource/gbifUpload.gsp` |
| `/dataResource/:id/consumers` | `ConsumersEditor` | `dataResource/consumers.gsp` |
| `/contact/*` | Contact CRUD pages | `contact/*.gsp` |
| `/manage/externalLoad*` | GBIF load wizard | `manage/externalLoad*.gsp` |
| `/manage/repatriate*` | Repatriation wizard | `manage/repatriate*.gsp` |
| `/manage/gbif*` | GBIF status pages | `manage/gbif*.gsp` |

### Admin (ROLE_ADMIN only)
| URL | Component | Replaces |
|-----|-----------|----------|
| `/reports/*` | 27 report components | `reports/*.gsp` |
| `/admin/gbif/*` | GBIF admin | `gbif/*.gsp` |
| `/licence/*` | Licence CRUD | `licence/*.gsp` |
| `/providerCode/*` | ProviderCode CRUD | `providerCode/*.gsp` |
| `/providerMap/*` | ProviderMap CRUD | `providerMap/*.gsp` |
| `/auditLogEvent/*` | Audit log views | `auditLogEvent/*.gsp` |

### Non-SPA routes (served by Spring Boot)
- `/ws/*` — All REST API endpoints
- `/rif-cs`, `/feed` — XML/RSS feeds
- `/eml/*` — EML XML
- `/sitemap*.xml` — Sitemaps
- `/data/*`, `/upload/*` — File serving

## B4. Reusable React Components

### Entity Edit Components (from `shared/` GSP templates)
| Component | Source | Purpose |
|-----------|--------|---------|
| `EntityEditForm` | `shared/base.gsp` | Polymorphic edit form |
| `DescriptionForm` | `*/description.gsp` | Description fields |
| `LocationForm` | `shared/location.gsp` | Address + map pin-drop |
| `ContactRoleEditor` | `shared/contactRole.gsp` | Entity-contact relationships |
| `ImagesEditor` | `shared/images.gsp` | Image upload + attribution |
| `AttributionsEditor` | `shared/attributions.gsp` | Attribution management |
| `ExternalIdEditor` | `shared/editExternalIdentifiers.gsp` | External IDs |
| `TaxonomyHintsEditor` | `shared/editTaxonomyHints.gsp` | Taxonomy hints |
| `GbifPanel` | `shared/gbif.gsp` | GBIF registration |
| `ChangeLog` | `shared/showChanges.gsp` | Audit trail |

### Public Partials
`RecordsMetrics`, `ContactsPanel`, `DataAccessPanel`, `DataLinksPanel`, `EditButton`, `ExternalIds`, `LoggerStats`, `TaxonTree`, `ImageGallery`

### Common Components
`Pagination`, `Breadcrumbs`, `SortableTable`, `FormattedText`, `EntityLink`, `RoleGuard`, `FlashMessage`, `HelpText`, `ConfirmDialog`

## B5. Authentication

A new **public OIDC client** (no secret, PKCE) must be registered with the ALA Auth service. The Spring Boot backend validates JWTs using `spring-security-oauth2-resource-server` against the same OIDC discovery URI. Frontend uses `oidc-client-ts` + `react-oidc-context`.

## B6. i18n

Build-time script converts 18 existing `messages*.properties` files to i18next JSON. All 1,537 message keys preserved. English loaded eagerly; others lazy-loaded.

## B7. ALA Header/Footer

`AlaLayout.tsx` fetches header/footer HTML fragments from `headerAndFooter.baseURL`, injects via `dangerouslySetInnerHTML`, executes embedded scripts, passes auth state via `window` globals.

---

# PART C: PHASED IMPLEMENTATION

## Phase 0: Project Scaffolding (Week 1-3)

### Backend scaffold
- [ ] Create `collectory-api/` Spring Boot 3.2 project (Maven, Java 21 — same parent as search-service)
- [ ] Port `application.yml` to `application.properties` (Spring Boot format, aligned with search-service)
- [ ] Write Flyway migrations (`V1__initial_schema.sql`) with PostgreSQL-native DDL (ported from existing MySQL schema)
- [ ] Configure Flyway (verify against PostgreSQL via Testcontainers)
- [ ] Set up Spring Security with ALA auth libs (`ala-ws-spring-security`, `ala-ws-security`) — same pattern as search-service `SecurityConfig.java`
- [ ] Set up config with `@Value` annotations (aligned with search-service patterns)
- [ ] Create `Dockerfile` (multi-stage build, same pattern as search-service)
- [ ] Create `docker-compose.yml` with PostgreSQL 17
- [ ] Set up Testcontainers for PostgreSQL integration tests
- [ ] Create data migration script (pgloader or custom) for MySQL → PostgreSQL transfer

### Frontend scaffold
- [ ] Create `collectory-ui/` Vite + React + TypeScript project
- [ ] Set up Axios client with JWT interceptor
- [ ] Implement `AuthProvider` with OIDC PKCE
- [ ] Register public OIDC client with ALA Auth service
- [ ] Implement `AlaLayout` (header/footer loading)
- [ ] Implement `ProtectedRoute`
- [ ] Build i18n conversion script; generate 18 locale JSONs
- [ ] Set up route skeleton with lazy loading

## Phase 1: Domain Layer + JPA Entities (Week 4-5)

- [ ] Port `ProviderGroup` trait → `@MappedSuperclass` with all 30+ fields
- [ ] Port all 6 entity types (Collection, Institution, DataProvider, DataResource, DataHub, TempDataResource) with JPA annotations
- [ ] Port embedded types (Address, Image)
- [ ] Port supporting entities (Contact, ContactFor, ExternalIdentifier, Attribution, Licence, ProviderCode, ProviderMap, AuditLogEvent, ActivityLog, Sequence)
- [ ] Create all 16 Spring Data JPA repository interfaces with derived query methods
- [ ] Add `@EntityGraph` annotations for eager-loading patterns (matching GORM criteria queries)
- [ ] Add `@Query` annotations for HQL/criteria equivalents
- [ ] Verify entity mappings against PostgreSQL schema (integration test with Testcontainers)
- [ ] Set up JPA auditing (`@CreatedDate`, `@LastModifiedDate`)
- [ ] Set up audit logging (Envers or custom listener to populate `audit_log` table)

## Phase 2: Core Services (Week 6-8)

- [ ] Port `IdGeneratorService` (UID generation with REQUIRES_NEW propagation)
- [ ] Port `ProviderGroupService` (entity lookup, contact management, authorization)
- [ ] Port `CrudService` (entity CRUD, JSON building → Jackson ObjectMapper)
- [ ] Port `CollectoryAuthService` (role checking, entity authorization)
- [ ] Port `ContactService` (extracted from controller logic)
- [ ] Port `ActivityLogService`
- [ ] Port `CollectionService`, `InstitutionService`, `DataResourceService`, `DataHubService`
- [ ] Port `MetadataService`, `IsoCodeService`, `ExternalIdentifierService`
- [ ] Port `MessageSourceCacheService` with Spring `@Cacheable`
- [ ] Write unit tests for each service

## Phase 3: REST API Controllers (Week 9-11)

- [ ] Port `DataController` — main `/ws/*` CRUD API (largest controller)
  - Replace `params` → `@RequestParam` / `@PathVariable`
  - Replace `render x as JSON` → return DTOs with Jackson
  - Replace `response.sendError` → throw exceptions with `@ExceptionHandler`
- [ ] Port `LookupController` — `/ws/lookup/*`
- [ ] Port `PublicController` — condensed, mapFeatures, biocacheRecords proxy
- [ ] Port `ReportsController` — convert all 27 actions to JSON endpoints
- [ ] Port `ManageController` — convert to JSON
- [ ] Port `GbifController`, `IptController`
- [ ] Port `DataFeedsController` — RIF-CS XML, RSS feed
- [ ] Port `AdminController` — export endpoint
- [ ] Port `SitemapController`
- [ ] Port `LicenceController`, `ProviderCodeController`, `ProviderMapController`
- [ ] Create `ConfigController` — new `/ws/config` endpoint
- [ ] Create `FileController` — `/data/*`, `/upload/*` file serving
- [ ] Create `SpaController` — catch-all forwarding to React's `index.html`
- [ ] Port `PermissionChecker` interceptor with `@PermissionRequired` annotation support
- [ ] Integration tests: verify every `/ws/*` endpoint returns identical JSON to Grails version

## Phase 4: Specialized Services (Week 12-13)

- [ ] Port `EmlImportService` (XML parsing with JAXB or Jackson XML)
- [ ] Port `EmlRenderService` (EML XML generation)
- [ ] Port `GbifService` (GBIF API integration, ZIP processing, thread pool)
- [ ] Port `GbifRegistryService` (GBIF registration/sync)
- [ ] Port `IptService` (IPT scanning)
- [ ] Port `ExternalDataService` (external resource adapters)
- [ ] Port `DataImportService`, `DataLoaderService`
- [ ] Port `RifCsService` (RIF-CS XML with caching)
- [ ] Port `SitemapService`
- [ ] Port async GBIF operations (Spring `@Async` replaces `@DelegateAsync`)

## Phase 5: Public React Pages (Week 14-17)

- [ ] `CollectionMap` — Port `map.js` to react-leaflet
- [ ] `DatasetList` — Port `datasets.js` faceted search + URL state
- [ ] `ShowCollection` — Tabbed view (overview, stats, metrics, images)
- [ ] `ShowInstitution`, `ShowDataResource`, `ShowDataProvider`, `ShowDataHub`, `ShowTempDataResource`
- [ ] `InstitutionDirectory`
- [ ] All public partials: `RecordsMetrics`, `ContactsPanel`, `DataAccessPanel`, `DataLinksPanel`, `LoggerStats`, `ImageGallery`, `TaxonTree`, `ExternalIds`, `EditButton`

## Phase 6: Admin React Pages (Week 18-21)

- [ ] `EntityList` — Generic sortable/filterable list
- [ ] `EntityAdminShow` — Admin detail view
- [ ] `EntityEditForm` — Polymorphic edit (from `shared/base.gsp`)
- [ ] `DescriptionForm`, `LocationForm`, `ContactRoleEditor`, `ImagesEditor`
- [ ] `AttributionsEditor`, `ProvidersEditor`, `ExternalIdEditor`, `TaxonomyHintsEditor`
- [ ] `ContributionForm` — Dynamic connection parameters
- [ ] `RightsForm`, `FileUpload`, `GbifUploadForm`, `ConsumersEditor`, `ImageMetadataForm`
- [ ] `ContactForm`, `ContactList`, `ContactCard`

## Phase 7: Manage + Reports React Pages (Week 22-25)

- [ ] `ManageDashboard`, `ManageList`, `ManageShow`
- [ ] GBIF load wizard: `GbifExternalLoad` → `GbifLoadReview` → `GbifLoadStatus`
- [ ] `GbifCountryStatus`, `GbifDatasetDownload`, `GbifDatasetStatus`
- [ ] `Repatriate`, `RepatriateReview`
- [ ] `ReportsDashboard` + all 27 report components
- [ ] Scaffold CRUD: Licence, ProviderCode, ProviderMap, AuditLogEvent
- [ ] GBIF admin: HealthCheck, SyncAllResources
- [ ] Error/NotFound pages

## Phase 8: Integration, Testing & Cutover (Week 26-28)

- [ ] API parity testing: run both Grails and Spring Boot, compare responses for every endpoint
- [ ] End-to-end Playwright tests for all critical flows
- [ ] Performance testing (Spring Boot startup, response times)
- [ ] Docker image build + K8s deployment config
- [ ] Side-by-side deployment with traffic splitting
- [ ] Data migration verification (same MySQL database, zero changes)
- [ ] Cutover: switch DNS/load balancer to Spring Boot + React
- [ ] Decommission Grails application

---

## Verification Strategy

### Backend verification
- **Unit tests**: Every service method with mocked repositories
- **Integration tests**: Every REST endpoint with Testcontainers MySQL
- **Parity tests**: Script that calls both Grails and Spring Boot endpoints, diffs JSON responses
- **Database**: Data migrated from MySQL to PostgreSQL using pgloader; verified with row count + checksum comparison

### Frontend verification
- **Component tests**: React Testing Library for each page
- **E2E tests**: Playwright for critical flows (map, datasets, CRUD, auth, GBIF wizard)
- **Visual comparison**: Side-by-side screenshots of GSP vs React pages

### Acceptance criteria
- Every `/ws/*` endpoint returns identical JSON structure and data
- Every UI page has equivalent functionality
- Auth flow (login, role-gated access, logout) works end-to-end
- File upload/download works
- GBIF sync/registration workflows complete successfully
- All 18 i18n locales load correctly

---

## Critical Files Reference

| Purpose | Current Path |
|---------|-------------|
| URL mappings (all routes) | `grails-app/controllers/.../UrlMappings.groovy` |
| REST API controller | `grails-app/controllers/.../DataController.groovy` |
| Public controller | `grails-app/controllers/.../PublicController.groovy` |
| Reports controller | `grails-app/controllers/.../ReportsController.groovy` |
| Entity admin base | `grails-app/controllers/.../ProviderGroupController.groovy` |
| Manage controller | `grails-app/controllers/.../ManageController.groovy` |
| ProviderGroup trait | `src/main/groovy/.../ProviderGroup.groovy` |
| Domain classes (20) | `grails-app/domain/au/org/ala/collectory/` |
| Services (25) | `grails-app/services/au/org/ala/collectory/` |
| Auth interceptors | `grails-app/controllers/.../*Interceptor.groovy` |
| CRUD service | `grails-app/services/.../CrudService.groovy` |
| Auth service | `grails-app/services/.../CollectoryAuthService.groovy` |
| TagLib (component logic) | `grails-app/taglib/.../CollectoryTagLib.groovy` |
| App config | `grails-app/conf/application.yml` (482 lines) |
| Build config | `build.gradle` |
| DB migrations | `grails-app/migrations/changelog.xml` + SQL files |
| Supporting classes (24) | `src/main/groovy/au/org/ala/collectory/` |
| Existing tests (2) | `src/test/groovy/.../EmlImportServiceSpec.groovy`, `IptServiceSpec.groovy` |
| JS (datasets logic) | `grails-app/assets/javascripts/datasets.js` |
| JS (map logic) | `grails-app/assets/javascripts/map.js` |
| JS (utils) | `grails-app/assets/javascripts/collectory.js` |
| i18n (18 files) | `grails-app/i18n/messages*.properties` |
