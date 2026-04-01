-- ============================================================================
-- V1__initial_schema.sql
-- Flyway initial schema migration for collectory (MySQL -> PostgreSQL)
--
-- Ported from MySQL initial.sql + 5.1.0*.sql migrations.
-- Address and Image fields are EMBEDDED (flattened into parent tables)
-- matching the GORM `static embedded = ['address', 'logoRef', 'imageRef']`.
-- ============================================================================

-- 1. institution
CREATE TABLE institution (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT NOT NULL,
    acronym                     VARCHAR(45),
    address_city                VARCHAR(255),
    address_country             VARCHAR(255),
    address_post_box            VARCHAR(255),
    address_postcode            VARCHAR(255),
    address_state               VARCHAR(255),
    address_street              VARCHAR(255),
    altitude                    VARCHAR(255),
    attributions                VARCHAR(256),
    child_institutions          VARCHAR(255),
    date_created                TIMESTAMP NOT NULL,
    email                       VARCHAR(256),
    focus                       TEXT,
    gbif_country_to_attribute   VARCHAR(3) NOT NULL DEFAULT '',
    gbif_registry_key           VARCHAR(36),
    guid                        VARCHAR(256),
    image_ref_attribution       VARCHAR(255),
    image_ref_caption           VARCHAR(255),
    image_ref_copyright         VARCHAR(255),
    image_ref_file              VARCHAR(255),
    institution_type            VARCHAR(45),
    isalapartner                BOOLEAN NOT NULL DEFAULT FALSE,
    keywords                    TEXT,
    last_updated                TIMESTAMP NOT NULL,
    latitude                    NUMERIC(13,10) NOT NULL DEFAULT 0,
    logo_ref_attribution        VARCHAR(255),
    logo_ref_caption            VARCHAR(255),
    logo_ref_copyright          VARCHAR(255),
    logo_ref_file               VARCHAR(255),
    longitude                   NUMERIC(13,10) NOT NULL DEFAULT 0,
    name                        VARCHAR(1024) NOT NULL,
    network_membership          TEXT,
    notes                       TEXT,
    phone                       VARCHAR(200),
    pub_description             TEXT,
    pub_short_description       VARCHAR(100),
    state                       VARCHAR(45),
    taxonomy_hints              TEXT,
    tech_description            TEXT,
    uid                         VARCHAR(20) NOT NULL,
    user_last_modified          VARCHAR(255) NOT NULL,
    website_url                 VARCHAR(256)
);

CREATE INDEX institution_uid_idx ON institution(uid);

-- 2. collection
CREATE TABLE collection (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT NOT NULL,
    acronym                     VARCHAR(45),
    active                      VARCHAR(14),
    address_city                VARCHAR(255),
    address_country             VARCHAR(255),
    address_post_box            VARCHAR(255),
    address_postcode            VARCHAR(255),
    address_state               VARCHAR(255),
    address_street              VARCHAR(255),
    altitude                    VARCHAR(255),
    attributions                VARCHAR(256),
    collection_type             VARCHAR(256),
    date_created                TIMESTAMP NOT NULL,
    east_coordinate             NUMERIC(13,10) NOT NULL DEFAULT 0,
    email                       VARCHAR(256),
    end_date                    VARCHAR(45),
    focus                       TEXT,
    gbif_registry_key           VARCHAR(36),
    geographic_description      VARCHAR(255),
    guid                        VARCHAR(256),
    image_ref_attribution       VARCHAR(255),
    image_ref_caption           VARCHAR(255),
    image_ref_copyright         VARCHAR(255),
    image_ref_file              VARCHAR(255),
    institution_id              BIGINT REFERENCES institution(id),
    isalapartner                BOOLEAN NOT NULL DEFAULT FALSE,
    keywords                    TEXT,
    kingdom_coverage            TEXT,
    last_updated                TIMESTAMP NOT NULL,
    latitude                    NUMERIC(13,10) NOT NULL DEFAULT 0,
    logo_ref_attribution        VARCHAR(255),
    logo_ref_caption            VARCHAR(255),
    logo_ref_copyright          VARCHAR(255),
    logo_ref_file               VARCHAR(255),
    longitude                   NUMERIC(13,10) NOT NULL DEFAULT 0,
    name                        VARCHAR(1024) NOT NULL,
    network_membership          TEXT,
    north_coordinate            NUMERIC(13,10) NOT NULL DEFAULT 0,
    notes                       TEXT,
    num_records                 INTEGER NOT NULL DEFAULT 0,
    num_records_digitised       INTEGER NOT NULL DEFAULT 0,
    phone                       VARCHAR(200),
    pub_description             TEXT,
    pub_short_description       VARCHAR(100),
    scientific_names            TEXT,
    south_coordinate            NUMERIC(13,10) NOT NULL DEFAULT 0,
    start_date                  VARCHAR(45),
    state                       VARCHAR(45),
    states                      VARCHAR(255),
    sub_collections             TEXT,
    taxonomy_hints              TEXT,
    tech_description            TEXT,
    uid                         VARCHAR(20) NOT NULL,
    user_last_modified          VARCHAR(255) NOT NULL,
    website_url                 VARCHAR(256),
    west_coordinate             NUMERIC(13,10) NOT NULL DEFAULT 0
);

