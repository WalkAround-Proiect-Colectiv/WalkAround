export type MapPoi = {
  id: string;
  name: string;
  category: string;
  sources: string;
  attribution: string;
  licenses: string;
  coordinates?: [longitude: number, latitude: number];
};
export type MapZone = { h3Index: string; intensity: number; categories: string[] };

export function commentablePoiId(poi: MapPoi | null): string | null {
  const identity = poi?.id;
  if (!identity) return null;
  return /^(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|osm:[nwr]\/[1-9][0-9]{0,18})$/.test(identity) ? identity : null;
}

export function selectMapHit(poiFeatures: { properties?: unknown }[], zoneFeatures: { properties?: unknown }[]) {
  const poi = selectMapPois(poiFeatures)[0] ?? null;
  if (poi) return { poi, zone: null };
  for (const feature of zoneFeatures) {
    const values = feature.properties as Record<string, unknown> | null;
    if (!values || typeof values.h3Index !== 'string' || typeof values.intensity !== 'number' || !Number.isFinite(values.intensity)) continue;
    let categories = values.topCategories;
    if (typeof categories === 'string') {
      try { categories = JSON.parse(categories); } catch { categories = []; }
    }
    const zone: MapZone = { h3Index: values.h3Index, intensity: values.intensity,
      categories: Array.isArray(categories) ? categories.filter((value): value is string => typeof value === 'string') : [] };
    return { poi: null, zone };
  }
  return { poi: null, zone: null };
}

export function selectMapPois(poiFeatures: { properties?: unknown }[]): MapPoi[] {
  const candidates: MapPoi[] = [];
  const seen = new Set<string>();
  for (const feature of poiFeatures) {
    const geometry = 'geometry' in feature
      ? (feature as { geometry?: unknown }).geometry
      : null;
    let coordinates: [number, number] | undefined;
    if (geometry && typeof geometry === 'object') {
      const geom = geometry as { type?: unknown; coordinates?: unknown };
      if (geom.type === 'Point' && Array.isArray(geom.coordinates)
        && geom.coordinates.length >= 2
        && geom.coordinates.every(value => typeof value === 'number')) {
        coordinates = [geom.coordinates[0] as number, geom.coordinates[1] as number];
      }
    }
    const poi = readPoi(feature.properties, coordinates);
    if (poi && !seen.has(poi.id)) {
      seen.add(poi.id);
      candidates.push(poi);
    }
  }
  return candidates;
}

export function readPoi(properties: unknown, coordinates?: [number, number]): MapPoi | null {
  if (!properties || typeof properties !== 'object') return null;
  const values = properties as Record<string, unknown>;
  const text = (key: string, limit: number) => typeof values[key] === 'string' ? values[key].trim().slice(0, limit) : '';
  const id = text('poi_id', 160);
  const name = text('name', 200);
  const category = text('category', 80).replace(/_/g, ' ');
  if (!id || !name || !category) return null;
  return {
    id,
    name,
    category,
    sources: text('sources', 200),
    attribution: text('attribution', 500),
    licenses: text('licenses', 200),
    ...(coordinates ? { coordinates } : {}),
  };
}