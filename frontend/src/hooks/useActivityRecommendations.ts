import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchActivityRecommendations } from '../recommendations/api/recommendationsApi';
import type { Coordinates, RecommendationResponse } from '../recommendations/recommendations.types';

export const RECOMMENDATION_CACHE_TTL_MS = 15 * 60 * 1000;

interface CacheEntry {
  data: RecommendationResponse;
  fetchedAt: number;
}

const recommendationCache = new Map<string, CacheEntry>();

export function buildRecommendationCacheKey(
  latitude: number,
  longitude: number,
  radiusMeters: number,
): string {
  return `${latitude.toFixed(2)}:${longitude.toFixed(2)}:${Math.round(radiusMeters)}`;
}

export function isCacheFresh(fetchedAt: number, now = Date.now()): boolean {
  return now - fetchedAt < RECOMMENDATION_CACHE_TTL_MS;
}

export function clearRecommendationCache(): void {
  recommendationCache.clear();
}

function isLikelyOfflineError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  const msg = error.message.toLowerCase();
  return (
    msg.includes('could not reach') ||
    msg.includes('network') ||
    msg.includes('failed to fetch') ||
    msg.includes('offline')
  );
}

export type FetchStatus = 'idle' | 'loading' | 'success' | 'error';

interface LoadOutcome {
  data: RecommendationResponse | null;
  status: FetchStatus;
  error: string | null;
  isOffline: boolean;
  isStale: boolean;
  fetchedAt: number;
}

async function resolveRecommendations(
  targetCoords: Coordinates,
  targetRadius: number,
  forceRefresh: boolean,
): Promise<LoadOutcome> {
  const key = buildRecommendationCacheKey(
    targetCoords.latitude,
    targetCoords.longitude,
    targetRadius,
  );
  const cached = recommendationCache.get(key);

  if (!forceRefresh && cached && isCacheFresh(cached.fetchedAt)) {
    return {
      data: cached.data,
      status: 'success',
      error: null,
      isOffline: false,
      isStale: cached.data.degraded,
      fetchedAt: cached.fetchedAt,
    };
  }

  try {
    const response = await fetchActivityRecommendations(
      targetCoords.latitude,
      targetCoords.longitude,
      targetRadius,
    );
    const now = Date.now();
    recommendationCache.set(key, { data: response, fetchedAt: now });
    return {
      data: response,
      status: 'success',
      error: null,
      isOffline: false,
      isStale: response.degraded,
      fetchedAt: now,
    };
  } catch (err) {
    const offline = isLikelyOfflineError(err);
    if (cached) {
      return {
        data: cached.data,
        status: 'success',
        error: null,
        isOffline: offline,
        isStale: true,
        fetchedAt: cached.fetchedAt,
      };
    }
    return {
      data: null,
      status: 'error',
      error:
        err instanceof Error
          ? err.message
          : 'Could not load activity recommendations. Please try again.',
      isOffline: offline,
      isStale: false,
      fetchedAt: 0,
    };
  }
}

export function useActivityRecommendations(
  coords: Coordinates | null,
  radiusMeters: number,
) {
  const [data, setData] = useState<RecommendationResponse | null>(null);
  const [status, setStatus] = useState<FetchStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [isOffline, setIsOffline] = useState(false);
  const [isStale, setIsStale] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const lastFetchedAtRef = useRef<number>(0);

  useEffect(() => {
    if (!coords) return;
    let active = true;

    Promise.resolve()
      .then(() => {
        if (!active) return null;
        const key = buildRecommendationCacheKey(coords.latitude, coords.longitude, radiusMeters);
        const cached = recommendationCache.get(key);
        if (!cached || !isCacheFresh(cached.fetchedAt)) {
          setStatus('loading');
          setError(null);
        }
        return resolveRecommendations(coords, radiusMeters, false);
      })
      .then(outcome => {
        if (!active || !outcome) return;
        lastFetchedAtRef.current = outcome.fetchedAt;
        setData(outcome.data);
        setStatus(outcome.status);
        setError(outcome.error);
        setIsOffline(outcome.isOffline);
        setIsStale(outcome.isStale);
      });

    return () => {
      active = false;
    };
  }, [coords, radiusMeters]);

  const refetch = useCallback(async () => {
    if (!coords) return;
    if (data) {
      setIsRefreshing(true);
    } else {
      setStatus('loading');
    }
    setError(null);

    const outcome = await resolveRecommendations(coords, radiusMeters, true);
    lastFetchedAtRef.current = outcome.fetchedAt;
    setData(outcome.data);
    setStatus(outcome.status);
    setError(outcome.error);
    setIsOffline(outcome.isOffline);
    setIsStale(outcome.isStale);
    setIsRefreshing(false);
  }, [coords, radiusMeters, data]);

  const refreshIfStale = useCallback(() => {
    if (!coords) return;
    if (lastFetchedAtRef.current > 0 && !isCacheFresh(lastFetchedAtRef.current)) {
      void refetch();
    }
  }, [coords, refetch]);

  return {
    data,
    status,
    error,
    isOffline,
    isStale,
    isRefreshing,
    refetch,
    refreshIfStale,
  };
}