CREATE INDEX collection_uid_idx ON collection(uid);
CREATE INDEX collection_institution_id_idx ON collection(institution_id);

-- 3. contact
CREATE TABLE contact (
    id                  BIGSERIAL PRIMARY KEY,
    version             BIGINT NOT NULL,
    date_created        TIMESTAMP NOT NULL,
    email               VARCHAR(128),
    fax                 VARCHAR(45),
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    last_updated        TIMESTAMP NOT NULL,
    mobile              VARCHAR(45),
    notes               VARCHAR(1024),
    phone               VARCHAR(45),
    publish             BOOLEAN NOT NULL DEFAULT TRUE,
    title               VARCHAR(20),
    user_last_modified  VARCHAR(256) NOT NULL,
    -- Fields added after initial schema (by dbCreate:update)
    organization_name   VARCHAR(255),
    position_name       VARCHAR(255),
    user_id             VARCHAR(255)
);

-- 4. contact_for
CREATE TABLE contact_for (
    id                  BIGSERIAL PRIMARY KEY,
    version             BIGINT NOT NULL,
    administrator       BOOLEAN NOT NULL DEFAULT FALSE,
    contact_id          BIGINT NOT NULL REFERENCES contact(id),
    date_created        TIMESTAMP NOT NULL,
    date_last_modified  TIMESTAMP NOT NULL,
    entity_uid          VARCHAR(255) NOT NULL,
    notify              BOOLEAN NOT NULL DEFAULT FALSE,
    primary_contact     BOOLEAN NOT NULL DEFAULT FALSE,
    role                VARCHAR(128),
    user_last_modified  VARCHAR(256) NOT NULL
);

CREATE INDEX contact_for_contact_id_idx ON contact_for(contact_id);
CREATE INDEX contact_for_entity_uid_idx ON contact_for(entity_uid);

-- 5. data_provider
CREATE TABLE data_provider (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT NOT NULL,
    acronym                     VARCHAR(45),
    address_city                VARCHAR(255),
    address_country             VARCHAR(255),
    address_post_box            VARCHAR(255),
    address_postcode            VARCHAR(255),
    address_state               VARCHAR(255),
    address_street              VARCHAR(255),
    altitude                    VARCHAR(255),
    attributions                VARCHAR(256),
    date_created                TIMESTAMP NOT NULL,
    email                       VARCHAR(256),
    focus                       TEXT,
    gbif_country_to_attribute   VARCHAR(3) NOT NULL DEFAULT '',
    gbif_registry_key           VARCHAR(36),
    guid                        VARCHAR(256),
    hiddenjson                  TEXT,
    image_ref_attribution       VARCHAR(255),
    image_ref_caption           VARCHAR(255),
    image_ref_copyright         VARCHAR(255),
    image_ref_file              VARCHAR(255),
    isalapartner                BOOLEAN NOT NULL DEFAULT FALSE,
    keywords                    VARCHAR(255),
    last_updated                TIMESTAMP NOT NULL,
    latitude                    NUMERIC(13,10) NOT NULL DEFAULT 0,
    logo_ref_attribution        VARCHAR(255),
    logo_ref_caption            VARCHAR(255),
    logo_ref_copyright          VARCHAR(255),
    logo_ref_file               VARCHAR(255),
    longitude                   NUMERIC(13,10) NOT NULL DEFAULT 0,
    name                        VARCHAR(1024) NOT NULL,
    network_membership          TEXT,
    notes                       TEXT,
    phone                       VARCHAR(200),
    pub_description             TEXT,
    pub_short_description       VARCHAR(100),
    state                       VARCHAR(45),
    taxonomy_hints              TEXT,
    tech_description            TEXT,
    uid                         VARCHAR(20) NOT NULL,
    user_last_modified          VARCHAR(255) NOT NULL,
    website_url                 VARCHAR(256)
);

