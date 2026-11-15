export type MapPoi = { id: string; name: string; category: string; sources: string; attribution: string; licenses: string };
export type MapZone = { h3Index: string; intensity: number; categories: string[] };

export function selectMapHit(poiFeatures: { properties?: unknown }[], zoneFeatures: { properties?: unknown }[]) {
  const poi = poiFeatures.map(feature => readPoi(feature.properties)).find(value => value !== null) ?? null;
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

export function readPoi(properties: unknown): MapPoi | null {
  if (!properties || typeof properties !== 'object') return null;
  const values = properties as Record<string, unknown>;
  const text = (key: string, limit: number) => typeof values[key] === 'string' ? values[key].trim().slice(0, limit) : '';
  const id = text('poi_id', 160);
  const name = text('name', 200);
  const category = text('category', 80).replace(/_/g, ' ');
  if (!id || !name || !category) return null;
  return { id, name, category, sources: text('sources', 200), attribution: text('attribution', 500), licenses: text('licenses', 200) };
}