#!/usr/bin/env bash
# ===========================================================================
# migrate_mysql_to_pg.sh
# Migrates data from the legacy MySQL collectory database to PostgreSQL.
#
# Prerequisites:
#   - MySQL container running on localhost:3306
#   - PostgreSQL container running on localhost:5432
#   - Both databases named 'collectory' with user 'collectory_user' / 'password'
#   - PostgreSQL schema already created by Flyway (V1–V3)
#
# Usage: bash scripts/migrate_mysql_to_pg.sh
# ===========================================================================
set -euo pipefail

MYSQL_CMD="docker exec mysql-collectory mysql -ucollectory_user -ppassword collectory -N -B"
PG_CMD="PGPASSWORD=password psql -h localhost -U collectory_user -d collectory"

DUMP_DIR="/tmp/collectory_migration"
mkdir -p "$DUMP_DIR"

echo "=== Collectory MySQL → PostgreSQL Data Migration ==="
echo ""

# ---------------------------------------------------------------------------
# Helper: dump a MySQL table to TSV, load into PostgreSQL via COPY
# $1 = table name
# $2 = comma-separated column list
# ---------------------------------------------------------------------------
migrate_table() {
    local table=$1
    local columns=$2

    echo -n "  Migrating $table... "

    # Build SELECT with proper NULL handling
    local select_cols=""
    IFS=',' read -ra COLS <<< "$columns"
    for col in "${COLS[@]}"; do
        if [ -n "$select_cols" ]; then
            select_cols="$select_cols, "
        fi
        select_cols="${select_cols}IFNULL(CAST(\`${col}\` AS CHAR), '\\\\N')"
    done

    # Dump from MySQL as TSV
    $MYSQL_CMD -e "SELECT $select_cols FROM \`$table\`" 2>/dev/null \
        | sed 's/\\\\N/\\N/g' \
        > "$DUMP_DIR/${table}.tsv"

    local count=$(wc -l < "$DUMP_DIR/${table}.tsv" | tr -d ' ')

    if [ "$count" -eq 0 ]; then
        echo "0 rows (skipped)"
        return
    fi

    # Truncate target and load
    eval "$PG_CMD -c \"TRUNCATE TABLE \\\"$table\\\" CASCADE;\"" 2>/dev/null || true
    eval "$PG_CMD -c \"\\COPY \\\"$table\\\"($columns) FROM '$DUMP_DIR/${table}.tsv' WITH (FORMAT text, NULL '\\\\N');\"" 2>/dev/null

    echo "$count rows"
}

# ---------------------------------------------------------------------------
# Step 1: Migrate tables in dependency order
# ---------------------------------------------------------------------------
echo "--- Step 1: Core entities ---"

migrate_table "institution" \
    "id,version,acronym,address_city,address_country,address_post_box,address_postcode,address_state,address_street,altitude,attributions,child_institutions,date_created,email,focus,gbif_country_to_attribute,gbif_registry_key,guid,image_ref_attribution,image_ref_caption,image_ref_copyright,image_ref_file,institution_type,isalapartner,keywords,last_updated,latitude,logo_ref_attribution,logo_ref_caption,logo_ref_copyright,logo_ref_file,longitude,name,network_membership,notes,phone,pub_description,pub_short_description,state,taxonomy_hints,tech_description,uid,user_last_modified,website_url"

migrate_table "collection" \
    "id,version,acronym,active,address_city,address_country,address_post_box,address_postcode,address_state,address_street,altitude,attributions,collection_type,date_created,east_coordinate,email,end_date,focus,gbif_registry_key,geographic_description,guid,image_ref_attribution,image_ref_caption,image_ref_copyright,image_ref_file,institution_id,isalapartner,keywords,kingdom_coverage,last_updated,latitude,logo_ref_attribution,logo_ref_caption,logo_ref_copyright,logo_ref_file,longitude,name,network_membership,north_coordinate,notes,num_records,num_records_digitised,phone,pub_description,pub_short_description,scientific_names,south_coordinate,start_date,state,states,sub_collections,taxonomy_hints,tech_description,uid,user_last_modified,website_url,west_coordinate"