CREATE INDEX data_provider_uid_idx ON data_provider(uid);

-- 6. data_resource
CREATE TABLE data_resource (
    id                              BIGSERIAL PRIMARY KEY,
    version                         BIGINT NOT NULL,
    acronym                         VARCHAR(45),
    address_city                    VARCHAR(255),
    address_country                 VARCHAR(255),
    address_post_box                VARCHAR(255),
    address_postcode                VARCHAR(255),
    address_state                   VARCHAR(255),
    address_street                  VARCHAR(255),
    altitude                        VARCHAR(255),
    attributions                    VARCHAR(256),
    begin_date                      VARCHAR(255),
    citation                        TEXT,
    connection_parameters           TEXT,
    content_types                   VARCHAR(2048),
    data_currency                   TIMESTAMP,
    data_generalizations            TEXT,
    data_provider_id                BIGINT REFERENCES data_provider(id),
    date_created                    TIMESTAMP NOT NULL,
    default_darwin_core_values      TEXT,
    download_limit                  INTEGER NOT NULL DEFAULT 0,
    east_bounding_coordinate        VARCHAR(255),
    email                           VARCHAR(256),
    end_date                        VARCHAR(255),
    filed                           BOOLEAN NOT NULL DEFAULT FALSE,
    focus                           TEXT,
    gbif_dataset                    BOOLEAN NOT NULL DEFAULT FALSE,
    gbif_doi                        VARCHAR(255),
    gbif_registry_key               VARCHAR(36),
    geographic_description          TEXT,
    guid                            VARCHAR(256),
    harvest_frequency               INTEGER NOT NULL DEFAULT 0,
    harvesting_notes                TEXT,
    image_metadata                  TEXT,
    image_ref_attribution           VARCHAR(255),
    image_ref_caption               VARCHAR(255),
    image_ref_copyright             VARCHAR(255),
    image_ref_file                  VARCHAR(255),
    information_withheld            TEXT,
    institution_id                  BIGINT REFERENCES institution(id),
    isalapartner                    BOOLEAN NOT NULL DEFAULT FALSE,
    is_shareable_withgbif           BOOLEAN NOT NULL DEFAULT TRUE,
    keywords                        VARCHAR(255),
    last_checked                    TIMESTAMP,
    last_updated                    TIMESTAMP NOT NULL,
    latitude                        NUMERIC(13,10) NOT NULL DEFAULT 0,
    license_type                    VARCHAR(45) DEFAULT 'other',
    license_version                 VARCHAR(45),
    logo_ref_attribution            VARCHAR(255),
    logo_ref_caption                VARCHAR(255),
    logo_ref_copyright              VARCHAR(255),
    logo_ref_file                   VARCHAR(255),
    longitude                       NUMERIC(13,10) NOT NULL DEFAULT 0,
    make_contact_public             BOOLEAN NOT NULL DEFAULT TRUE,
    method_step_description         TEXT,
    mobilisation_notes              TEXT,
    name                            VARCHAR(1024) NOT NULL,
    network_membership              TEXT,
    north_bounding_coordinate       VARCHAR(255),
    notes                           TEXT,
    permissions_document            TEXT,
    permissions_document_type       VARCHAR(23) DEFAULT 'Other',
    phone                           VARCHAR(200),
    provenance                      VARCHAR(45),
    pub_description                 TEXT,
    pub_short_description           VARCHAR(100),
    public_archive_available        BOOLEAN NOT NULL DEFAULT FALSE,
    purpose                         TEXT,
    quality_control_description     TEXT,
    repatriation_country            VARCHAR(255),
    resource_type                   VARCHAR(255) NOT NULL DEFAULT 'records',
    rights                          TEXT,
    risk_assessment                 BOOLEAN NOT NULL DEFAULT FALSE,
    south_bounding_coordinate       VARCHAR(255),
    state                           VARCHAR(45),
    status                          VARCHAR(45) NOT NULL DEFAULT 'identified',
    taxonomy_hints                  TEXT,
    tech_description                TEXT,
    uid                             VARCHAR(20) NOT NULL,
    user_last_modified              VARCHAR(255) NOT NULL,
    website_url                     VARCHAR(256),
    west_bounding_coordinate        VARCHAR(255),
    -- Fields added after initial schema (by dbCreate:update)
    is_private                      BOOLEAN DEFAULT FALSE,
    created_byid                    VARCHAR(255),
    data_collection_protocol_name   TEXT,
    data_collection_protocol_doc    TEXT,
    suitable_for                    TEXT,
    suitable_for_other_detail       TEXT
);

