import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseEnv } from 'node:util';

export const moduleDirectory = dirname(fileURLToPath(import.meta.url));

export async function initializeEnvironment(directory = moduleDirectory) {
  const template = await readFile(join(directory, '.env.example'), 'utf8');
  const content = template.replace(/^MAP_(DB_ADMIN|IMPORT|TILES)_PASSWORD=.*$/gm,
    (_line, role) => `MAP_${role}_PASSWORD=${randomBytes(32).toString('hex')}`);
  try {
    await writeFile(join(directory, '.env'), content, { flag: 'wx', mode: 0o600 });
    return true;
  } catch (error) {
    if (error.code === 'EEXIST') return false;
    throw error;
  }
}

export async function localConfiguration(directory = moduleDirectory) {
  let content;
  try { content = await readFile(join(directory, '.env'), 'utf8'); }
  catch (error) {
    if (error.code === 'ENOENT') throw new Error('Local map configuration is missing. Run: node services/map-data/manage.mjs init');
    throw error;
  }
  const config = parseEnv(content);
  for (const key of ['MAP_DB_ADMIN_PASSWORD', 'MAP_IMPORT_PASSWORD', 'MAP_TILES_PASSWORD']) {
    if (!config[key] || config[key].startsWith('replace_with_')) throw new Error(`Configure ${key} locally; never share its value.`);
  }
  if (!/^[A-Za-z0-9_-]+$/.test(config.MAP_TILES_PASSWORD)) throw new Error('MAP_TILES_PASSWORD must use URL-safe letters, numbers, underscores, or hyphens.');
  const port = config.MAP_TILES_PORT || '3001';
  if (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535) throw new Error('MAP_TILES_PORT must be between 1 and 65535.');
  return { ...config, MAP_TILES_PORT: port };
}

export function composeArguments(args, directory = moduleDirectory) {
  return ['compose', '--project-directory', directory, '--env-file', join(directory, '.env'),
    '-f', join(directory, 'compose.yaml'), '--profile', 'maintenance', ...args];
}

function execute(program, args, { capture = false, environment = process.env } = {}) {
  const result = spawnSync(program, args, { env: environment, encoding: 'utf8', stdio: capture ? 'pipe' : 'inherit' });
  if (result.error?.code === 'ENOENT') throw new Error(`${program} was not found. Install it and reopen your terminal.`);
  if (result.error) throw new Error(`Could not launch ${program}.`);
  if (result.status !== 0) throw new Error(`${program} failed. Check the preceding output; do not share credentials in logs.`);
  return result.stdout?.trim();
}

export function checkDocker() {
  let type;
  try { type = execute('docker', ['info', '--format', '{{.OSType}}'], { capture: true }); }
  catch { throw new Error('Docker engine is unavailable. Start Docker Desktop with Linux containers (or Docker Engine on Linux), then rerun this command.'); }
  if (type !== 'linux') throw new Error('This map stack requires Linux containers.');
  execute('docker', ['compose', 'version', '--short'], { capture: true });
}

export function setupPlan(state) {
  if (!['empty', 'ready', 'partial'].includes(state.import) || typeof state.snapshot !== 'boolean') throw new Error('Unexpected map import state.');
  if (state.import === 'partial') throw new Error('A partial or unmarked import exists. Setup will not overwrite it. Use a separate staging stack and follow the recovery guide.');
  const jobs = [];
  if (state.import === 'empty') {
    if (!state.snapshot) jobs.push('download');
    jobs.push('import');
  }
  jobs.push('replication-init');
  return jobs;
}

export function gatewayUrl(config) {
  const bind = config.MAP_BIND_ADDRESS || '127.0.0.1';
  const host = ['0.0.0.0', '127.0.0.1', 'localhost'].includes(bind) ? '127.0.0.1' : bind;
  return `http://${host}:${config.MAP_TILES_PORT}`;
}