migrate_table "contact" \
    "id,version,date_created,email,fax,first_name,last_name,last_updated,mobile,notes,phone,publish,title,user_last_modified,organization_name,position_name,user_id"

migrate_table "contact_for" \
    "id,version,administrator,contact_id,date_created,date_last_modified,entity_uid,notify,primary_contact,role,user_last_modified"

migrate_table "data_provider" \
    "id,version,acronym,address_city,address_country,address_post_box,address_postcode,address_state,address_street,altitude,attributions,date_created,email,focus,gbif_country_to_attribute,gbif_registry_key,guid,hiddenjson,image_ref_attribution,image_ref_caption,image_ref_copyright,image_ref_file,isalapartner,keywords,last_updated,latitude,logo_ref_attribution,logo_ref_caption,logo_ref_copyright,logo_ref_file,longitude,name,network_membership,notes,phone,pub_description,pub_short_description,state,taxonomy_hints,tech_description,uid,user_last_modified,website_url"

migrate_table "data_resource" \
    "id,version,acronym,address_city,address_country,address_post_box,address_postcode,address_state,address_street,altitude,attributions,begin_date,citation,connection_parameters,content_types,data_currency,data_generalizations,data_provider_id,date_created,default_darwin_core_values,download_limit,east_bounding_coordinate,email,end_date,filed,focus,gbif_dataset,gbif_doi,gbif_registry_key,geographic_description,guid,harvest_frequency,harvesting_notes,image_metadata,image_ref_attribution,image_ref_caption,image_ref_copyright,image_ref_file,information_withheld,institution_id,isalapartner,is_shareable_withgbif,keywords,last_checked,last_updated,latitude,license_type,license_version,logo_ref_attribution,logo_ref_caption,logo_ref_copyright,logo_ref_file,longitude,make_contact_public,method_step_description,mobilisation_notes,name,network_membership,north_bounding_coordinate,notes,permissions_document,permissions_document_type,phone,provenance,pub_description,pub_short_description,public_archive_available,purpose,quality_control_description,repatriation_country,resource_type,rights,risk_assessment,south_bounding_coordinate,state,status,taxonomy_hints,tech_description,uid,user_last_modified,website_url,west_bounding_coordinate,is_private,created_byid,data_collection_protocol_name,data_collection_protocol_doc,suitable_for,suitable_for_other_detail,citable_agent,contributor,display_name,last_harvested"

migrate_table "data_hub" \
    "id,version,acronym,address_city,address_country,address_post_box,address_postcode,address_state,address_street,altitude,attributions,date_created,email,focus,gbif_registry_key,guid,image_ref_attribution,image_ref_caption,image_ref_copyright,image_ref_file,isalapartner,keywords,last_updated,latitude,logo_ref_attribution,logo_ref_caption,logo_ref_copyright,logo_ref_file,longitude,member_collections,member_data_resources,member_institutions,members,name,network_membership,notes,phone,pub_description,pub_short_description,state,taxonomy_hints,tech_description,uid,user_last_modified,website_url"

migrate_table "temp_data_resource" \
    "id,version,ala_id,citation,csv_separator,data_generalisations,date_created,description,email,first_name,information_withheld,is_contact_public,key_fields,last_name,last_updated,license,name,number_of_records,prod_uid,source_file,status,ui_url,uid,webservice_url"

echo ""
echo "--- Step 2: Supporting entities ---"

migrate_table "external_identifier" \
    "id,version,entity_uid,identifier,source,uri"

migrate_table "attribution" \
    "id,version,name,uid,url"

migrate_table "licence" \
    "id,version,acronym,date_created,image_url,last_updated,licence_version,name,url"

migrate_table "provider_map" \
    "id,version,collection_id,date_created,exact,institution_id,last_updated,match_any_collection_code,warning"

migrate_table "provider_code" \
    "id,version,code"

