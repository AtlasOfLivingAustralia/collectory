#!/bin/bash

set -e

if [ -n "$DB_DUMP_EXISTS" ]; then
  mysql -u root -p"$MYSQL_ROOT_PASSWORD" < "$DB_DUMP_EXISTS"
fi
