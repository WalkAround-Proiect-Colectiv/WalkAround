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
  assert.ok(style.layers.every(layer => !layer.source || layer['source-layer'] === 'features'));
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