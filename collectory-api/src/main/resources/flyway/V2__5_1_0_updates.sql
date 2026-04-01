-- ============================================================================
-- V2__5_1_0_updates.sql
-- Ensure gbifCountryToAttribute columns are NOT NULL with default value.
--
-- NOTE: The external_identifier join tables and data-link join tables are
-- already created in V1__initial_schema.sql. The INSERT...SELECT data
-- migration statements from the original Grails migrations are not needed
-- for a fresh PostgreSQL database (no legacy data to migrate).
-- ============================================================================

-- institution: set any existing NULLs to empty string, then enforce NOT NULL
UPDATE institution SET gbif_country_to_attribute = '' WHERE gbif_country_to_attribute IS NULL;
ALTER TABLE institution ALTER COLUMN gbif_country_to_attribute SET NOT NULL;
ALTER TABLE institution ALTER COLUMN gbif_country_to_attribute SET DEFAULT '';

-- data_provider: set any existing NULLs to empty string, then enforce NOT NULL
UPDATE data_provider SET gbif_country_to_attribute = '' WHERE gbif_country_to_attribute IS NULL;
ALTER TABLE data_provider ALTER COLUMN gbif_country_to_attribute SET NOT NULL;
ALTER TABLE data_provider ALTER COLUMN gbif_country_to_attribute SET DEFAULT '';
