#!/usr/bin/env python3
"""
Migrate data from legacy MySQL collectory database to PostgreSQL.

Prerequisites:
  - MySQL container running on localhost:3306
  - PostgreSQL container running on localhost:5432
  - Both databases named 'collectory' with user 'collectory_user' / 'password'
  - PostgreSQL schema already created by Flyway (V1–V3)

Usage: python3 scripts/migrate_mysql_to_pg.py
"""

import mysql.connector
import psycopg2
import psycopg2.extras
import sys
from datetime import datetime

MYSQL_CONFIG = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'collectory_user',
    'password': 'password',
    'database': 'collectory',
}

PG_CONFIG = {
    'host': '127.0.0.1',
    'port': 5432,
    'user': 'collectory_user',
    'password': 'password',
    'database': 'collectory',
}

# Tables to migrate in dependency order.
# Each entry: (table_name, [columns], {optional overrides})
# If columns is None, we auto-detect from PostgreSQL schema.
TABLES = [
    # Core entities (order matters for FK constraints)
    'institution',
    'collection',
    'contact',
    'contact_for',
    'data_provider',
    'data_resource',
    'data_hub',
    'temp_data_resource',
    # Supporting entities
    'external_identifier',
    'attribution',
    'licence',
    'provider_map',
    'provider_code',
    'data_link',
    'sequence',
    # Join tables
    'collection_external_identifier',
    'data_provider_external_identifier',
    'data_resource_external_identifier',
    'institution_external_identifier',
    'data_hub_external_identifier',
    'data_provider_collection',
    'data_provider_institution',
    'data_resource_institution',
    'data_resource_collection',
    # Large tables last
    'audit_log',
    'activity_log',
]

# MySQL BIT/TINYINT columns that need boolean conversion
BOOLEAN_COLUMNS = {
    'isalapartner', 'publish', 'administrator', 'notify', 'primary_contact',
    'admin', 'administrator_for_entity', 'contact_for_entity',
    'filed', 'risk_assessment', 'contributor', 'gbif_dataset',
    'is_shareable_withgbif', 'make_contact_public', 'public_archive_available',
    'is_private', 'exact', 'match_any_collection_code', 'is_contact_public',
}

# MySQL provider_map join table mapping
# MySQL: provider_map_provider_code (provider_map_collection_codes_id, provider_code_id, provider_map_institution_codes_id)
# PG: provider_map_collection_codes (provider_map_id, provider_code_id)
#     provider_map_institution_codes (provider_map_id, provider_code_id)
PROVIDER_MAP_JOIN = True

# Default values for NOT NULL columns that may be NULL in MySQL.
# Format: (table, column) → default_value
NOT_NULL_DEFAULTS = {
    ('data_resource', 'status'): 'identified',
    ('data_resource', 'resource_type'): 'records',
    ('data_resource', 'download_limit'): 0,
    ('data_resource', 'harvest_frequency'): 0,
    ('data_resource', 'name'): '',
    ('data_resource', 'uid'): '',
    ('data_resource', 'user_last_modified'): '',
    ('data_resource', 'version'): 0,
    ('collection', 'east_coordinate'): 0,
    ('collection', 'north_coordinate'): 0,
    ('collection', 'south_coordinate'): 0,
    ('collection', 'west_coordinate'): 0,
    ('collection', 'num_records'): 0,
    ('collection', 'num_records_digitised'): 0,
    ('institution', 'latitude'): 0,
    ('institution', 'longitude'): 0,
    ('data_provider', 'latitude'): 0,
    ('data_provider', 'longitude'): 0,
    ('data_resource', 'latitude'): 0,
    ('data_resource', 'longitude'): 0,
    ('data_hub', 'latitude'): 0,
    ('data_hub', 'longitude'): 0,
    ('collection', 'latitude'): 0,
    ('collection', 'longitude'): 0,
    ('temp_data_resource', 'number_of_records'): 0,
    ('contact_for', 'date_created'): datetime(2000, 1, 1),
    ('contact_for', 'date_last_modified'): datetime(2000, 1, 1),
}


