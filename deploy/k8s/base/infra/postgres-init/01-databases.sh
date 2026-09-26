#!/bin/sh
# One database and one owner per service. Services never share a database.
# Runs once, when the data volume is empty (docker-entrypoint-initdb.d).
set -eu

for service in ${SHOP_DATABASES}; do
  echo "Creating database and role for ${service}"
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname postgres <<SQL
CREATE ROLE ${service} LOGIN PASSWORD '${SHOP_DB_PASSWORD}';
CREATE DATABASE ${service} OWNER ${service};
REVOKE ALL ON DATABASE ${service} FROM PUBLIC;
SQL
done
