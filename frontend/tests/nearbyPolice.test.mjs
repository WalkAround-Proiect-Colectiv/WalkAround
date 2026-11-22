import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fetchNearbyPolice, policePins, policePoi } from '../src/map/nearbyPolice.ts';
import { selectMapPois } from '../src/map/poi.ts';

const station = (id, distanceMeters = 200) => ({ id, name: 'Police station', latitude: 46.77, longitude: 23.59, distanceMeters });

test('police search uses device coordinates, fetches every page and deduplicates results', async t => {
  const requests = [];
  t.mock.method(globalThis, 'fetch', async url => {
    const params = new URL(url).searchParams;
    requests.push(params);
    return Response.json(params.get('page') === '0'
      ? { items: [station('n:2', 400)], nextPage: 1 }
      : { items: [station('n:2', 400), station('n:1', 100)], nextPage: null });
  });
  const results = await fetchNearbyPolice('https://api.example.test', [23.59, 46.77], 3000, new AbortController().signal);
  assert.deepEqual(results.map(value => value.id), ['n:1', 'n:2']);
  assert.equal(requests.length, 2);
  assert.equal(requests[0].get('lat'), '46.77');
  assert.equal(requests[0].get('lon'), '23.59');
  assert.equal(requests[0].get('radiusMeters'), '3000');
  assert.equal(requests[0].get('type'), 'police');
});

test('an empty search is distinct from an unavailable service or failed later page', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ items: [], nextPage: null }));
  assert.deepEqual(await fetchNearbyPolice('', [23, 46], 1000, new AbortController().signal), []);
  globalThis.fetch.mock.mockImplementation(async url => new URL(`https://test${url}`).searchParams.get('page') === '0'
    ? Response.json({ items: [station('n:1')], nextPage: 1 })
    : new Response('', { status: 503 }));
  await assert.rejects(fetchNearbyPolice('', [23, 46], 1000, new AbortController().signal), /temporarily unavailable/);
});

test('cancellation and malformed pagination cannot publish stale or incomplete station results', async t => {
  const controller = new AbortController();
  t.mock.method(globalThis, 'fetch', async () => {
    controller.abort();
    return Response.json({ items: [station('n:1')], nextPage: 1 });
  });
  await assert.rejects(fetchNearbyPolice('', [23, 46], 1000, controller.signal), { name: 'AbortError' });
  assert.equal(globalThis.fetch.mock.calls.length, 1);
  globalThis.fetch.mock.mockImplementation(async () => Response.json({ items: [station('n:1')], nextPage: 0 }));
  await assert.rejects(fetchNearbyPolice('', [23, 46], 1000, new AbortController().signal), /invalid response/);
  globalThis.fetch.mock.mockImplementation(async () => Response.json({ items: [{ ...station('n:1'), latitude: null }], nextPage: null }));
  await assert.rejects(fetchNearbyPolice('', [23, 46], 1000, new AbortController().signal), /invalid response/);
});

test('police pins use the existing POI selection contract and preserve exact route destination coordinates', () => {
  const record = station('n:1');
  const poi = policePoi(record);
  const hit = selectMapPois(policePins([record]).features)[0];
  assert.equal(hit.id, poi.id);
  assert.deepEqual(hit.coordinates, [23.59, 46.77]);
  assert.equal(hit.name, 'Police station');
  assert.equal(hit.sources, 'OpenStreetMap');
});