migrate_table "data_link" \
    "id,version,consumer,provider"

migrate_table "\"sequence\"" \
    "id,version,name,next_id,prefix"

echo ""
echo "--- Step 3: Join tables ---"

migrate_table "collection_external_identifier" \
    "collection_external_identifiers_id,external_identifier_id"

migrate_table "data_provider_external_identifier" \
    "data_provider_external_identifiers_id,external_identifier_id"

migrate_table "data_resource_external_identifier" \
    "data_resource_external_identifiers_id,external_identifier_id"

migrate_table "institution_external_identifier" \
    "institution_external_identifiers_id,external_identifier_id"

migrate_table "data_hub_external_identifier" \
    "data_hub_external_identifiers_id,external_identifier_id"

migrate_table "data_provider_collection" \
    "data_provider_id,collection_id"

migrate_table "data_provider_institution" \
    "data_provider_id,institution_id"

migrate_table "data_resource_institution" \
    "data_resource_id,institution_id"

migrate_table "data_resource_collection" \
    "data_resource_id,collection_id"

# ---------------------------------------------------------------------------
# Step 3b: provider_map_provider_code → two separate PG tables
# MySQL has a single table with nullable FK columns for collection/institution codes
# ---------------------------------------------------------------------------
echo -n "  Migrating provider_map_provider_code → provider_map_collection_codes... "
$MYSQL_CMD -e "SELECT IFNULL(provider_map_collection_codes_id, '\\\\N'), IFNULL(provider_code_id, '\\\\N') FROM provider_map_provider_code WHERE provider_map_collection_codes_id IS NOT NULL" 2>/dev/null \
    | sed 's/\\\\N/\\N/g' \
    > "$DUMP_DIR/provider_map_collection_codes.tsv"
count=$(wc -l < "$DUMP_DIR/provider_map_collection_codes.tsv" | tr -d ' ')
if [ "$count" -gt 0 ]; then
    eval "$PG_CMD -c \"TRUNCATE TABLE provider_map_collection_codes;\"" 2>/dev/null || true
    eval "$PG_CMD -c \"\\COPY provider_map_collection_codes(provider_map_id,provider_code_id) FROM '$DUMP_DIR/provider_map_collection_codes.tsv' WITH (FORMAT text, NULL '\\\\N');\"" 2>/dev/null
fi
echo "$count rows"

echo -n "  Migrating provider_map_provider_code → provider_map_institution_codes... "
$MYSQL_CMD -e "SELECT IFNULL(provider_map_institution_codes_id, '\\\\N'), IFNULL(provider_code_id, '\\\\N') FROM provider_map_provider_code WHERE provider_map_institution_codes_id IS NOT NULL" 2>/dev/null \
    | sed 's/\\\\N/\\N/g' \
    > "$DUMP_DIR/provider_map_institution_codes.tsv"
count=$(wc -l < "$DUMP_DIR/provider_map_institution_codes.tsv" | tr -d ' ')
if [ "$count" -gt 0 ]; then
    eval "$PG_CMD -c \"TRUNCATE TABLE provider_map_institution_codes;\"" 2>/dev/null || true
    eval "$PG_CMD -c \"\\COPY provider_map_institution_codes(provider_map_id,provider_code_id) FROM '$DUMP_DIR/provider_map_institution_codes.tsv' WITH (FORMAT text, NULL '\\\\N');\"" 2>/dev/null
fi
echo "$count rows"

echo ""
echo "--- Step 4: Large tables (audit_log, activity_log) ---"

migrate_table "audit_log" \
    "id,actor,class_name,date_created,event_name,last_updated,new_value,old_value,persisted_object_id,persisted_object_version,property_name,uri"

echo -n "  Migrating activity_log (large table, may take a moment)... "