CREATE INDEX data_resource_uid_idx ON data_resource(uid);
CREATE INDEX data_resource_data_provider_id_idx ON data_resource(data_provider_id);
CREATE INDEX data_resource_institution_id_idx ON data_resource(institution_id);

-- 7. data_hub
CREATE TABLE data_hub (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT NOT NULL,
    acronym                     VARCHAR(45),
    address_city                VARCHAR(255),
    address_country             VARCHAR(255),
    address_post_box            VARCHAR(255),
    address_postcode            VARCHAR(255),
    address_state               VARCHAR(255),
    address_street              VARCHAR(255),
    altitude                    VARCHAR(255),
    attributions                VARCHAR(256),
    date_created                TIMESTAMP NOT NULL,
    email                       VARCHAR(256),
    focus                       TEXT,
    gbif_registry_key           VARCHAR(36),
    guid                        VARCHAR(256),
    image_ref_attribution       VARCHAR(255),
    image_ref_caption           VARCHAR(255),
    image_ref_copyright         VARCHAR(255),
    image_ref_file              VARCHAR(255),
    isalapartner                BOOLEAN NOT NULL DEFAULT FALSE,
    keywords                    TEXT,
    last_updated                TIMESTAMP NOT NULL,
    latitude                    NUMERIC(13,10) NOT NULL DEFAULT 0,
    logo_ref_attribution        VARCHAR(255),
    logo_ref_caption            VARCHAR(255),
    logo_ref_copyright          VARCHAR(255),
    logo_ref_file               VARCHAR(255),
    longitude                   NUMERIC(13,10) NOT NULL DEFAULT 0,
    member_collections          TEXT,
    member_data_resources       TEXT,
    member_institutions         TEXT,
    members                     TEXT,
    name                        VARCHAR(1024) NOT NULL,
    network_membership          TEXT,
    notes                       TEXT,
    phone                       VARCHAR(200),
    pub_description             TEXT,
    pub_short_description       VARCHAR(100),
    state                       VARCHAR(45),
    taxonomy_hints              TEXT,
    tech_description            TEXT,
    uid                         VARCHAR(20) NOT NULL,
    user_last_modified          VARCHAR(255) NOT NULL,
    website_url                 VARCHAR(256)
);

CREATE INDEX data_hub_uid_idx ON data_hub(uid);

-- 8. temp_data_resource
CREATE TABLE temp_data_resource (
    id                      BIGSERIAL PRIMARY KEY,
    version                 BIGINT NOT NULL,
    ala_id                  VARCHAR(256),
    citation                TEXT,
    csv_separator           VARCHAR(10),
    data_generalisations    TEXT,
    date_created            TIMESTAMP NOT NULL,
    description             TEXT,
    email                   VARCHAR(256),
    first_name              VARCHAR(255),
    information_withheld    TEXT,
    is_contact_public       BOOLEAN,
    key_fields              VARCHAR(255),
    last_name               VARCHAR(255),
    last_updated            TIMESTAMP NOT NULL,
    license                 VARCHAR(10),
    name                    VARCHAR(1024),
    number_of_records       INTEGER NOT NULL DEFAULT 0,
    prod_uid                VARCHAR(20),
    source_file             VARCHAR(255),
    status                  VARCHAR(16) DEFAULT 'draft',
    ui_url                  VARCHAR(255),
    uid                     VARCHAR(20) NOT NULL,
    webservice_url          VARCHAR(255)
);

