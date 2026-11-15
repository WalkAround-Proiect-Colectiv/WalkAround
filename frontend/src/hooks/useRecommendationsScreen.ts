import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { useRouter } from 'expo-router';
import { useActivityRecommendations } from './useActivityRecommendations';
import { useUserLocation } from './useUserLocation';

export const RADIUS_OPTIONS = [100, 500, 1000, 2000, 3500, 5000] as const;
const DEFAULT_RADIUS_METERS = 2000;

export function useRecommendationsScreen() {
  const router = useRouter();
  const [radiusMeters, setRadiusMeters] = useState<number>(DEFAULT_RADIUS_METERS);
  const location = useUserLocation();
  const recommendations = useActivityRecommendations(location.coords, radiusMeters);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextAppState => {
      if (nextAppState === 'active') {
        if (location.status === 'denied' || location.status === 'unavailable') {
          void location.refresh();
        } else {
          recommendations.refreshIfStale();
        }
      }
    });
    return () => subscription.remove();
  }, [location, recommendations]);

  const expandRadius = useCallback(() => {
    setRadiusMeters(current => {
      const next = RADIUS_OPTIONS.find(option => option > current);
      return next ?? 5000;
    });
  }, []);

  const navigateBackToMap = useCallback(() => {
    router.push('/map');
  }, [router]);

  return {
    locationStatus: location.status,
    locationLabel: location.locationLabel,
    coords: location.coords,
    refreshLocation: location.refresh,
    openSettings: location.openSettings,
    radiusMeters,
    radiusOptions: RADIUS_OPTIONS,
    setRadiusMeters,
    expandRadius,
    canExpandRadius: radiusMeters < 5000,
    data: recommendations.data,
    fetchStatus: recommendations.status,
    error: recommendations.error,
    isOffline: recommendations.isOffline,
    isStale: recommendations.isStale,
    isRefreshing: recommendations.isRefreshing,
    refetch: recommendations.refetch,
    navigateBackToMap,
  };
}
