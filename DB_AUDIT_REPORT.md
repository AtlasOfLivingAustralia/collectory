# Database Audit Report
## MySQL 8 → PostgreSQL 17 / Grails GORM → Spring Boot JPA

**Date:** 2026-03-25
**Scope:** Full schema + data comparison between MySQL dump (`/Users/jav014/new-collecroty/dump.sql`, MySQL 8.0.45) and live PostgreSQL 17 (`localhost:5432`, db: `collectory`), plus Grails GORM domain models vs Spring Boot JPA entities.

---

## 1. Record Count Comparison

All 16 data tables have identical row counts between MySQL and PostgreSQL.

| Table                  | MySQL COUNT | PostgreSQL COUNT | Match? |
|------------------------|-------------|------------------|--------|
| activity_log           | 7,803,795   | 7,803,795        | ✅     |
| attribution            | 49          | 49               | ✅     |
| audit_log              | 355,310     | 355,310          | ✅     |
| collection             | 217         | 217              | ✅     |
| contact                | 1,073       | 1,073            | ✅     |
| contact_for            | 860         | 860              | ✅     |
| data_hub               | 10          | 10               | ✅     |
| data_link              | 275         | 275              | ✅     |
| data_provider          | 1,700       | 1,700            | ✅     |
| data_resource          | 14,619      | 14,619           | ✅     |
| external_identifier    | 161         | 161              | ✅     |
| institution            | 910         | 910              | ✅     |
| licence                | 20          | 20               | ✅     |
| provider_code          | 216         | 216              | ✅     |
| provider_map           | 160         | 160              | ✅     |
| temp_data_resource     | 10,727      | 10,727           | ✅     |

---

## 2. Table-Level Differences

### Tables in MySQL but NOT in PostgreSQL

| Table                        | Verdict |
|------------------------------|---------|
| `DATABASECHANGELOG`          | ✅ Expected — Liquibase replaced by Flyway (`flyway_schema_history`) |
| `DATABASECHANGELOGLOCK`      | ✅ Expected — same reason |
| `address`                    | ✅ Expected — embedded into parent tables via GORM `static embedded = ['address']` |
| `image`                      | ✅ Expected — embedded into parent tables |
| `collection_command`         | ✅ OK — table was empty in MySQL (0 rows), not migrated |
| `api_key_log`                | ⚠️ **DATA LOSS** — had 2 rows in MySQL (ids 12 and 13, last API calls May/June 2025), NOT migrated to PostgreSQL |

### Tables in PostgreSQL but NOT in MySQL

| Table                          | Verdict |
|--------------------------------|---------|
| `flyway_schema_history`        | ✅ Expected — replaces Liquibase |
| `provider_map_collection_codes`  | ⚠️ **DATA LOSS** — see section 5 |
| `provider_map_institution_codes` | ⚠️ **DATA LOSS** — see section 5 |

---

## 3. Schema Discrepancies (Column-Level)

### `institution` table

| Column              | MySQL                  | PostgreSQL             | Issue |
|---------------------|------------------------|------------------------|-------|
| `name`              | `VARCHAR(1024) NULL`   | `VARCHAR(1024) NOT NULL` | ⚠️ Nullability tightened |
| `focus`             | `VARCHAR(2048)`        | `TEXT`                 | ✅ Compatible (wider) |
| `notes`             | `VARCHAR(2048)`        | `TEXT`                 | ✅ Compatible |
| `network_membership`| `VARCHAR(256)`         | `TEXT`                 | ✅ Compatible |
| `taxonomy_hints`    | `VARCHAR(1024)`        | `TEXT`                 | ✅ Compatible |
| `keywords`          | `VARCHAR(255)`         | `TEXT`                 | ✅ Compatible |

### `collection` table

| Column               | MySQL                  | PostgreSQL               | Issue |
|----------------------|------------------------|--------------------------|-------|
| `name`               | `VARCHAR(1024) NULL`   | `VARCHAR(1024) NOT NULL` | ⚠️ Nullability tightened |
| `kingdom_coverage`   | `VARCHAR(1024)`        | `TEXT`                   | ✅ Compatible |
| `scientific_names`   | `VARCHAR(2048)`        | `TEXT`                   | ✅ Compatible |
| `keywords`           | `VARCHAR(1024)`        | `TEXT`                   | ✅ Compatible |
| `focus`              | `LONGTEXT`             | `TEXT`                   | ✅ Compatible |
| `notes`              | `VARCHAR(2048)`        | `TEXT`                   | ✅ Compatible |

