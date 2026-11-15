import { useCallback, useEffect, useRef, useState } from 'react';
import { Linking } from 'react-native';
import * as Location from 'expo-location';
import type { Coordinates, LocationStatus } from '../recommendations/recommendations.types';

const LOCATION_TIMEOUT_MS = 8000;

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('Location request timed out'));
    }, timeoutMs);
    promise.then(
      value => {
        clearTimeout(timer);
        resolve(value);
      },
      error => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}

interface ResolvedLocationResult {
  coords: Coordinates | null;
  status: LocationStatus;
  label: string;
}

async function resolveDeviceLocation(): Promise<ResolvedLocationResult> {
  try {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (!permission.granted) {
      return { coords: null, status: 'denied', label: 'Current location' };
    }

    try {
      const position = await withTimeout(
        Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
        LOCATION_TIMEOUT_MS,
      );
      return {
        coords: {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        },
        status: 'granted',
        label: 'Current location',
      };
    } catch {
      const lastKnown = await Location.getLastKnownPositionAsync();
      if (lastKnown) {
        return {
          coords: {
            latitude: lastKnown.coords.latitude,
            longitude: lastKnown.coords.longitude,
          },
          status: 'granted',
          label: 'Last known location',
        };
      }
      return { coords: null, status: 'unavailable', label: 'Current location' };
    }
  } catch {
    return { coords: null, status: 'unavailable', label: 'Current location' };
  }
}

export function useUserLocation() {
  const [coords, setCoords] = useState<Coordinates | null>(null);
  const [status, setStatus] = useState<LocationStatus>('loading');
  const [locationLabel, setLocationLabel] = useState<string>('Current location');
  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = ++requestIdRef.current;
    resolveDeviceLocation().then(result => {
      if (requestId !== requestIdRef.current) return;
      setCoords(result.coords);
      setLocationLabel(result.label);
      setStatus(result.status);
    });
    return () => {
      requestIdRef.current += 1;
    };
  }, []);

  const refresh = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setStatus('loading');
    const result = await resolveDeviceLocation();
    if (requestId !== requestIdRef.current) return;
    setCoords(result.coords);
    setLocationLabel(result.label);
    setStatus(result.status);
  }, []);

  const openSettings = useCallback(async () => {
    try {
      await Linking.openSettings();
    } catch {
      // Ignore if platform does not support openSettings (e.g. web)
    }
  }, []);

  return {
    coords,
    status,
    locationLabel,
    refresh,
    openSettings,
  };
}