def get_pg_columns(pg_cur, table_name):
    """Get column names from PostgreSQL table."""
    pg_cur.execute("""
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = %s
        ORDER BY ordinal_position
    """, (table_name,))
    return [row[0] for row in pg_cur.fetchall()]


def get_mysql_columns(my_cur, table_name):
    """Get column names from MySQL table."""
    my_cur.execute("""
        SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'collectory' AND TABLE_NAME = %s
        ORDER BY ORDINAL_POSITION
    """, (table_name,))
    return [row[0] for row in my_cur.fetchall()]


def convert_value(val, col_name, table_name):
    """Convert a MySQL value to PostgreSQL-compatible value."""
    if val is None:
        # Apply default for NOT NULL columns
        default = NOT_NULL_DEFAULTS.get((table_name, col_name))
        return default  # None if no default defined

    # BIT/TINYINT → boolean
    if col_name in BOOLEAN_COLUMNS:
        if isinstance(val, (bytes, bytearray)):
            return val != b'\x00'
        return bool(val)

    # bytes → decode
    if isinstance(val, (bytes, bytearray)):
        try:
            return val.decode('utf-8')
        except UnicodeDecodeError:
            return val.decode('latin-1')

    return val


def migrate_table(my_conn, pg_conn, pg_cur, table_name, batch_size=5000):
    """Migrate a single table from MySQL to PostgreSQL."""
    # Use a separate buffered cursor for metadata queries
    meta_cur = my_conn.cursor(buffered=True)

    pg_cols = get_pg_columns(pg_cur, table_name)
    my_cols = get_mysql_columns(meta_cur, table_name)
    meta_cur.close()

    if not pg_cols:
        print(f"  {table_name}: skipped (no PG table)")
        return 0

    # Use intersection, preserving PG column order
    common_cols = [c for c in pg_cols if c in my_cols]

    if not common_cols:
        print(f"  {table_name}: skipped (no common columns)")
        return 0

    # Truncate PG table
    pg_cur.execute(f'TRUNCATE TABLE "{table_name}" CASCADE')
    pg_conn.commit()

    # Read from MySQL using a streaming (unbuffered) cursor for large tables
    data_cur = my_conn.cursor(buffered=False)
    col_list = ', '.join(f'`{c}`' for c in common_cols)
    data_cur.execute(f"SELECT {col_list} FROM `{table_name}`")

    total = 0
    placeholders = ', '.join(['%s'] * len(common_cols))
    col_names = ', '.join(f'"{c}"' for c in common_cols)
    insert_sql = f'INSERT INTO "{table_name}" ({col_names}) VALUES ({placeholders})'

    while True:
        rows = data_cur.fetchmany(batch_size)
        if not rows:
            break

        # Convert values
        converted = []
        for row in rows:
            converted.append(tuple(
                convert_value(val, common_cols[i], table_name)
                for i, val in enumerate(row)
            ))

        # Insert into PostgreSQL
        psycopg2.extras.execute_batch(pg_cur, insert_sql, converted, page_size=1000)
        pg_conn.commit()

        total += len(converted)

    data_cur.close()
    return total


def migrate_provider_map_join(my_conn, pg_conn, pg_cur):
    """Migrate provider_map_provider_code → two PG tables."""
    my_cur = my_conn.cursor(buffered=True)

    # Collection codes
    pg_cur.execute('TRUNCATE TABLE provider_map_collection_codes')
    my_cur.execute("""
        SELECT DISTINCT provider_map_collection_codes_id, provider_code_id
        FROM provider_map_provider_code
        WHERE provider_map_collection_codes_id IS NOT NULL
    """)
    rows = my_cur.fetchall()
    if rows:
        psycopg2.extras.execute_batch(
            pg_cur,
            'INSERT INTO provider_map_collection_codes (provider_map_id, provider_code_id) VALUES (%s, %s)',
            rows, page_size=1000
        )
    pg_conn.commit()
    coll_count = len(rows)

    # Institution codes
    pg_cur.execute('TRUNCATE TABLE provider_map_institution_codes')
    my_cur.execute("""
        SELECT DISTINCT provider_map_institution_codes_id, provider_code_id
        FROM provider_map_provider_code
        WHERE provider_map_institution_codes_id IS NOT NULL
    """)
    rows = my_cur.fetchall()
    if rows:
        psycopg2.extras.execute_batch(
            pg_cur,
            'INSERT INTO provider_map_institution_codes (provider_map_id, provider_code_id) VALUES (%s, %s)',
            rows, page_size=1000
        )
    pg_conn.commit()
    inst_count = len(rows)

    my_cur.close()
    return coll_count, inst_count