### `data_resource` table

| Column                   | MySQL                         | PostgreSQL                              | Issue |
|--------------------------|-------------------------------|-----------------------------------------|-------|
| `status`                 | `VARCHAR(255) NULL`           | `VARCHAR(45) NOT NULL DEFAULT 'identified'` | ⚠️ **Both nullability and length differ** — MySQL: nullable, up to 255 chars; PG: NOT NULL, max 45 chars |
| `citation`               | `VARCHAR(4096)`               | `TEXT`                                  | ✅ Compatible |
| `notes`                  | `LONGTEXT`                    | `TEXT`                                  | ✅ Compatible |
| `geographic_description` | `LONGTEXT`                    | `TEXT`                                  | ✅ Compatible |
| `network_membership`     | `VARCHAR(256)`                | `TEXT`                                  | ✅ Compatible |
| `taxonomy_hints`         | `LONGTEXT`                    | `TEXT`                                  | ✅ Compatible |

### `data_provider` table

| Column                  | MySQL       | PostgreSQL     | Issue |
|-------------------------|-------------|----------------|-------|
| `pub_short_description` | `LONGTEXT`  | `VARCHAR(100)` | ⚠️ **Type regression** — MySQL allows unlimited text; PG truncates at 100 chars |
| `focus`                 | `VARCHAR(2048)` | `TEXT`     | ✅ Compatible |
| `notes`                 | `VARCHAR(2048)` | `TEXT`     | ✅ Compatible |

### `audit_log` table

| Column                  | MySQL           | PostgreSQL       | Issue |
|-------------------------|-----------------|------------------|-------|
| `persisted_object_id`   | `BIGINT NULL`   | `VARCHAR(255) NULL` | ⚠️ **Type changed** — intentional per V4 migration comment ("flexibility"), values stored as numeric strings |
| `new_value`             | `VARCHAR(2048)` | `VARCHAR(2048)`  | ✅ Fixed by V4 migration (was VARCHAR(255) in V1) |
| `old_value`             | `VARCHAR(2048)` | `VARCHAR(2048)`  | ✅ Fixed by V4 migration |

### `contact_for` table

| Column              | MySQL              | PostgreSQL          | Issue |
|---------------------|--------------------|---------------------|-------|
| `date_created`      | `datetime NOT NULL` | `TIMESTAMP NULL`   | ⚠️ Nullability relaxed (V3 migration did this intentionally) — all 860 existing rows have non-null values |
| `date_last_modified`| `datetime NOT NULL` | `TIMESTAMP NULL`   | ⚠️ Same — all 860 existing rows have non-null values |

### `sequence` table

| Column   | MySQL                     | PostgreSQL                  | Issue |
|----------|---------------------------|-----------------------------|-------|
| `name`   | `VARCHAR(45) NULL`        | `VARCHAR(255) NOT NULL`     | ⚠️ Nullability tightened AND length widened |
| `prefix` | `VARCHAR(5) NULL`         | `VARCHAR(255) NOT NULL`     | ⚠️ Same — MySQL allows NULL, max 5 chars; PG NOT NULL, max 255 chars |

### `contact`, `attribution`, `licence`, `external_identifier`, `provider_code` tables

All columns match in type, length, and nullability between MySQL and PostgreSQL. ✅

---

## 4. Grails Domain → JPA Entity Discrepancies

### 4.1 `TempDataResource` — `type` field is a phantom `@Column`

- **Grails**: `type` is declared as `static transients = [..., 'type']` — it is a **computed property** (`getType()` returns `'Production'` if `prodUid != null`, else `'Draft'`). It is **never persisted** to the database.
- **Java entity** (`TempDataResource.java:107`): `@Column(length = 45) private String type` — annotated as a real persisted column.
- **Actual PostgreSQL schema**: `type` column **does NOT exist** in the `temp_data_resource` table.
- **Verdict**: ⚠️ **Orphaned field** — `@Column` on a field with no corresponding DB column. Hibernate may throw a `SchemaManagementException` on startup (if DDL validation is on), or silently fail on insert/update. Should be annotated `@Transient` or removed.

