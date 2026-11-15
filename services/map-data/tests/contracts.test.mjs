import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

const requireFrontend = createRequire(new URL('../../../frontend/package.json', import.meta.url));
const { validateStyleMin } = requireFrontend('@maplibre/maplibre-gl-style-spec');
const read = path => readFile(new URL(path, import.meta.url), 'utf8');

test('the self-hosted style is valid and references only local map resources', async () => {
  const style = JSON.parse(await read('../gateway/style.json'));
  assert.deepEqual(validateStyleMin(style), []);
  assert.deepEqual(style.sources.basemap.tiles, ['/tiles/basemap/{z}/{x}/{y}']);
  assert.equal(style.glyphs, '/font/{fontstack}/{range}');
  assert.ok(style.sources.basemap.attribution.includes('openstreetmap.org/copyright'));
  assert.equal(style.sources.basemap.maxzoom, 15);
  assert.ok(style.layers.every(layer => !layer.source || layer['source-layer'] === (layer.source === 'pois' ? 'pois' : 'features')));
  assert.deepEqual(style.sources.pois.tiles, ['/tiles/pois/{z}/{x}/{y}']);
  assert.ok(style.layers.filter(layer => ['poi-dot', 'poi-label'].includes(layer.id)).every(layer => layer.source === 'pois'));
  assert.ok(style.layers.filter(layer => ['poi-cluster', 'poi-cluster-count'].includes(layer.id)).every(layer => layer.source === 'basemap'));
  assert.ok(style.layers.every(layer => !['hillshade', 'fill-extrusion', 'raster'].includes(layer.type)));
});

test('Martin and the tile function share their explicit, versioned schema contract', async () => {
  const sql = await read('../database/migrations/V1__basemap_tiles.sql');
  const config = await read('../martin.yaml');
  assert.match(sql, /FUNCTION tiles\.basemap\(z integer, x integer, y integer\)/);
  assert.match(sql, /LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE/);
  assert.match(sql, /geom && buffered AND minzoom <= z/);
  assert.match(sql, /ST_AsMVT\(tile, 'features'/);
  assert.match(config, /auto_publish: false/);
  assert.match(config, /cache: disable/);
  assert.match(config, /schema: tiles\s+function: basemap/);
});

test('POI model keeps manual enrichment separate from OSM-owned tables and limits map-role access', async () => {
  const sql = await read('../database/migrations/V3__poi_source_catalog_and_model.sql');
  for (const table of ['providers', 'pois', 'import_runs', 'poi_records', 'poi_links']) {
    assert.match(sql, new RegExp(`CREATE TABLE places\\.${table}\\b`));
  }
  assert.match(sql, /CHECK \(trigger_mode = 'manual'\)/);
  assert.match(sql, /UNIQUE \(provider_code, external_id\)/);
  assert.match(sql, /REFERENCES places\.import_runs\(id, provider_code\)/);
  assert.match(sql, /GRANT SELECT ON places\.pois TO map_tiles/);
  assert.match(sql, /GRANT SELECT \(code, name, documentation_url, license_id, license_url, terms_url, attribution, approval_status\)\s+ON places\.providers TO map_tiles/);
  assert.doesNotMatch(sql, /(?:ALTER|INSERT INTO|UPDATE|DELETE FROM) osm\./i);
  assert.doesNotMatch(sql, /CREATE FUNCTION tiles\.pois/);
  assert.match(sql, /'pending_review'/);
});

test('POI reconciliation preserves confirmed links and does not confirm proximity alone', async () => {
  const sql = await read('../database/migrations/V4__poi_reconciliation.sql');
  assert.match(sql, /provider\.approval_status = 'approved'/);
  assert.match(sql, /pg_advisory_xact_lock/);
  assert.match(sql, /'identifier_conflict'/);
  assert.match(sql, /'proximity_candidate', 'proposed'/);
  assert.match(sql, /PRIMARY KEY \(namespace, value\)/);
  assert.match(sql, /REVOKE ALL ON FUNCTION places\.reconcile_record\(uuid\) FROM PUBLIC/);
  assert.doesNotMatch(sql, /(?:INSERT INTO|UPDATE|DELETE FROM) osm\./i);
});

test('POI tile publication requires confirmed current approved sources and suppresses only explicit OSM identities', async () => {
  const sql = await read('../database/migrations/V5__published_poi_tiles.sql');
  assert.match(sql, /link\.match_status = 'confirmed'/);
  assert.match(sql, /record\.record_status = 'current'/);
  assert.match(sql, /provider\.approval_status = 'approved'/);
  assert.match(sql, /run\.status = 'succeeded'/);
  assert.match(sql, /record\.license_id = provider\.license_id/);
  assert.match(sql, /ARRAY\['name', 'category', 'geom'\]::text\[\] <@ link\.contributed_fields/);
  assert.match(sql, /places\.published_osm_references/);
  assert.match(sql, /LIMIT 500/);
  assert.match(sql, /ST_Transform\(poi\.geom, 3857\)/);
});

test('POI density uses a later migration and publishes zoom-aware OSM clusters', async () => {
  const sql = await read('../database/migrations/V6__poi_density_tiles.sql');
  assert.match(sql, /z BETWEEN 10 AND 14/);
  assert.match(sql, /'poi_cluster'/);
  assert.match(sql, /point_count/);
});