export async function connectFrontend(styleUrl, filename = resolve(moduleDirectory, '../../frontend/.env.local')) {
  const parsed = new URL(styleUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('The map style must use HTTP or HTTPS.');
  let existing = '';
  try { existing = await readFile(filename, 'utf8'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const old = parseEnv(existing).EXPO_PUBLIC_MAP_STYLE_URL;
  if (old?.includes('\n')) throw new Error('The existing map URL is multiline. Update that setting locally instead.');
  const setting = `EXPO_PUBLIC_MAP_STYLE_URL=${parsed.href}`;
  const pattern = /^(?:export\s+)?EXPO_PUBLIC_MAP_STYLE_URL\s*=.*$/gm;
  const updated = pattern.test(existing) ? existing.replace(pattern, setting) : `${existing}${existing && !existing.endsWith('\n') ? '\n' : ''}${setting}\n`;
  await writeFile(filename, updated, { mode: 0o600 });
}

async function response(url) {
  const result = await fetch(url, { signal: AbortSignal.timeout(30000), cache: 'no-store' });
  if (!result.ok) throw new Error(`Map endpoint returned HTTP ${result.status}: ${new URL(url).pathname}`);
  return result;
}

export async function verifyEndpoints(base) {
  await response(`${base}/health`);
  const style = await (await response(`${base}/style.json`)).json();
  if (style.version !== 8 || style.sources?.basemap?.tiles?.[0] !== '/tiles/basemap/{z}/{x}/{y}') throw new Error('Unexpected self-hosted map style.');
  if (style.sources?.pois?.tiles?.[0] !== '/tiles/pois/{z}/{x}/{y}') throw new Error('The self-hosted POI source is missing.');
  const metadata = await (await response(`${base}/tiles/basemap`)).json();
  if (!Array.isArray(metadata.tiles) || !metadata.tiles.length) throw new Error('Martin did not publish basemap tile metadata.');
  const poiMetadata = await (await response(`${base}/tiles/pois`)).json();
  if (!Array.isArray(poiMetadata.tiles) || !poiMetadata.tiles.length) throw new Error('Martin did not publish POI tile metadata.');
  const glyphs = await (await response(`${base}/font/Noto%20Sans%20Regular/0-255`)).arrayBuffer();
  if (!glyphs.byteLength) throw new Error('The local map font response is empty.');
  for (const [name, longitude, latitude] of [['Bucharest', 26.1025, 44.4268], ['Cluj-Napoca', 23.5899, 46.7712], ['Brasov', 25.6012, 45.6579], ['Constanta', 28.6348, 44.1598]]) {
    const zoom = 14;
    const tileX = Math.floor((longitude + 180) / 360 * 2 ** zoom);
    const tileY = Math.floor((1 - Math.asinh(Math.tan(latitude * Math.PI / 180)) / Math.PI) / 2 * 2 ** zoom);
    const tile = await response(`${base}/tiles/basemap/${zoom}/${tileX}/${tileY}`);
    if (!/protobuf|mapbox-vector-tile/.test(tile.headers.get('content-type') || '')) throw new Error(`Unexpected tile format for ${name}.`);
    if (!(await tile.arrayBuffer()).byteLength) throw new Error(`No map features returned for ${name}. Check the imported region.`);
    const poiTile = await response(`${base}/tiles/pois/${zoom}/${tileX}/${tileY}`);
    if (poiTile.status !== 204 && !/protobuf|mapbox-vector-tile/.test(poiTile.headers.get('content-type') || '')) throw new Error(`Unexpected POI tile format for ${name}.`);
  }
}

export async function main(args = process.argv.slice(2)) {
  const command = args[0] || 'help';
  if (args.length > 1) throw new Error('Only one command is accepted; run help for usage.');
  if (command === 'help' || command === '--help') {
    console.log('Usage: node services/map-data/manage.mjs init|doctor|smoke|setup|start|stop|update|status|verify|connect|reconcile-pois|help');
    console.log('setup downloads/imports Romania once; reruns preserve a completed import. stop preserves all data.');
    console.log('connect changes only frontend/.env.local after map verification. Restart Expo afterward.');
    return;
  }
  if (command === 'init') {
    console.log(await initializeEnvironment() ? 'Created ignored local map configuration with unique credentials.' : 'Existing local map configuration preserved.');
    return;
  }
  if (command === 'doctor') { checkDocker(); console.log('Docker Linux engine and Compose are available.'); return; }
  if (!['smoke', 'setup', 'start', 'stop', 'update', 'status', 'verify', 'connect', 'reconcile-pois'].includes(command)) throw new Error(`Unknown command: ${command}`);
  checkDocker();
  if (command === 'smoke') {
    execute(process.execPath, [join(moduleDirectory, 'tests/smoke.mjs')]);
    return;
  }
  if (command === 'setup') await initializeEnvironment();
  const config = await localConfiguration();
  const environment = { ...process.env, ...config };
  const compose = (steps, capture = false) => execute('docker', composeArguments(steps), { capture, environment });
  const job = (name, capture = false) => compose(['run', '--rm', '--no-deps', '-T', 'map-job', name], capture);
  const base = gatewayUrl(config);
  const verify = async () => {
    job('verify');
    await verifyEndpoints(base);
    console.log('Database checks, style/fonts, and basemap/POI endpoints for four Romania cities passed.');
  };
  compose(['config', '--quiet']);
  if (command === 'stop') { compose(['stop']); console.log('Map containers stopped. Volumes and credentials are preserved.'); return; }
  if (command === 'status') {
    compose(['ps']);
    const running = compose(['ps', '--status', 'running', '--services'], true);
    if (running.split(/\r?\n/).includes('map-db')) {
      compose(['build', 'map-job']);
      const state = JSON.parse(job('inspect-state', true));
      console.log(`Import state: ${state.import}; original extract available: ${state.snapshot}`);
      if (state.import === 'ready') job('status');
    }
    return;
  }
  if (command === 'setup' || command === 'start') {
    compose(['build', 'map-job']);
    compose(['up', '-d', '--wait', 'map-db']);
    const state = JSON.parse(job('inspect-state', true));
    if (command === 'start' && state.import !== 'ready') throw new Error('A completed regional import is required. Run setup first.');
    if (command === 'setup') for (const step of setupPlan(state)) job(step);
    compose(['run', '--rm', '--no-deps', '-T', 'map-schema']);
    compose(['run', '--rm', '--no-deps', '-T', 'map-migrate']);
    compose(['up', '--build', '--no-deps', '--force-recreate', '-d', '--wait', 'martin', 'map-gateway']);
    await verify();
    console.log(`Self-hosted style: ${base}/style.json`);
    console.log('Run the connect command to point the frontend at this local map, then restart Expo.');
    return;
  }
  if (command === 'update') {
    compose(['build', 'map-job']);
    job('update');
    await verify();
    job('status');
    console.log('Reload the map to discard tiles cached by the open client session.');
    return;
  }
  if (command === 'reconcile-pois') {
    compose(['build', 'map-job']);
    job('reconcile-pois');
    console.log('Stored eligible POIs reconciled. Review proposed matches explicitly, then reload the map. No providers were fetched.');
    return;
  }
  await verify();
  if (command === 'connect') {
    await connectFrontend(`${base}/style.json`);
    console.log(`Updated only the local frontend map URL: ${base}/style.json`);
    console.log('Restart Expo. For phones/emulators, use an accessible host address instead of loopback.');
  }
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}