CREATE INDEX temp_data_resource_uid_idx ON temp_data_resource(uid);

-- 9. external_identifier
CREATE TABLE external_identifier (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL,
    entity_uid      VARCHAR(255) NOT NULL,
    identifier      VARCHAR(255) NOT NULL,
    source          VARCHAR(255) NOT NULL,
    uri             VARCHAR(255)
);

-- 10. attribution
CREATE TABLE attribution (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL,
    name            VARCHAR(256) NOT NULL,
    uid             VARCHAR(20) NOT NULL,
    url             VARCHAR(256)
);

-- 11. audit_log
CREATE TABLE audit_log (
    id                          BIGSERIAL PRIMARY KEY,
    actor                       VARCHAR(255),
    class_name                  VARCHAR(255),
    date_created                TIMESTAMP NOT NULL,
    event_name                  VARCHAR(255),
    last_updated                TIMESTAMP NOT NULL,
    new_value                   VARCHAR(255),
    old_value                   VARCHAR(255),
    persisted_object_id         VARCHAR(255),
    persisted_object_version    BIGINT,
    property_name               VARCHAR(255),
    uri                         VARCHAR(255)
);

-- 12. licence
CREATE TABLE licence (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL,
    acronym         VARCHAR(255) NOT NULL,
    date_created    TIMESTAMP NOT NULL,
    image_url       VARCHAR(255),
    last_updated    TIMESTAMP NOT NULL,
    licence_version VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    url             VARCHAR(255) NOT NULL
);

-- 13. provider_map
CREATE TABLE provider_map (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT NOT NULL,
    collection_id               BIGINT NOT NULL UNIQUE REFERENCES collection(id),
    date_created                TIMESTAMP NOT NULL,
    exact                       BOOLEAN NOT NULL DEFAULT TRUE,
    institution_id              BIGINT REFERENCES institution(id),
    last_updated                TIMESTAMP NOT NULL,
    match_any_collection_code   BOOLEAN NOT NULL DEFAULT FALSE,
    warning                     VARCHAR(255)
);

CREATE INDEX provider_map_institution_id_idx ON provider_map(institution_id);

-- 14. provider_code
CREATE TABLE provider_code (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL,
    code            VARCHAR(200) NOT NULL
);

-- 15. provider_map join tables (separate tables for collection codes and institution codes)
CREATE TABLE provider_map_collection_codes (
    provider_map_id     BIGINT NOT NULL REFERENCES provider_map(id),
    provider_code_id    BIGINT NOT NULL REFERENCES provider_code(id),
    PRIMARY KEY (provider_map_id, provider_code_id)
);

CREATE TABLE provider_map_institution_codes (
    provider_map_id     BIGINT NOT NULL REFERENCES provider_map(id),
    provider_code_id    BIGINT NOT NULL REFERENCES provider_code(id),
    PRIMARY KEY (provider_map_id, provider_code_id)
);

-- 16. activity_log
CREATE TABLE activity_log (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT NOT NULL,
    action                      VARCHAR(255) NOT NULL,
    admin                       BOOLEAN NOT NULL DEFAULT FALSE,
    administrator_for_entity    BOOLEAN NOT NULL DEFAULT FALSE,
    contact_for_entity          BOOLEAN NOT NULL DEFAULT FALSE,
    entity_uid                  VARCHAR(255),
    "timestamp"                 TIMESTAMP NOT NULL,
    "user"                      VARCHAR(255) NOT NULL
);

-- 17. "sequence" (reserved word in PostgreSQL)
CREATE TABLE "sequence" (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    next_id         BIGINT NOT NULL,
    prefix          VARCHAR(255) NOT NULL
);

-- 18. data_link (legacy table)
CREATE TABLE data_link (
    id              BIGSERIAL PRIMARY KEY,
    version         BIGINT NOT NULL,
    consumer        VARCHAR(255) NOT NULL,
    provider        VARCHAR(255) NOT NULL
);

