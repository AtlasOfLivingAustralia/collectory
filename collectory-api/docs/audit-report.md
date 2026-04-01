# Collectory Migration Audit Report

**Date:** 2026-03-19  
**Scope:** Complete comparison of Grails 6.2.2 (MySQL) → Spring Boot 3.2.5 (PostgreSQL)  
**Auditor:** Automated multi-phase audit  

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 7 |
| HIGH | 16 |
| MEDIUM | 26 |
| LOW | 17 |
| **Total** | **66** |

CRITICAL and HIGH issues will prevent correct operation or lose/corrupt data. MEDIUM issues cause
behavioral regressions that affect callers. LOW issues are minor deviations unlikely to impact production.

---

## CRITICAL Issues

### C1 — `ProviderMap` join table split missing (Schema / Entity)

**Files:** `collectory-api/src/main/resources/flyway/V1__initial_schema.sql`,
`collectory-api/src/main/java/au/org/ala/collectory/domain/ProviderMap.java`

Grails creates two separate join tables for `ProviderMap`: `provider_map_collection_codes` and
`provider_map_institution_codes`. The V1 Flyway migration creates only the original single
`provider_map_provider_code` table. The Java entity `ProviderMap.java` must declare two separate
`@JoinTable` entries with these exact names. If the schema and entity are not aligned the
application **will not start** (schema validation failure) and existing data will not map correctly.

**Action required:** Verify that `ProviderMap.java` has separate `@JoinTable` annotations with
names `provider_map_collection_codes` and `provider_map_institution_codes`, and that both tables
exist in V1 Flyway SQL.

---

### C2 — `V2__5_1_0_updates.sql` missing three MySQL migration scripts (Schema)

**Files:** `collectory-api/src/main/resources/flyway/V2__5_1_0_updates.sql`,
`grails-app/migrations/5.1.0.sql`, `grails-app/migrations/5.1.0.1.sql`,
`grails-app/migrations/5.1.0.3.sql`

Three MySQL migration scripts are not represented in the Flyway V2 script:

- **`5.1.0.sql`** — creates `external_identifier` join tables per entity
  (`collection_external_identifiers`, `institution_external_identifiers`,
  `data_provider_external_identifiers`, `data_resource_external_identifiers`)
- **`5.1.0.1.sql`** — creates `data_link` join tables
  (`collection_data_links`, `institution_data_links`)
- **`5.1.0.3.sql`** — creates `data_hub_external_identifiers` join table

Without these tables, `@ManyToMany` associations on `ExternalIdentifier` and data-link
relationships will **fail at runtime** with table-not-found errors.

**Note:** V1 does contain a `data_link` table DDL (line 490), so the base `data_link` entity table
itself is present. The missing piece is the join tables that link it to collections/institutions.

---

### C3 — Flyway locations misconfigured (Config)

**File:** `collectory-api/src/main/resources/application.properties`

```
spring.flyway.locations=classpath:db/migration
```

Flyway migration scripts are located at `src/main/resources/flyway/`, which resolves to
`classpath:flyway/` at runtime. The property points to `classpath:db/migration`. Flyway will find
**no migrations** and the database schema will not be created.

**Fix:** Change to `spring.flyway.locations=classpath:flyway`

---

### C4 — `PermissionChecker.java` role check not implemented (Security)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/security/PermissionChecker.java`

The role/permission check method always returns `true` after confirming a principal is present.
`// TODO` stubs remain in the file. Any authenticated user can perform any write operation
regardless of role. This is a critical security gap.

---

### C5 — `SecurityConfig.java` permits all requests (Security)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/config/SecurityConfig.java`

```java
anyRequest().permitAll()
```

No URL-level security is enforced. Unauthenticated callers can invoke any endpoint including
admin, manage, and write operations. All Grails interceptor URL patterns are absent.

---

### C6 — JWT/Token authentication filter absent (Security)

**Files:** `grails-app/interceptors/au/org/ala/collectory/TokenInterceptor.groovy` (legacy),
no Java equivalent exists

Grails used `TokenInterceptor.groovy` to validate M2M JWT tokens for service-to-service calls
(e.g. biocache → collectory). No Spring Security filter implementing equivalent JWT validation
exists in the Java codebase. Machine-to-machine authentication is entirely missing.

---

### C7 — `gbifCountryToAttribute` NOT NULL constraint violation (Entity)