def reset_sequences(pg_conn, pg_cur):
    """Reset PostgreSQL sequences to max(id) + 1 for each table."""
    tables_with_id = [
        'institution', 'collection', 'contact', 'contact_for',
        'data_provider', 'data_resource', 'data_hub', 'temp_data_resource',
        'external_identifier', 'attribution', 'audit_log', 'licence',
        'provider_map', 'provider_code', 'activity_log', 'data_link',
        'sequence',
    ]
    for table in tables_with_id:
        seq_name = f'{table}_id_seq'
        pg_cur.execute(f'SELECT COALESCE(MAX(id), 0) FROM "{table}"')
        max_id = pg_cur.fetchone()[0]
        if max_id > 0:
            pg_cur.execute(f"SELECT setval('{seq_name}', {max_id})")
            print(f"  {table}: sequence → {max_id}")
    pg_conn.commit()


def verify(my_conn, pg_cur):
    """Compare row counts between MySQL and PostgreSQL."""
    my_cur = my_conn.cursor(buffered=True)

    print(f"\n{'TABLE':<40} {'MYSQL':>10} {'PG':>10} STATUS")
    print(f"{'─' * 40} {'─' * 10} {'─' * 10} {'─' * 8}")

    all_ok = True
    for table in TABLES:
        my_cur.execute(f"SELECT COUNT(*) FROM `{table}`")
        my_count = my_cur.fetchone()[0]

        pg_cur.execute(f'SELECT COUNT(*) FROM "{table}"')
        pg_count = pg_cur.fetchone()[0]

        status = 'OK' if my_count == pg_count else 'MISMATCH'
        if status == 'MISMATCH':
            all_ok = False
        print(f"  {table:<38} {my_count:>10} {pg_count:>10} {status}")

    my_cur.close()
    return all_ok


def main():
    print("=== Collectory MySQL → PostgreSQL Data Migration ===\n")

    # Connect
    print("Connecting to databases...")
    my_conn = mysql.connector.connect(**MYSQL_CONFIG)
    pg_conn = psycopg2.connect(**PG_CONFIG)
    pg_cur = pg_conn.cursor()

    # Disable FK checks during migration
    pg_cur.execute("SET session_replication_role = 'replica'")
    pg_conn.commit()

    try:
        # Migrate tables
        print("\n--- Migrating tables ---")
        for table in TABLES:
            start = datetime.now()
            count = migrate_table(my_conn, pg_conn, pg_cur, table)
            elapsed = (datetime.now() - start).total_seconds()
            suffix = f" ({elapsed:.1f}s)" if elapsed > 1 else ""
            print(f"  {table}: {count:,} rows{suffix}")

        # Migrate provider_map join table (MySQL → two PG tables)
        print("\n--- Migrating provider_map join tables ---")
        coll_count, inst_count = migrate_provider_map_join(my_conn, pg_conn, pg_cur)
        print(f"  provider_map_collection_codes: {coll_count} rows")
        print(f"  provider_map_institution_codes: {inst_count} rows")

        # Re-enable FK checks
        pg_cur.execute("SET session_replication_role = 'origin'")
        pg_conn.commit()

        # Reset sequences
        print("\n--- Resetting sequences ---")
        reset_sequences(pg_conn, pg_cur)

        # Verify
        print("\n--- Verification ---")
        all_ok = verify(my_conn, pg_cur)

        print()
        if all_ok:
            print("=== Migration completed successfully! All row counts match. ===")
        else:
            print("=== WARNING: Some row counts do not match. ===")
            sys.exit(1)

    finally:
        pg_cur.close()
        pg_conn.close()
        my_conn.close()


if __name__ == '__main__':
    main()
