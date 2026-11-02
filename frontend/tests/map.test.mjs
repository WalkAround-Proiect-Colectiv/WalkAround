import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PLACES, MIN_ZOOM, MAX_ZOOM, isInCoverage, MAP_STYLE_URL } from '../src/map/config.ts';
import { resolveStyleResources, styleMap } from '../src/map/style.ts';

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