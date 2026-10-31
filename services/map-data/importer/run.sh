#!/usr/bin/env bash
set -euo pipefail
operation="${1:-status}"
shift || true
export PGOPTIONS='-c search_path=osm_middle,public'
mkdir -p /locks
exec 9>/locks/import.lock
flock -n 9 || { echo 'Another map import/update job is running.' >&2; exit 1; }
query() { printf '%s;\n' "$1" | psql -X -v ON_ERROR_STOP=1 --set=feed="${OSM_REPLICATION_URL:-}" -At; }
cache="${OSM_CACHE_MB:-512}"
processes="${OSM_PROCESSES:-2}"
case "$operation" in
  download)
    test ! -e /data/region.osm.pbf || { echo 'An extract already exists; preserve it for replication initialization.' >&2; exit 1; }
    test "${OSM_EXTRACT_URL#https://}" != "$OSM_EXTRACT_URL" || { echo 'Extract URL must use HTTPS.' >&2; exit 1; }
    curl --fail --location --retry 3 "$OSM_EXTRACT_URL" --output /data/region.osm.pbf.part
    curl --fail --location --retry 3 "$OSM_EXTRACT_URL.md5" --output /data/region.md5
    read -r expected _ < /data/region.md5
    printf '%s  %s\n' "$expected" /data/region.osm.pbf.part | md5sum --check -
    mv /data/region.osm.pbf.part /data/region.osm.pbf
    ;;
  import)
    test -f "$OSM_INPUT" || { echo 'Download or mount an OSM input file first.' >&2; exit 1; }
    test "$(query "SELECT to_regclass('osm_middle.osm2pgsql_properties') IS NOT NULL")" = f || {
      echo 'Refusing to overwrite an existing import. Use update, or create a separate staging stack for a full rebuild.' >&2; exit 1;
    }
    osm2pgsql --create --slim --output=flex --style=/workspace/importer/flex.lua \
      --middle-schema=osm_middle --cache="$cache" --number-processes="$processes" \
      --disable-parallel-indexing "$OSM_INPUT"
    query 'ANALYZE osm.features' >/dev/null
    ;;
  replication-init)
    test -f "$OSM_INPUT" || { echo 'The original imported PBF is required.' >&2; exit 1; }
    test "$(query "SELECT EXISTS (SELECT 1 FROM osm_middle.osm2pgsql_properties WHERE property = 'replication_base_url' AND value <> :'feed')")" = f || {
      echo 'Replication feed differs from the imported extract; refusing to switch feeds.' >&2; exit 1;
    }
    if test "$(query "SELECT count(*) FROM osm_middle.osm2pgsql_properties WHERE property IN ('replication_base_url', 'replication_sequence_number', 'replication_timestamp') AND value <> ''")" = 3; then
      echo 'Existing replication checkpoint retained.'
    else
      osm2pgsql-replication init --database "$PGDATABASE" --middle-schema osm_middle --osm-file "$OSM_INPUT"
    fi
    test "$(query "SELECT EXISTS (SELECT 1 FROM osm_middle.osm2pgsql_properties WHERE property = 'replication_base_url' AND value = :'feed')")" = t || {
      echo 'PBF replication headers do not match the configured regional feed. Do not run updates.' >&2; exit 1;
    }
    ;;
  update)
    test "$(query "SELECT EXISTS (SELECT 1 FROM osm_middle.osm2pgsql_properties WHERE property = 'replication_base_url' AND value = :'feed')")" = t || {
      echo 'Initialize replication with the matching regional feed first.' >&2; exit 1;
    }
    osm2pgsql-replication update --database "$PGDATABASE" --middle-schema osm_middle --max-diff-size 100 -- \
      --cache="$cache" --number-processes="$processes"
    query 'ANALYZE osm.features' >/dev/null
    ;;
  status)
    query "SELECT property || '=' || value FROM osm_middle.osm2pgsql_properties WHERE property IN ('replication_base_url', 'replication_sequence_number', 'replication_timestamp', 'current_timestamp', 'updatable') ORDER BY property"
    ;;
  verify)
    psql -X -v ON_ERROR_STOP=1 -f /workspace/tests/verify.sql
    ;;
  test-style)
    lua5.4 /workspace/tests/flex.test.lua
    ;;
  append-fixture)
    test "${OSM_INPUT:-}" = /workspace/tests/fixture.osm || { echo 'Fixture updates require the fixture stack.' >&2; exit 1; }
    osm2pgsql --append --slim --middle-schema=osm_middle /workspace/tests/change.osc
    ;;
  *) echo 'Usage: map-job download|import|replication-init|update|status|verify|test-style|append-fixture' >&2; exit 2 ;;
esac