### 4.2 `AuditLogEvent` — `new_value`/`old_value` length annotation mismatch

- **Java entity** (`AuditLogEvent.java:43,46`): `@Column(name = "new_value", length = 255)` and `@Column(name = "old_value", length = 255)`
- **Actual PostgreSQL schema**: both columns are `VARCHAR(2048)` (widened by V4 migration)
- **Verdict**: ⚠️ **Entity out of sync with DB** — length annotation says 255, actual DB column is 2048. No runtime error (Hibernate trusts the DB for reads/writes), but `@Size` or `@Column` bean validation would incorrectly reject values between 256–2048 chars.

### 4.3 `ContactFor` — `dateCreated`/`dateLastModified` not auto-managed

- **Grails**: `dateCreated = new Date()` and `dateLastModified = new Date()` are auto-populated by GORM on persist.
- **Java entity** (`ContactFor.java:57,61`): plain `@Column` with no `@CreatedDate` / `@LastModifiedDate` and no `@EntityListeners(AuditingEntityListener.class)`.
- **Verdict**: ⚠️ **Auto-timestamp management missing** — new `contact_for` records created via Spring Boot will have `date_created = NULL` and `date_last_modified = NULL` unless the caller explicitly sets them.

### 4.4 `DataProvider` — `pub_short_description` type regression

- **Grails**: no length constraint on `pubShortDescription` → MySQL stored as `LONGTEXT`
- **Java entity** (`DataProvider.java`): `@Column(name = "pub_short_description", length = 100)` → PG `VARCHAR(100)`
- **Verdict**: ⚠️ **Data truncation risk** — any MySQL rows where `pub_short_description` exceeded 100 chars would have been silently truncated on migration. Needs data verification.

### 4.5 `ProviderMap` — join table split (schema correct, data not migrated)

- **Grails**: `hasMany = [collectionCodes: ProviderCode, institutionCodes: ProviderCode]` → GORM produced a single join table `provider_map_provider_code` with two nullable FK columns (`provider_map_collection_codes_id`, `provider_map_institution_codes_id`)
- **Java entity**: two separate `@ManyToMany` with `@JoinTable` mapping to `provider_map_collection_codes` and `provider_map_institution_codes`
- **Schema verdict**: ✅ Correct JPA equivalent of the Grails pattern
- **Data verdict**: ⚠️ **Critical data loss** — see section 5

### 4.6 `ActivityLog` — `user` quoted identifier

- **Java entity** (`ActivityLog.java:54`): `@Column(name = "\"user\"")` — correctly quoted because `user` is a reserved word in PostgreSQL ✅

### 4.7 `Sequence` — table name quoting

- **Java entity** (`Sequence.java:17`): `@Table(name = "\"sequence\"")` — correctly quoted because `sequence` is a reserved word in PostgreSQL ✅

### 4.8 `AuditLogEvent` — no `version` column

- **Grails**: `version false` in mapping — no `version` column
- **Java entity**: no `@Version` field ✅ — correctly absent
- **PostgreSQL schema**: no `version` column ✅

---

## 5. Critical Data Loss: `provider_map_collection_codes` / `provider_map_institution_codes`

**This is the most critical finding.**

- MySQL `provider_map_provider_code`: **410 total rows**
  - 241 rows with `provider_map_collection_codes_id IS NOT NULL` → should migrate to `provider_map_collection_codes`
  - 169 rows with `provider_map_institution_codes_id IS NOT NULL` → should migrate to `provider_map_institution_codes`
- PostgreSQL `provider_map_collection_codes`: **0 rows**
- PostgreSQL `provider_map_institution_codes`: **0 rows**

**None of the 410 join table rows were migrated.** The `ProviderMap` feature — which maps `(institutionCode, collectionCode)` pairs to `Collection` or `Institution` records and is used for DwCA ingestion matching — is completely broken.

### Required Fix

A migration script (or new Flyway migration `V5__migrate_provider_map_codes.sql`) needs to:

```sql
-- Migrate collection codes
INSERT INTO provider_map_collection_codes (provider_map_id, provider_code_id)
SELECT provider_map_collection_codes_id, provider_code_id
FROM provider_map_provider_code  -- (from MySQL, must be run against migrated data)
WHERE provider_map_collection_codes_id IS NOT NULL;

-- Migrate institution codes
INSERT INTO provider_map_institution_codes (provider_map_id, provider_code_id)
SELECT provider_map_institution_codes_id, provider_code_id
FROM provider_map_provider_code
WHERE provider_map_institution_codes_id IS NOT NULL;
```

Since the original `provider_map_provider_code` table was not migrated to PostgreSQL, this data must be sourced from the MySQL dump and inserted directly. All 216 `provider_code` rows and all 160 `provider_map` rows are already present in PostgreSQL, so the FKs will resolve.

---

## 6. Minor Data Loss: `api_key_log`

- MySQL `api_key_log`: **2 rows** (API access log entries, ids 12 and 13, last calls from May and June 2025)
- PostgreSQL: **table does not exist, data not migrated**
- Low severity (logging/audit table, only 2 rows), but the table and schema were not carried over. If this functionality is needed in Spring Boot, the table must be recreated and the 2 rows optionally back-filled.

---

## 7. Summary of Issues by Severity

### 🔴 Critical

1. **`provider_map_collection_codes` and `provider_map_institution_codes` are empty** — 410 rows of join data from MySQL were never migrated. DwCA provider code matching is completely broken. Requires a targeted data migration from the MySQL dump.

### 🟠 High

2. **`TempDataResource.type` phantom column** — `@Column` annotation on a field that has no DB column (`TempDataResource.java:107`). Should be `@Transient` to match Grails behaviour.
3. **`data_provider.pub_short_description` type regression** — MySQL `LONGTEXT` → PG `VARCHAR(100)`. Data truncation may have occurred on migration. Needs verification against the MySQL dump.
4. **`ContactFor.dateCreated`/`dateLastModified` not auto-populated** — new records via Spring Boot will have NULL timestamps. Add `@CreatedDate`/`@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)` to `ContactFor.java`.

### 🟡 Medium

5. **`AuditLogEvent.newValue`/`oldValue` length annotation mismatch** (`AuditLogEvent.java:43,46`) — entity says 255, DB is 2048. Should be updated to `length = 2048`.
6. **`institution.name` and `collection.name` nullability tightened** — MySQL allows NULL, PG does not. Any future attempt to insert a nameless institution/collection will fail in PG but would succeed in MySQL.
7. **`data_resource.status` nullability tightened and length reduced** — MySQL: nullable, 255 chars; PG: NOT NULL DEFAULT 'identified', 45 chars. The DEFAULT handles migration, but existing NULL values in MySQL source data would fail on insert.
8. **`sequence.name` and `sequence.prefix` nullability tightened** — MySQL: nullable VARCHAR(5/45); PG: NOT NULL VARCHAR(255). Existing data had values so migration was fine, but the schema contract differs.

### 🟢 Low / Informational

9. **`api_key_log` not migrated** — 2 rows of API access log data not carried over. Table does not exist in PostgreSQL.
10. **`audit_log.persisted_object_id` type changed** — BIGINT in MySQL → VARCHAR(255) in PG. Values stored as numeric strings; intentional per V4 migration comment.
11. **`contact_for.date_created`/`date_last_modified` nullability relaxed** — MySQL NOT NULL → PG nullable (V3 migration). All 860 existing rows have values; no data lost.
12. **Multiple VARCHAR → TEXT widenings** across `institution`, `collection`, `data_resource`, `data_provider`, `data_hub` — all compatible, no data loss.

---

## 8. Flyway Migration Files Reviewed

| File | Purpose | Notes |
|------|---------|-------|
| `V1__initial_schema.sql` | Full PostgreSQL initial schema | Base schema |
| `V2__5_1_0_updates.sql` | NOT NULL + DEFAULT on `gbif_country_to_attribute` | ✅ |
| `V3__nullable_fixes.sql` | Drop NOT NULL on lat/lon and contact_for timestamps | ✅ |
| `V4__mysql_compat_columns.sql` | Adds `citable_agent`, `contributor`, `display_name`, `last_harvested` to `data_resource`; widens `audit_log.new_value/old_value` to 2048 | ✅ |