# For activity_log, dump directly and load — it's large (7M+ rows)
$MYSQL_CMD -e "SELECT IFNULL(CAST(id AS CHAR), '\\\\N'), IFNULL(CAST(version AS CHAR), '\\\\N'), IFNULL(action, '\\\\N'), IFNULL(CAST(admin AS CHAR), '\\\\N'), IFNULL(CAST(administrator_for_entity AS CHAR), '\\\\N'), IFNULL(CAST(contact_for_entity AS CHAR), '\\\\N'), IFNULL(entity_uid, '\\\\N'), IFNULL(CAST(timestamp AS CHAR), '\\\\N'), IFNULL(\`user\`, '\\\\N') FROM activity_log" 2>/dev/null \
    | sed 's/\\\\N/\\N/g' \
    > "$DUMP_DIR/activity_log.tsv"

count=$(wc -l < "$DUMP_DIR/activity_log.tsv" | tr -d ' ')
eval "$PG_CMD -c \"TRUNCATE TABLE activity_log CASCADE;\"" 2>/dev/null || true
eval "$PG_CMD -c \"\\COPY activity_log(id,version,action,admin,administrator_for_entity,contact_for_entity,entity_uid,\\\"timestamp\\\",\\\"user\\\") FROM '$DUMP_DIR/activity_log.tsv' WITH (FORMAT text, NULL '\\\\N');\"" 2>/dev/null
echo "$count rows"

# ---------------------------------------------------------------------------
# Step 5: Reset PostgreSQL sequences to match max IDs
# ---------------------------------------------------------------------------
echo ""
echo "--- Step 5: Reset sequences ---"

TABLES_WITH_SEQ="institution collection contact contact_for data_provider data_resource data_hub temp_data_resource external_identifier attribution audit_log licence provider_map provider_code activity_log data_link"

for table in $TABLES_WITH_SEQ; do
    seq_name="${table}_id_seq"
    max_id=$(eval "$PG_CMD -t -A -c \"SELECT COALESCE(MAX(id), 0) FROM \\\"$table\\\";\"" 2>/dev/null | tr -d ' ')
    if [ "$max_id" -gt 0 ]; then
        eval "$PG_CMD -c \"SELECT setval('${seq_name}', $max_id);\"" 2>/dev/null > /dev/null
        echo "  $table: sequence → $max_id"
    fi
done

# Also reset the sequence table's own sequence
seq_max=$(eval "$PG_CMD -t -A -c \"SELECT COALESCE(MAX(id), 0) FROM \\\"sequence\\\";\"" 2>/dev/null | tr -d ' ')
if [ "$seq_max" -gt 0 ]; then
    eval "$PG_CMD -c \"SELECT setval('sequence_id_seq', $seq_max);\"" 2>/dev/null > /dev/null
    echo "  sequence: sequence → $seq_max"
fi

# ---------------------------------------------------------------------------
# Step 6: Verify row counts
# ---------------------------------------------------------------------------
echo ""
echo "--- Step 6: Verification ---"
echo ""
printf "%-40s %10s %10s %s\n" "TABLE" "MYSQL" "PG" "STATUS"
printf "%-40s %10s %10s %s\n" "-----" "-----" "--" "------"

VERIFY_TABLES="institution collection contact contact_for data_provider data_resource data_hub temp_data_resource external_identifier attribution audit_log licence provider_map provider_code activity_log data_link"

all_ok=true
for table in $VERIFY_TABLES; do
    mysql_count=$($MYSQL_CMD -e "SELECT COUNT(*) FROM \`$table\`" 2>/dev/null | tr -d ' ')
    pg_count=$(eval "$PG_CMD -t -A -c \"SELECT COUNT(*) FROM \\\"$table\\\";\"" 2>/dev/null | tr -d ' ')

    if [ "$mysql_count" = "$pg_count" ]; then
        status="OK"
    else
        status="MISMATCH"
        all_ok=false
    fi
    printf "%-40s %10s %10s %s\n" "$table" "$mysql_count" "$pg_count" "$status"
done

echo ""
if $all_ok; then
    echo "=== Migration completed successfully! All row counts match. ==="
else
    echo "=== WARNING: Some row counts do not match. Check the tables above. ==="
fi

# Cleanup
rm -rf "$DUMP_DIR"
