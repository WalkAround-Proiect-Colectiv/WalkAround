/**
 * Upload limits shared with the backend (`app.poi-images` in the Application API). Checked here too so the
 * user gets an answer before a large file is uploaded. `tests/poiImages.test.mjs` keeps them in sync.
 */
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
export const CAPTION_MAX_LENGTH = 200;

/**
 * Whether a POI from the map can have photos. Matches the backend's accepted ids: a curated POI's UUID or an
 * OpenStreetMap reference such as `osm:n/123`. Search results and tapped map coordinates are not POIs.
 */
export function canHavePhotos(poiId: string | null | undefined): poiId is string {
  return typeof poiId === 'string'
    && /^(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|osm:(?:node|way|relation|[nwr])\/-?[0-9]{1,20})$/.test(poiId);
}

/** Photos are shown only from https URLs, so data from the server cannot trigger other URL schemes. */
export function isSafeImageUrl(url: string | null | undefined): url is string {
  return typeof url === 'string' && /^https:\/\/[^\s]+$/.test(url);
}