-- ============================================================================
-- Join tables for hasMany relationships with ExternalIdentifier
-- Column names match GORM convention: {ownerClass}_external_identifiers_id
-- ============================================================================

CREATE TABLE collection_external_identifier (
    collection_external_identifiers_id  BIGINT NOT NULL REFERENCES collection(id),
    external_identifier_id              BIGINT NOT NULL REFERENCES external_identifier(id)
);

CREATE INDEX cei_collection_idx ON collection_external_identifier(collection_external_identifiers_id);
CREATE INDEX cei_ext_id_idx ON collection_external_identifier(external_identifier_id);

CREATE TABLE data_provider_external_identifier (
    data_provider_external_identifiers_id   BIGINT NOT NULL REFERENCES data_provider(id),
    external_identifier_id                  BIGINT NOT NULL REFERENCES external_identifier(id)
);

CREATE INDEX dpei_data_provider_idx ON data_provider_external_identifier(data_provider_external_identifiers_id);
CREATE INDEX dpei_ext_id_idx ON data_provider_external_identifier(external_identifier_id);

CREATE TABLE data_resource_external_identifier (
    data_resource_external_identifiers_id   BIGINT NOT NULL REFERENCES data_resource(id),
    external_identifier_id                  BIGINT NOT NULL REFERENCES external_identifier(id)
);

CREATE INDEX drei_data_resource_idx ON data_resource_external_identifier(data_resource_external_identifiers_id);
CREATE INDEX drei_ext_id_idx ON data_resource_external_identifier(external_identifier_id);

CREATE TABLE institution_external_identifier (
    institution_external_identifiers_id     BIGINT NOT NULL REFERENCES institution(id),
    external_identifier_id                  BIGINT NOT NULL REFERENCES external_identifier(id)
);

CREATE INDEX iei_institution_idx ON institution_external_identifier(institution_external_identifiers_id);
CREATE INDEX iei_ext_id_idx ON institution_external_identifier(external_identifier_id);

CREATE TABLE data_hub_external_identifier (
    data_hub_external_identifiers_id        BIGINT NOT NULL REFERENCES data_hub(id),
    external_identifier_id                  BIGINT NOT NULL REFERENCES external_identifier(id)
);

CREATE INDEX dhei_data_hub_idx ON data_hub_external_identifier(data_hub_external_identifiers_id);
CREATE INDEX dhei_ext_id_idx ON data_hub_external_identifier(external_identifier_id);

-- ============================================================================
-- Join tables for DataProvider consumer relationships
-- ============================================================================

CREATE TABLE data_provider_collection (
    data_provider_id    BIGINT NOT NULL REFERENCES data_provider(id),
    collection_id       BIGINT NOT NULL REFERENCES collection(id)
);

CREATE INDEX dpc_dp_idx ON data_provider_collection(data_provider_id);
CREATE INDEX dpc_coll_idx ON data_provider_collection(collection_id);

CREATE TABLE data_provider_institution (
    data_provider_id    BIGINT NOT NULL REFERENCES data_provider(id),
    institution_id      BIGINT NOT NULL REFERENCES institution(id)
);

CREATE INDEX dpi_dp_idx ON data_provider_institution(data_provider_id);
CREATE INDEX dpi_inst_idx ON data_provider_institution(institution_id);

-- ============================================================================
-- Join tables for DataResource consumer relationships
-- ============================================================================

CREATE TABLE data_resource_institution (
    data_resource_id    BIGINT NOT NULL REFERENCES data_resource(id),
    institution_id      BIGINT NOT NULL REFERENCES institution(id)
);

CREATE INDEX dri_dr_idx ON data_resource_institution(data_resource_id);
CREATE INDEX dri_inst_idx ON data_resource_institution(institution_id);

CREATE TABLE data_resource_collection (
    data_resource_id    BIGINT NOT NULL REFERENCES data_resource(id),
    collection_id       BIGINT NOT NULL REFERENCES collection(id)
);

CREATE INDEX drc_dr_idx ON data_resource_collection(data_resource_id);
CREATE INDEX drc_coll_idx ON data_resource_collection(collection_id);
