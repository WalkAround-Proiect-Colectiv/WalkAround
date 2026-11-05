import { useCallback, useRef, useState, type RefObject } from 'react';
import { useFocusEffect } from 'expo-router';
import * as Location from 'expo-location';
import type { MapHandle, UserLocation } from './MapCanvas.types';
import { isInCoverage } from './config';

export function useUserLocalization(map: RefObject<MapHandle | null>) {
  const locationSubscription = useRef<Location.LocationSubscription | null>(null);
  const trackingRef = useRef(false);
  const trackingRequest = useRef(0);
  const displayRef = useRef<UserLocation>(null);
  const animationFrame = useRef<number | null>(null);
  const [locating, setLocating] = useState(false);
  const [userLocation, setUserLocation] = useState<UserLocation>(null);
  const [error, setError] = useState<string | null>(null);

  const stopTracking = useCallback(() => {
    trackingRequest.current += 1;
    trackingRef.current = false;
    locationSubscription.current?.remove();
    locationSubscription.current = null;
    if (animationFrame.current !== null) cancelAnimationFrame(animationFrame.current);
    animationFrame.current = null;
    displayRef.current = null;
    setLocating(false);
    setUserLocation(null);
    setError(null);
  }, []);

  useFocusEffect(useCallback(() => () => stopTracking(), [stopTracking]));

  const animateTo = useCallback((coordinates: [number, number], accuracy: number) => {
    const target: UserLocation = { coordinates, accuracy };
    if (animationFrame.current !== null) cancelAnimationFrame(animationFrame.current);
    const start = displayRef.current;
    if (!start) {
      displayRef.current = target;
      setUserLocation(target);
      return;
    }
    const startedAt = performance.now();
    const duration = 700;
    const step = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / duration);
      const eased = progress * (2 - progress);
      const current: UserLocation = {
        coordinates: [
          start.coordinates[0] + (coordinates[0] - start.coordinates[0]) * eased,
          start.coordinates[1] + (coordinates[1] - start.coordinates[1]) * eased,
        ],
        accuracy: start.accuracy + (accuracy - start.accuracy) * eased,
      };
      displayRef.current = current;
      setUserLocation(current);
      if (progress < 1) animationFrame.current = requestAnimationFrame(step);
      else animationFrame.current = null;
    };
    animationFrame.current = requestAnimationFrame(step);
  }, []);

  const locate = useCallback(async () => {
    if (trackingRef.current) {
      if (userLocation) map.current?.goTo({ center: userLocation.coordinates, zoom: 15 });
      return;
    }
    if (locating) return;

    const request = ++trackingRequest.current;
    setLocating(true);
    setError(null);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (request !== trackingRequest.current) return;
      if (!permission.granted) throw new Error('Location permission was denied. You can still browse the map.');

      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.BestForNavigation });
      if (request !== trackingRequest.current) return;
      const { longitude, latitude } = position.coords;
      if (!isInCoverage(longitude, latitude)) throw new Error('Your location is outside the Romania prototype area.');
      const accuracy = position.coords.accuracy ?? 100;

      animateTo([longitude, latitude], accuracy);
      map.current?.goTo({ center: [longitude, latitude], zoom: 15 });
      trackingRef.current = true;
      const subscription = await Location.watchPositionAsync(
        { accuracy: Location.Accuracy.BestForNavigation, distanceInterval: 5, timeInterval: 1000 },
        update => {
          if (!trackingRef.current) return;
          const { longitude: nextLongitude, latitude: nextLatitude } = update.coords;
          if (!isInCoverage(nextLongitude, nextLatitude)) {
            if (animationFrame.current !== null) cancelAnimationFrame(animationFrame.current);
            animationFrame.current = null;
            setUserLocation(null);
            displayRef.current = null;
            setError('Your location moved outside the Romania prototype area. Tracking will resume if you return.');
            return;
          }
          if (Date.now() - update.timestamp > 15000) return;
          const nextAccuracy = update.coords.accuracy ?? 100;
          if (nextAccuracy > 100) return;
          animateTo([nextLongitude, nextLatitude], nextAccuracy);
          setError(null);
        },
      );
      if (request !== trackingRequest.current || !trackingRef.current) subscription.remove();
      else {
        locationSubscription.current = subscription;
      }
    } catch (reason) {
      if (request === trackingRequest.current) {
        trackingRef.current = false;
        setError(reason instanceof Error ? reason.message : 'Could not find your location.');
      }
    } finally {
      if (request === trackingRequest.current) {
        if (!locationSubscription.current) trackingRef.current = false;
        setLocating(false);
      }
    }
  }, [animateTo, locating, map, userLocation]);

  return { userLocation, locating, error, locate };
}