**Files:** `collectory-api/src/main/java/au/org/ala/collectory/domain/Institution.java`,
`collectory-api/src/main/java/au/org/ala/collectory/domain/DataProvider.java`

Both entities declare `@Column(nullable = false)` on `gbifCountryToAttribute`. The MySQL schema
defines this column as `VARCHAR(3) DEFAULT NULL` (nullable). Any existing row with a NULL value
will fail JPA validation on load or save.

---

## HIGH Issues

### H1 — `DataLoaderService.groovy` has no Java equivalent (Service)

**File:** `grails-app/services/au/org/ala/collectory/DataLoaderService.groovy`

Contains: `importDataProviders`, `importDataResources`, `importJson` (full JSON bootstrap import),
`loadSupplementaryData`, `loadBCIData`. These are called from `AdminController` endpoints
(`/admin/importJson`, `/admin/importDataProviders`, `/admin/importDataResources`). Without this
service those admin import endpoints cannot function, blocking bootstrap and data migration
operations.

---

### H2 — `GbifRegistryService` institution resource sync not implemented (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/GbifRegistryService.java`

`syncDataResourcesForProviderGroup` logs: *"Institution resource syncing not yet fully
implemented."* Groovy iterates `dp.providerDataResource` for both institutions and collections.
GBIF sync will be incomplete for institution-owned resources.

---

### H3 — `CollectoryAuthService` missing child-entity expansion for `authorisedForUser` (Security/Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/CollectoryAuthService.java`

Groovy adds all child entities (e.g. collections belonging to an institution) to the authorised
list. Java only adds the directly matched entity from `ContactFor`. Users who are authorised for
an institution will not be authorised for its collections.

---

### H4 — `DataController` missing `isPrivate` filter on DataResource list (Controller)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/controller/DataController.java`

Groovy: `DataResource.findAllByIsPrivate(false)`. Java returns all data resources including
private ones in the public `/ws/dataResource` listing. Private resource metadata is exposed.

---

### H5 — `DataController` missing generic query-param filter on entity list (Controller)

Groovy `getEntity()` calls `filter(list)` which filters any entity list by matching any query
parameter name against an entity property. Java ignores query parameters entirely for list
endpoints. API clients relying on this filtering behavior (e.g. `?resourceType=records`) will
receive unfiltered results.

---

### H6 — `DataController` missing `connectionParameters` endpoint (Controller)

Groovy endpoint: `GET /ws/dataResource/$uid/connectionParameters`  
Returns connection parameters for a data resource. No Java equivalent exists. Biocache and other
services use this endpoint to discover how to harvest a resource.

---

### H7 — `DataController` missing `notification` endpoint (Controller)

Groovy endpoint: `POST /ws/notify`  
Used by external services to trigger notifications. No Java equivalent exists.

---

### H8 — `DataController` missing `resolveNames` endpoint (Controller)

Groovy endpoint: `GET /ws/resolveNames/$uids`  
Used to batch-resolve UIDs to names. No Java equivalent exists.

---

### H9 — `ReportsController` URL path changed from `/reports/*` to `/ws/reports/*` (Controller)

All existing callers using `/reports/changes`, `/reports/notifications` will receive 404.
Breaking change for any service pointing at the old path.

---

### H10 — `AdminController` URL path changed from `/admin/*` to `/ws/admin/*` (Controller)

Breaking change for all existing admin tooling pointing at `/admin/*`.

---

### H11 — `ManageController` URL path changed from `/manage/*` to `/ws/manage/*` (Controller)

Breaking change for UI and any direct callers.

---

### H12 — `DataFeedsController` missing `/ws/rif-cs` URL (Controller)

Groovy maps both `/rif-cs` and `/ws/rif-cs`. Java only maps `/rif-cs`. Callers using the `/ws/`
prefixed path get a 404.

---

### H13 — `PublicController` `downloadDataSets` CSV format changed (Controller)

Groovy outputs 11 columns; Java outputs 8 columns. Breaking change for downstream consumers
parsing the CSV.

---

### H14 — `LookupController` missing `findResourceByGuid` URL compatibility (Controller)

Groovy: `GET /ws/lookup/findResourceByGuid?guid=`  
Java: `GET /ws/find/guid?guid=`  
Old URL path not mapped; callers get 404.

---

