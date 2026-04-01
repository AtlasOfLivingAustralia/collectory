-- ============================================================================
-- V3__mysql_compat_columns.sql
-- Add columns that exist in the legacy MySQL schema but were missing from
-- the initial PostgreSQL schema, and widen columns where MySQL was larger.
-- ============================================================================

-- data_resource: missing columns
ALTER TABLE data_resource ADD COLUMN IF NOT EXISTS citable_agent VARCHAR(2048);
ALTER TABLE data_resource ADD COLUMN IF NOT EXISTS contributor BOOLEAN DEFAULT FALSE;
ALTER TABLE data_resource ADD COLUMN IF NOT EXISTS display_name VARCHAR(1024);
ALTER TABLE data_resource ADD COLUMN IF NOT EXISTS last_harvested TIMESTAMP;

-- audit_log: widen new_value / old_value from 255 to 2048 (matches MySQL)
ALTER TABLE audit_log ALTER COLUMN new_value TYPE VARCHAR(2048);
ALTER TABLE audit_log ALTER COLUMN old_value TYPE VARCHAR(2048);

-- audit_log: persisted_object_id is BIGINT in MySQL but VARCHAR(255) in PG.
-- Keep as VARCHAR for flexibility (some values may not be numeric).
