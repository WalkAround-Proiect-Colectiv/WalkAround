export type MapPoi = { id: string; name: string; category: string; sources: string; attribution: string; licenses: string };

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