import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { CAPTION_MAX_LENGTH, MAX_UPLOAD_BYTES } from '../src/poiimages/limits.ts';

const backendConfig = readFileSync(new URL('../../services/application-api/src/main/resources/application.yaml', import.meta.url), 'utf8');
const migration = readFileSync(new URL('../../services/application-api/src/main/resources/db/migration/V18__create_poi_images.sql', import.meta.url), 'utf8');

test('the app checks the same upload size limit as the backend', () => {
  const match = backendConfig.match(/max-bytes: \$\{POI_IMAGE_MAX_BYTES:(\d+)\}/);
  assert.ok(match, 'app.poi-images.max-bytes is configured');
  assert.equal(MAX_UPLOAD_BYTES, Number(match[1]));
  const multipart = backendConfig.match(/max-file-size: \$\{PROFILE_PICTURE_MAX_BYTES:(\d+)\}B/);
  assert.ok(multipart && Number(multipart[1]) >= MAX_UPLOAD_BYTES, 'the global multipart limit allows POI photos');
});

test('the caption limit matches the database column', () => {
  const match = migration.match(/caption VARCHAR\((\d+)\)/);
  assert.ok(match);
  assert.equal(CAPTION_MAX_LENGTH, Number(match[1]));
});

test('only real map POIs offer photos, using the same ids the backend accepts', async () => {
  const { canHavePhotos } = await import('../src/poiimages/limits.ts');
  for (const id of ['3f2b6c1e-8a4d-4f7b-9c2e-1a2b3c4d5e6f', 'osm:n/123456', 'osm:w/42', 'osm:r/-7', 'osm:node/1']) {
    assert.equal(canHavePhotos(id), true, id);
  }
  for (const id of [undefined, null, '', 'selected-coordinate', 'Search Result', 'osm:x/1', 'osm:n/abc', '../etc']) {
    assert.equal(canHavePhotos(id), false, String(id));
  }
});
