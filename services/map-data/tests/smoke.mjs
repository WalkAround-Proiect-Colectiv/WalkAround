import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const project = `walkaround-map-smoke-${randomBytes(4).toString('hex')}`;
const environment = { ...process.env, MAP_DB_ADMIN_PASSWORD: randomBytes(24).toString('hex'),
  MAP_IMPORT_PASSWORD: randomBytes(24).toString('hex'), MAP_TILES_PASSWORD: randomBytes(24).toString('hex'),
  MAP_TILES_PORT: '0', MAP_BIND_ADDRESS: '127.0.0.1', OSM_INPUT: '/workspace/tests/fixture.osm' };
const composeArgs = ['compose', '--env-file', `${root}.env.example`, '-f', `${root}compose.yaml`, '-p', project, '--profile', 'maintenance'];
function docker(args, capture = false) {
  const result = spawnSync('docker', args, { env: environment, encoding: 'utf8', stdio: capture ? 'pipe' : 'inherit' });
  if (result.status !== 0) throw new Error(capture ? result.stderr || 'Docker is unavailable' : `Docker exited with ${result.status}`);
  return result.stdout?.trim();
}
function compose(args, capture = false) { return docker([...composeArgs, ...args], capture); }
function coordinate(longitude, latitude, zoom) {
  return [Math.floor((longitude + 180) / 360 * 2 ** zoom), Math.floor((1 - Math.asinh(Math.tan(latitude * Math.PI / 180)) / Math.PI) / 2 * 2 ** zoom)];
}
const requireFrontend = createRequire(new URL('../../../frontend/package.json', import.meta.url));
const { VectorTile } = requireFrontend('@mapbox/vector-tile');
const { PbfReader } = requireFrontend('pbf');
async function tile(base, zoom) {
  const [tileX, tileY] = coordinate(26.1025, 44.4268, zoom);
  const response = await fetch(`${base}/tiles/basemap/${zoom}/${tileX}/${tileY}`, { signal: AbortSignal.timeout(30000) });
  assert.equal(response.status, 200);
  const decoded = new VectorTile(new PbfReader(new Uint8Array(await response.arrayBuffer())));
  const layer = decoded.layers.features;
  assert.ok(layer, 'The tile must contain the features vector layer');
  return Array.from({ length: layer.length }, (_, index) => layer.feature(index).properties);
}

docker(['info', '--format', '{{.ServerVersion}}'], true);
try {
  compose(['up', '-d', '--wait', 'map-db']);
  compose(['run', '--build', '--rm', '--no-deps', 'map-job', 'test-style']);
  compose(['run', '--rm', '--no-deps', 'map-job', 'import']);
  compose(['up', '--build', '-d', '--wait', 'map-gateway']);
  compose(['run', '--rm', '--no-deps', 'map-job', 'verify']);
  const address = compose(['port', 'map-gateway', '8080'], true);
  const base = `http://${address}`;
  const style = await (await fetch(`${base}/style.json`)).json();
  assert.deepEqual(style.sources.basemap.tiles, ['/tiles/basemap/{z}/{x}/{y}']);
  const font = await fetch(`${base}/font/Noto%20Sans%20Regular/0-255`);
  assert.equal(font.status, 200);
  assert.ok((await font.arrayBuffer()).byteLength > 0);
  const before = await tile(base, 15);
  for (const kind of ['road', 'building', 'water', 'waterway', 'landcover', 'place', 'poi']) {
    assert.ok(before.some(feature => feature.kind === kind), `Missing ${kind}`);
  }
  assert.ok(!(await tile(base, 5)).some(feature => ['building', 'poi'].includes(feature.kind)));
  compose(['run', '--rm', '--no-deps', 'map-job', 'append-fixture']);
  const after = await tile(base, 15);
  assert.ok(after.some(feature => feature.name === 'Updated fixture road'));
  assert.ok(after.some(feature => feature.name === 'Fixture pharmacy'));
  assert.ok(!after.some(feature => feature.name === 'Fixture cafe'));
  assert.ok(!after.some(feature => feature.name === 'Fixture road'));
  console.log('Import, permissions, tiles, fonts, zoom filtering, and create/modify/delete updates passed.');
} finally {
  compose(['down', '--volumes', '--remove-orphans']);
}