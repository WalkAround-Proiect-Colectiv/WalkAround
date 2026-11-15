import { ApiError, request } from '../../login/api/http';
import type { RecommendationResponse } from '../recommendations.types';

export function buildRecommendationsPath(
  latitude: number,
  longitude: number,
  radiusMeters?: number,
): string {
  const params = new URLSearchParams({
    latitude: latitude.toFixed(5),
    longitude: longitude.toFixed(5),
  });
  if (radiusMeters != null && Number.isFinite(radiusMeters)) {
    params.set('radiusMeters', String(Math.round(radiusMeters)));
  }
  return `/api/v1/recommendations/activities?${params.toString()}`;
}

export async function fetchActivityRecommendations(
  latitude: number,
  longitude: number,
  radiusMeters?: number,
  signal?: AbortSignal,
): Promise<RecommendationResponse> {
  const path = buildRecommendationsPath(latitude, longitude, radiusMeters);
  try {
    return await request<RecommendationResponse>(path, signal ? { signal } : {});
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 400) {
        throw new Error('Invalid location or search radius.');
      }
      throw new Error('Could not load activity recommendations right now. Please try again.');
    }
    throw error;
  }
}
