import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { initializeEnvironment, localConfiguration, composeArguments, setupPlan, connectFrontend, gatewayUrl, verifyEndpoints } from '../manage.mjs';

async function temporaryModule(operation) {
  const directory = await mkdtemp(join(tmpdir(), 'walkaround-map-'));
  try {
    await writeFile(join(directory, '.env.example'), await readFile(new URL('../.env.example', import.meta.url)));
    await operation(directory);
  } finally { await rm(directory, { recursive: true, force: true }); }
}

test('initialization creates unique local credentials without overwriting existing configuration', async () => {
  await temporaryModule(async directory => {
    assert.equal(await initializeEnvironment(directory), true);
    const original = await readFile(join(directory, '.env'), 'utf8');
    const config = await localConfiguration(directory);
    const passwords = [config.MAP_DB_ADMIN_PASSWORD, config.MAP_IMPORT_PASSWORD, config.MAP_TILES_PASSWORD];
    assert.ok(passwords.every(password => /^[a-f0-9]{64}$/.test(password)));
    assert.equal(new Set(passwords).size, 3);
    assert.equal(await initializeEnvironment(directory), false);
    assert.equal(await readFile(join(directory, '.env'), 'utf8'), original);
  });
});

test('local settings reject placeholders without revealing their values', async () => {
  await temporaryModule(async directory => {
    await writeFile(join(directory, '.env'), await readFile(join(directory, '.env.example')));
    await assert.rejects(localConfiguration(directory), /Configure MAP_DB_ADMIN_PASSWORD locally/);
  });
});

test('Compose paths are independent of the caller working directory', () => {
  const directory = join(tmpdir(), 'directory with spaces');
  const args = composeArguments(['ps'], directory);
  assert.ok(args.includes(join(directory, '.env')));
  assert.ok(args.some(argument => argument.endsWith('docker-compose.yaml')));
  assert.equal(args.at(-1), 'ps');
  assert.ok(!args.some(argument => argument.includes('--volumes')));
});

test('setup plans reuse downloads/completed imports and refuse partial imports', () => {
  assert.deepEqual(setupPlan({ import: 'empty', snapshot: false }), ['download', 'import', 'replication-init']);
  assert.deepEqual(setupPlan({ import: 'empty', snapshot: true }), ['import', 'replication-init']);
  assert.deepEqual(setupPlan({ import: 'ready', snapshot: true }), ['replication-init']);
  assert.throws(() => setupPlan({ import: 'partial', snapshot: true }), /will not overwrite/);
  assert.throws(() => setupPlan({ import: 'unknown', snapshot: true }), /Unexpected/);
});

test('frontend connection changes only the map URL and is repeatable', async () => {
  await temporaryModule(async directory => {
    const filename = join(directory, '.env.local');
    const original = '# Local authentication\nEXPO_PUBLIC_API_BASE_URL=http://localhost:8080\nEXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=example-client\n';
    await writeFile(filename, original);
    await connectFrontend('http://127.0.0.1:3001/style.json', filename);
    await connectFrontend('http://127.0.0.1:3002/style.json', filename);
    const result = await readFile(filename, 'utf8');
    assert.ok(result.startsWith(original));
    assert.equal(result.match(/EXPO_PUBLIC_MAP_STYLE_URL=/g).length, 1);
    assert.ok(result.includes('EXPO_PUBLIC_MAP_STYLE_URL=http://127.0.0.1:3002/style.json'));
    assert.equal(gatewayUrl({ MAP_BIND_ADDRESS: '0.0.0.0', MAP_TILES_PORT: '3002' }), 'http://127.0.0.1:3002');
  });
});

test('local settings reject invalid ports and credentials used in connection URLs', async () => {
  await temporaryModule(async directory => {
    await initializeEnvironment(directory);
    const filename = join(directory, '.env');
    const original = await readFile(filename, 'utf8');
    await writeFile(filename, original.replace('MAP_TILES_PORT=3001', 'MAP_TILES_PORT=70000'));
    await assert.rejects(localConfiguration(directory), /MAP_TILES_PORT/);
    await writeFile(filename, original.replace(/^MAP_TILES_PASSWORD=.*$/m, 'MAP_TILES_PASSWORD=invalid@value'));
    await assert.rejects(localConfiguration(directory), /URL-safe/);
  });
});

test('verification requires local fonts, metadata, and nonempty city tile responses', async () => {
  let empty = true;
  let tileRequests = 0;
  const server = createServer((request, response) => {
    if (request.url === '/health') { response.end('OK'); return; }
    if (request.url === '/style.json') {
      response.setHeader('content-type', 'application/json');
      response.end(JSON.stringify({ version: 8, sources: { basemap: { tiles: ['/tiles/basemap/{z}/{x}/{y}'] }, pois: { tiles: ['/tiles/pois/{z}/{x}/{y}'] } } }));
      return;
    }
    if (request.url === '/tiles/basemap' || request.url === '/tiles/pois') { response.end(JSON.stringify({ tiles: [`${request.url}/{z}/{x}/{y}`] })); return; }
    response.setHeader('content-type', 'application/x-protobuf');
    if (request.url.startsWith('/font/')) { response.end('mock-glyph-response'); return; }
    tileRequests++;
    response.end(empty ? '' : 'mock-tile-response');
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    await assert.rejects(verifyEndpoints(base), /No map features returned for Bucharest/);
    empty = false;
    tileRequests = 0;
    await verifyEndpoints(base);
    assert.equal(tileRequests, 8);
  } finally { await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
});

test('synthetic OSM change objects remain ordered by type then ID across action blocks', async () => {
  const change = await readFile(new URL('./change.osc', import.meta.url), 'utf8');
  const objects = Array.from(change.matchAll(/<(node|way|relation)\b[^>]*\bid="(\d+)"/g), match => ({
    type: { node: 0, way: 1, relation: 2 }[match[1]], id: Number(match[2]),
  }));
  assert.ok(objects.length > 0);
  const ordered = [...objects].sort((left, right) => left.type - right.type || left.id - right.id);
  assert.deepEqual(objects, ordered);
});