### H15 — `InstitutionService` missing `relatedDataProviders`/`relatedDataResources` in summary (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/InstitutionService.java`

Groovy `buildSummary` iterates `institution.providerDataProviders` and
`institution.providerDataResources` and adds them to the summary. Java omits both lists. The
`/ws/institution/{uid}` summary response is missing these fields.

---

### H16 — `CollectionService` missing `relatedDataProviders`/`relatedDataResources` in summary (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/CollectionService.java`

Same issue as H15. Groovy builds `relatedDataProviders` and `relatedDataResources` from
`collection.providerDataResources + collection.providerDataProviders`. Java omits both.
Also missing: `derivedInstCodes` and `derivedCollCodes` (institution/collection codes for lookup).

---

## MEDIUM Issues

### M1 — `DataImportService` not calling `iptService.syncContacts` after EML import (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/DataImportService.java`

Groovy calls `iptService.syncContacts(dataResource, contacts, primaryContacts, ...)` after
extracting contacts from EML. Java calls `emlImportService.extractContactsFromEml(is, dataResource)`
but does not call `syncContacts`. Contacts extracted from reimported archives will not be
synchronised (added/updated/removed) to the database.

---

### M2 — `GbifRegistryService.writeCSVReportForGBIF` hardcodes `isVerified` as `"no"` (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/GbifRegistryService.java`

Line ~619: `"no" // isVerified not currently modelled`. GBIF CSV export will always report
datasets as unverified regardless of actual state.

---

### M3 — `EmlRenderService` missing `associatedParty` for Institution/Collection (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/EmlRenderService.java`

Java has a `// TODO` comment: *"Institution and Collection should list providerDataResources and
providerDataProviders as associatedParty with role 'publisher'."* EML output for institutions and
collections is missing publisher associations.

---

### M4 — `EmlRenderService` missing `electronicMailAddress` for ALA contact in EML (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/EmlRenderService.java`

`writeAlaAssociatedParty` has `// TODO: Add electronicMailAddress`. ALA contact email is absent
from EML output, which may affect GBIF metadata validation.

---

### M5 — `CrudService` missing `hasMappedCollections` in `readDataResource` (Service)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/service/CrudService.java`

Groovy `readDataResource` includes `hasMappedCollections`. Java omits it. Clients checking this
field will see it absent.

---

### M6 — `CrudService` missing `hubMembership` in `readDataResource`, `readInstitution`, `readCollection` (Service)

`hubMembership` array is not populated in any of these three read methods. Clients lose the
ability to see which data hubs an entity belongs to.

---

### M7 — `CrudService` missing `publicArchiveUrl`/`gbifArchiveUrl` in `readDataResource` (Service)

Both archive URL fields absent from Java `readDataResource` response.

---

### M8 — `CrudService` missing `verified` field in `readDataResource` (Service)

Field present in Groovy response, absent in Java.

---

### M9 — `CrudService` missing `linkedRecordProviders` in `readInstitution`/`readCollection` (Service)

Field absent from both institution and collection read responses.

---

### M10 — `CrudService` missing `parentInstitutions`/`childInstitutions` in `readInstitution` (Service)

Groovy includes both fields. Java omits them. Breaks hierarchy traversal via API.

---

### M11 — `CrudService` missing `recordsProviderMapping` in `readCollection` (Service)

Field absent from collection read response.

---

### M12 — `CrudService` `numRecords`/`numRecordsDigitised` sentinel handling differs (Service)

Groovy outputs the string `'not known'` when the value is `-1`. Java outputs the raw integer
`-1`. API clients testing `=== 'not known'` will break.

---

### M13 — `CrudService` missing `geographicRange` sub-object in `readCollection` (Service)

Groovy builds a `geographicRange` sub-object containing bounding box coordinates. Java includes
flat bounding box properties but not the nested object structure.

---

### M14 — `CrudService` missing `attributions` in `readCollection`/`readDataResource` (Service)

Attribution list absent from both responses.

---

### M15 — `CrudService` missing `networkMembership` resolved names (Service)

Groovy expands network membership acronyms into full objects with logos. Java serialises the raw
JSON string.

---

### M16 — `CrudService` `readTempDataResource` missing `type` field (Service)

Field present in Groovy, absent in Java.

---

### M17 — `ManageController.show` returns wrong `changes` data (Controller)

