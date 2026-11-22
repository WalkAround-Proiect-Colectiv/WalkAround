import type { FeatureCollection, Point } from 'geojson';
import type { MapPoi } from './poi';

export type PoliceStation = {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
};

export type PoliceSearchResult = {
  stations: PoliceStation[];
  origin: [longitude: number, latitude: number];
  radiusMeters: number;
};

function isStation(value: unknown): value is PoliceStation {
  if (!value || typeof value !== 'object') return false;
  return 'id' in value && typeof value.id === 'string' && value.id.length > 0
    && 'name' in value && typeof value.name === 'string' && value.name.trim().length > 0
    && 'latitude' in value && typeof value.latitude === 'number' && Number.isFinite(value.latitude) && Math.abs(value.latitude) <= 90
    && 'longitude' in value && typeof value.longitude === 'number' && Number.isFinite(value.longitude) && Math.abs(value.longitude) <= 180
    && 'distanceMeters' in value && typeof value.distanceMeters === 'number' && Number.isFinite(value.distanceMeters) && value.distanceMeters >= 0;
}

function throwIfAborted(signal: AbortSignal) {
  if (signal.aborted) {
    const error = new Error('Station lookup cancelled');
    error.name = 'AbortError';
    throw error;
  }
}

export async function fetchNearbyPolice(
  apiBaseUrl: string,
  origin: [longitude: number, latitude: number],
  radiusMeters: number,
  signal: AbortSignal,
): Promise<PoliceStation[]> {
  if (!apiBaseUrl.trim()) {
    throw new Error('Application API is not configured. Set EXPO_PUBLIC_API_BASE_URL in frontend/.env.local, then restart Expo.');
  }

  const stations = new Map<string, PoliceStation>();
  let page = 0;
  while (true) {
    throwIfAborted(signal);
    const params = new URLSearchParams({
      type: 'police', lat: String(origin[1]), lon: String(origin[0]),
      radiusMeters: String(radiusMeters), page: String(page),
    });
    let response: Response;
    try {
      response = await fetch(`${apiBaseUrl}/api/places/nearby?${params}`, { signal, cache: 'no-store' });
    } catch (error) {
      if (signal.aborted) throw error;
      throw new Error('Could not reach the Application API. Start it and verify EXPO_PUBLIC_API_BASE_URL in frontend/.env.local.');
    }
    if (!response.headers.get('content-type')?.toLowerCase().includes('json')) {
      throw new Error('The station service returned a non-JSON response. Verify EXPO_PUBLIC_API_BASE_URL points to the Application API.');
    }
    if (!response.ok) throw new Error('The station service is temporarily unavailable. Please try again.');
    let result: unknown;
    try {
      result = await response.json();
    } catch {
      throw new Error('The station service returned invalid JSON. Please try again.');
    }
    if (!result || typeof result !== 'object' || !('items' in result) || !Array.isArray(result.items)
      || !result.items.every(isStation) || !('nextPage' in result)
      || (result.nextPage !== null && result.nextPage !== page + 1)) {
      throw new Error('The station service returned an invalid response. Please try again.');
    }
    for (const station of result.items) {
      stations.set(station.id, station);
    }
    if (result.nextPage === null) break;
    page = result.nextPage;
  }
  throwIfAborted(signal);
  return [...stations.values()].sort((a, b) => a.distanceMeters - b.distanceMeters || a.id.localeCompare(b.id));
}

export function policePoi(station: PoliceStation): MapPoi {
  return {
    id: `nearby-police:${station.id}`,
    name: station.name,
    category: 'Police station',
    coordinates: [station.longitude, station.latitude],
    sources: 'OpenStreetMap',
    attribution: '(c) OpenStreetMap contributors',
    licenses: 'ODbL 1.0',
  };
}

export function policePins(stations: PoliceStation[]): FeatureCollection<Point> {
  return {
    type: 'FeatureCollection',
    features: stations.map(station => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [station.longitude, station.latitude] },
      properties: {
        poi_id: policePoi(station).id, name: station.name, category: 'police',
        sources: 'OpenStreetMap', attribution: '(c) OpenStreetMap contributors', licenses: 'ODbL 1.0',
      },
    })),
  };
}

export function policeDistance(meters: number): string {
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${Math.round(meters)} m`;
}
