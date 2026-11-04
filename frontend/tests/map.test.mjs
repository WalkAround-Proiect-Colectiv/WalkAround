import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PLACES, MIN_ZOOM, MAX_ZOOM, isInCoverage, MAP_STYLE_URL } from '../src/map/config.ts';
import { resolveStyleResources, styleMap } from '../src/map/style.ts';
import { readPoi, selectMapHit } from '../src/map/poi.ts';

test('Romania camera presets use longitude/latitude inside supported bounds', () => {
  for (const place of PLACES) {
    assert.ok(isInCoverage(...place.center), place.name);
    assert.ok(place.zoom >= MIN_ZOOM && place.zoom <= MAX_ZOOM);
  }
});

test('coverage rejects out-of-region or invalid coordinates', () => {
  assert.equal(isInCoverage(0, 0), false);
  assert.equal(isInCoverage(NaN, 45), false);
  assert.equal(isInCoverage(45, 26), false);
  assert.ok(MAP_STYLE_URL.startsWith('https://') || MAP_STYLE_URL.startsWith('http://'));
});

test('flat map style removes relief and extrusions without mutating the source', () => {
  const original = {
    version: 8,
    sources: { base: { type: 'vector', tiles: ['https://example.test/{z}/{x}/{y}.pbf'] }, relief: { type: 'raster-dem', tiles: [] } },
    terrain: { source: 'relief' },
    layers: [
      { id: 'background', type: 'background' },
      { id: 'water', type: 'fill', source: 'base', 'source-layer': 'water' },
      { id: 'building', type: 'fill', source: 'base', 'source-layer': 'building', maxzoom: 14 },
      { id: 'building-3d', type: 'fill-extrusion', source: 'base', 'source-layer': 'building' },
      { id: 'shading', type: 'hillshade', source: 'relief' },
    ],
  };
  const styled = styleMap(original);
  assert.equal(styled.terrain, undefined);
  assert.equal(styled.sources.relief, undefined);
  assert.deepEqual(styled.layers.map(layer => layer.id), ['background', 'water', 'building']);
  assert.equal(styled.layers[1].paint['fill-color'], '#a8d7f0');
  assert.equal(styled.layers[2].maxzoom, undefined);
  assert.equal(original.layers.length, 5);
  assert.equal(original.layers[2].maxzoom, 14);
});

test('relative style resources resolve against the tile gateway rather than the app origin', () => {
  const original = { version: 8, glyphs: '/font/{fontstack}/{range}', sprite: './sprites/base',
    sources: { base: { type: 'vector', tiles: ['/tiles/basemap/{z}/{x}/{y}'] }, metadata: { type: 'vector', url: '/tiles/basemap' } }, layers: [] };
  const result = resolveStyleResources(original, 'http://192.168.1.50:3001/style.json');
  assert.equal(result.sources.base.tiles[0], 'http://192.168.1.50:3001/tiles/basemap/{z}/{x}/{y}');
  assert.equal(result.sources.metadata.url, 'http://192.168.1.50:3001/tiles/basemap');
  assert.equal(result.glyphs, 'http://192.168.1.50:3001/font/{fontstack}/{range}');
  assert.equal(result.sprite, 'http://192.168.1.50:3001/sprites/base');
  assert.equal(original.sources.base.tiles[0], '/tiles/basemap/{z}/{x}/{y}');
});

test('absolute hosted resources and sprite arrays remain supported', () => {
  const result = resolveStyleResources({ version: 8, sources: { base: { type: 'vector', url: 'https://tiles.example.test/source' } },
    sprite: [{ id: 'base', url: '/sprites/base' }], layers: [] }, 'https://maps.example.test/style.json');
  assert.equal(result.sources.base.url, 'https://tiles.example.test/source');
  assert.equal(result.sprite[0].url, 'https://maps.example.test/sprites/base');
});

test('POI selection accepts public display fields and ignores provider payloads', () => {
  const poi = readPoi({ poi_id: 'canonical-id', name: ' Place ', category: 'food_drink', sources: 'OSM',
    attribution: 'Contributors', licenses: 'ODbL-1.0', raw_payload: { secret: 'must-not-copy' } });
  assert.deepEqual(poi, { id: 'canonical-id', name: 'Place', category: 'food drink', sources: 'OSM', attribution: 'Contributors', licenses: 'ODbL-1.0' });
  assert.equal(readPoi(null), null);
  assert.equal(readPoi({ name: 'Map label', category: 'tourism' }), null);
  assert.equal(readPoi({ poi_id: 'id', name: 42, category: 'other' }), null);
});

test('a POI inside a danger zone takes selection priority', () => {
  const hit = selectMapHit([{ properties: { poi_id: 'id', name: 'Cafe', category: 'food_drink' } }],
    [{ properties: { h3Index: 'zone', intensity: 3, topCategories: ['Theft'] } }]);
  assert.equal(hit.poi?.name, 'Cafe');
  assert.equal(hit.zone, null);
});

test('zone-only selection handles web JSON categories and native arrays', () => {
  for (const topCategories of [['Theft'], '["Theft"]']) {
    const hit = selectMapHit([], [{ properties: { h3Index: 'zone', intensity: 3, topCategories } }]);
    assert.equal(hit.poi, null);
    assert.deepEqual(hit.zone, { h3Index: 'zone', intensity: 3, categories: ['Theft'] });
  }
});

test('empty map and malformed categories do not leave a stale or invalid selection', () => {
  assert.deepEqual(selectMapHit([], []), { poi: null, zone: null });
  assert.deepEqual(selectMapHit([], [{ properties: null }]), { poi: null, zone: null });
  assert.deepEqual(selectMapHit([], [{ properties: { h3Index: 'zone', intensity: 2, topCategories: 'bad JSON' } }]).zone?.categories, []);
});