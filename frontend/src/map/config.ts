export const MAP_STYLE_URL = process.env.EXPO_PUBLIC_MAP_STYLE_URL || 'https://tiles.openfreemap.org/styles/liberty';
export const MIN_ZOOM = 4;
export const MAX_ZOOM = 18;
export const ROMANIA_BOUNDS: [[number, number], [number, number]] = [[19.5, 43.4], [30, 48.5]];
export const PLACES = [
  { name: 'Romania', center: [24.9, 45.9] as [number, number], zoom: 6 },
  { name: 'Bucharest', center: [26.1025, 44.4268] as [number, number], zoom: 14 },
  { name: 'Cluj-Napoca', center: [23.5899, 46.7712] as [number, number], zoom: 14 },
  { name: 'Brasov', center: [25.6012, 45.6579] as [number, number], zoom: 14 },
  { name: 'Constanta', center: [28.6348, 44.1598] as [number, number], zoom: 14 },
];

export function isInCoverage(longitude: number, latitude: number) {
  return Number.isFinite(longitude) && Number.isFinite(latitude)
    && longitude >= ROMANIA_BOUNDS[0][0] && longitude <= ROMANIA_BOUNDS[1][0]
    && latitude >= ROMANIA_BOUNDS[0][1] && latitude <= ROMANIA_BOUNDS[1][1];
}