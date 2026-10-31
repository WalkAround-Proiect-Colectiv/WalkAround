#!/usr/bin/env bash
set -euo pipefail
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
  --set=import_password="$MAP_IMPORT_PASSWORD" --set=tiles_password="$MAP_TILES_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE ROLE map_import LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'import_password';
CREATE ROLE map_tiles LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'tiles_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA osm AUTHORIZATION map_import;
CREATE SCHEMA osm_middle AUTHORIZATION map_import;
CREATE SCHEMA tiles AUTHORIZATION map_import;
GRANT CONNECT ON DATABASE map TO map_import, map_tiles;
GRANT USAGE ON SCHEMA osm, tiles TO map_tiles;
ALTER DEFAULT PRIVILEGES FOR ROLE map_import IN SCHEMA osm GRANT SELECT ON TABLES TO map_tiles;
ALTER DEFAULT PRIVILEGES FOR ROLE map_import IN SCHEMA tiles REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER ROLE map_tiles SET default_transaction_read_only = on;
ALTER ROLE map_tiles SET statement_timeout = '15s';
SQL