Java queries for any 20 audit events; Groovy filters by entity UID. Wrong changes are returned
for any entity detail page.

---

### M18 — `ManageController` missing `latestMod` field in `authorisedForUser` response (Controller)

Groovy includes `latestMod`; Java does not.

---

### M19 — `DataController` missing `count` `groupBy` functionality (Controller)

Groovy `count` endpoint supports `?groupBy=propertyName` returning per-value counts. Java returns
only `{"total": N}`. Existing dashboards using groupBy will break.

---

### M20 — `DataController` missing JSONP/callback support (Controller)

Groovy wraps responses in callback when `?callback=` is present. Java does not. Browser-based
JSONP callers will break.

---

### M21 — `DataController` missing ETag caching (Controller)

Groovy sets `ETag` and `Last-Modified` headers; Java does not. Cache-friendly clients will make
unnecessary requests.

---

### M22 — `DataController` missing `eml` endpoint (Controller)

Groovy endpoints: `GET /eml/$id?` and `GET /ws/eml/$id?`  
No Java equivalent. EML download links will 404.

---

### M23 — `DataController` missing `codeMapDump` endpoint (Controller)

Groovy: `GET /ws/codeMapDump`  
No Java equivalent.

---

### M24 — `DataController` missing `getFragment` endpoint (Controller)

Groovy: `GET /ws/fragment/$entity/$uid`  
No Java equivalent.

---

### M25 — `DataController` missing `institutionsForDataHub`/`collectionsForDataHub` endpoints (Controller)

Groovy endpoints return members of a data hub. No Java equivalents.

---

### M26 — `contact.user_last_modified` NOT NULL constraint absent in Flyway V1 (Schema)

MySQL schema has `NOT NULL` on this column; Flyway V1 omits the constraint. Data consistency
differs between engines.

---

## LOW Issues

### L1 — `ProviderGroupService.getSuitableFor()` returns empty map (Service)

Java returns an empty map placeholder. Groovy reads from config with i18n message resolution.
The `suitableFor` metadata in collection/institution summaries will be empty.

---

### L2 — `GbifService` uses `FileUtils.copyFile` instead of `renameTo` for zip creation (Service)

Minor behavioral difference: `renameTo` is atomic on same filesystem; `copyFile` is not. Under
high concurrency the zip file could be read in a partially-written state.

---

### L3 — `ActivityLogRepository` missing `findAllByUser` method (Repository)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/repository/ActivityLogRepository.java`

Used by `ReportsController.notifications` to filter by user (specifically to find
`notify-service` notifications). Without this method the notifications report cannot be filtered.

---

### L4 — `TempDataResourceRepository` missing paginated criteria query (Repository)

**File:** `collectory-api/src/main/java/au/org/ala/collectory/repository/TempDataResourceRepository.java`

Groovy `TempDataResourceController` uses a criteria query with filtering and pagination. Java
repository has only simple finders. Paginated admin listing of temp data resources is broken.

---

### L5 — `collection.latitude/longitude` NOT NULL vs nullable mismatch (Schema/Entity)

MySQL schema and Flyway V1 define these as `NOT NULL`. Java entity has nullable `Double`. Existing
data with `0.0` sentinel values must be converted to NULL during data migration, or the NOT NULL
constraint must be removed in Flyway.

---

### L6 — `contact_for` timestamp columns nullability mismatch (Schema/Entity)

MySQL has `NOT NULL` on `date_created` and `date_last_modified` in `contact_for`. Java
`ContactFor.java` has plain `LocalDateTime` with no `@CreatedDate`/`@LastModifiedDate`. New
inserts will fail the NOT NULL constraint unless auditing is enabled.

---

### L7 — `TempDataResource` missing `@Index` annotation for `uid` (Entity)

MySQL has `KEY uid_idx (uid)`. Flyway V1 includes this index. Java `@Table` annotation has no
corresponding `@Index`. Not a correctness issue but performance will degrade on uid lookups.

---

### L8 — `LookupController` missing CSV/TSV formats for citations (Controller)

Groovy supports `?type=csv` and `?type=tsv` for the citations endpoint. Java only returns JSON.

---

### L9 — `LookupController` missing `citations` `include`/`"all"` support (Controller)

Groovy supports filtering citations by `include` param or returning all with `include=all`. Java
ignores this parameter.

---

### L10 — `LookupController` missing `listResources` endpoint (Controller)

