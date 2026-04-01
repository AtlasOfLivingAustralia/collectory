-- ============================================================================
-- V3__nullable_fixes.sql
-- L5: Drop NOT NULL on latitude/longitude across all entity tables so that
--     Java's nullable Double fields work without sentinel-value conversion.
-- L6: Drop NOT NULL on contact_for timestamp columns and add defaults so
--     Spring Data auditing can manage them.
-- ============================================================================

-- L5: Allow NULL latitude/longitude (Java entity has nullable Double)
ALTER TABLE collection        ALTER COLUMN latitude  DROP NOT NULL;
ALTER TABLE collection        ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE institution       ALTER COLUMN latitude  DROP NOT NULL;
ALTER TABLE institution       ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE data_provider     ALTER COLUMN latitude  DROP NOT NULL;
ALTER TABLE data_provider     ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE data_resource     ALTER COLUMN latitude  DROP NOT NULL;
ALTER TABLE data_resource     ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE data_hub          ALTER COLUMN latitude  DROP NOT NULL;
ALTER TABLE data_hub          ALTER COLUMN longitude DROP NOT NULL;

-- L6: Make contact_for timestamps nullable so Spring Data @CreatedDate/@LastModifiedDate
--     can populate them on first insert without manual pre-setting.
ALTER TABLE contact_for ALTER COLUMN date_created        DROP NOT NULL;
ALTER TABLE contact_for ALTER COLUMN date_last_modified  DROP NOT NULL;
