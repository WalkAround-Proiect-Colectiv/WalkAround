import { environment } from '../config/environment';

export type ReversePlaceName = {
  name: string;
  detail: string;
};

export async function reversePlaceName(
  latitude: number,
  longitude: number,
  signal?: AbortSignal,
): Promise<ReversePlaceName | null> {
  const params = new URLSearchParams({
    lat: String(latitude),
    lon: String(longitude),
  });
  const response = await fetch(`${environment.apiBaseUrl}/api/places/reverse?${params.toString()}`, { signal });
  if (!response.ok) return null;
  const result = await response.json() as { name?: string; detail?: string };
  if (!result.name?.trim()) return null;
  return { name: result.name.trim(), detail: result.detail?.trim() || '' };
}