Groovy: `GET /ws/lookup/listResources`  
No Java equivalent.

---

### L11 — `LookupController` missing `/lookup/inst/{inst}/coll/{coll}` without `/ws` prefix (Controller)

Groovy maps both prefixed and non-prefixed versions. Java only maps the `/ws/` prefixed version.

---

### L12 — `LookupController` missing `summary` fallback for non-UID IDs (Controller)

Groovy `summary` accepts LSID, database ID, or acronym in addition to UID. Java only handles UID.

---

### L13 — `LookupController` missing `TempDataResource` lookup in `summary` for `drt` UIDs (Controller)

Groovy checks `TempDataResource` when uid starts with `drt`. Java does not.

---

### L14 — `GbifController.healthCheckLinked` not fully implemented (Controller)

Java calls the same code as `healthCheck`. Groovy fetches live biocache data to check linked
records. Health check results will be inaccurate.

---

### L15 — `GbifController.syncAllResources` changed from GET to POST (Controller)

Old callers using `GET /admin/gbif/sync` receive 405 Method Not Allowed.

---

### L16 — `MessagesController.groovy` has no Java equivalent (Controller)

Groovy exports i18n properties at `GET /messages/i18n` as `text/plain`. Used by some front-end
clients for localised label lookup. No Java equivalent.

---

### L17 — `DataLoaderService` / `InstitutionCodeLoaderService` absent (Service)

No Java equivalent for `InstitutionCodeLoaderService` (AFD institution code lookup). This is only
needed during BCI data bootstrap, not at runtime, so operational impact is limited to initial
data load tooling.

---

## Repository Status Summary

| Repository | Status |
|---|---|
| `ContactForRepository` | Complete — all required methods present |
| `AuditLogEventRepository` | Complete — `findAllByUri` present |
| `ActivityLogRepository` | **MISSING** `findAllByUser` (see L3) |
| `TempDataResourceRepository` | **MISSING** paginated criteria query (see L4) |
| `DataResourceRepository` | Complete |
| All others | Complete |

---

## Schema Migration Status Summary

| Issue | Status |
|---|---|
| `provider_map_collection_codes` / `provider_map_institution_codes` tables | **CRITICAL — C1** |
| `external_identifier` join tables (5.1.0.sql) | **CRITICAL — C2** |
| `data_link` join tables (5.1.0.1.sql) | **CRITICAL — C2** |
| `data_hub_external_identifiers` (5.1.0.3.sql) | **CRITICAL — C2** |
| `data_link` base table | Present in V1 (line 490) |
| `contact.user_last_modified` NOT NULL | MEDIUM — M26 |
| `contact_for` timestamp NOT NULL | LOW — L6 |
| `collection.latitude/longitude` NOT NULL vs nullable | LOW — L5 |
| Flyway `locations` property misconfigured | **CRITICAL — C3** |

---

## Services with No Java Equivalent

| Groovy Service | Java Equivalent | Assessment |
|---|---|---|
| `DataLoaderService.groovy` | None | HIGH gap — H1 |
| `InstitutionCodeLoaderService.groovy` | None | LOW gap — L17 |
| `AsyncGbifRegistryService.groovy` | Not needed | Replaced by `@Async` in GbifRegistryService |
| `CollectoryAuthService.groovy` | Present | Partially ported — H3 |

---

## Services Verified Equivalent (No Gaps)

- `RifCsService` — equivalent
- `ExternalIdentifierService` — equivalent
- `ExternalDataService` — equivalent
- `SitemapService` — equivalent (minor: uses `LocalDateTime.now()` instead of entity `lastUpdated Date`)
- `IsoCodeService` — equivalent
- `MessageSourceCacheService` — equivalent
- `ActivityLogService` — equivalent
- `GbifService` — equivalent (minor: file copy atomicity — L2)
- `IptService` — equivalent
- `EmlImportService` — equivalent
- `MetadataService` — equivalent
- `DataHubService` — equivalent
- `DataImportService` — equivalent except M1
- `ProviderGroupService` — equivalent except L1

---

## Recommended Fix Priority

1. **Immediate (blocks startup/data integrity):** C1, C2, C3, C7
2. **Before any traffic:** C4, C5, C6 (security)
3. **Before API consumers:** H1–H16
4. **Before full parity:** M1–M26
5. **Nice-to-have:** L1–L17
