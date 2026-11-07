import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getNearbyEvents, type UpcomingEvent } from '../events/api/events';
import {
  distanceKm,
  toEventPins,
  toEventPinsGeoJSON,
  type EventPin,
  type EventPinsFeatureCollection,
} from '../events/mapPins';
import { isInCoverage } from '../map/config';

/** Reload nearby events when the map centre moves at least 10 km from the last loaded position. */
export const EVENT_RELOAD_DISTANCE_KM = 10;
const MOVE_DEBOUNCE_MS = 450;

export interface UseMapEventsOptions {
  center: [longitude: number, latitude: number];
  signedIn: boolean;
  mapReady: boolean;
}

interface SelectionState {
  pinId: string;
  eventIndex: number;
}

export function useMapEvents({ center, signedIn, mapReady }: UseMapEventsOptions) {
  const [events, setEvents] = useState<UpcomingEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selection, setSelection] = useState<SelectionState | null>(null);

  const mountedRef = useRef(true);
  const requestIdRef = useRef(0);
  const lastLoadedCenterRef = useRef<[number, number] | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const centerLng = center[0];
  const centerLat = center[1];

  useEffect(() => {
    if (!signedIn) {
      lastLoadedCenterRef.current = null;
      return;
    }
    if (!mapReady || !isInCoverage(centerLng, centerLat)) {
      return;
    }

    const targetCenter: [number, number] = [centerLng, centerLat];
    const previousCenter = lastLoadedCenterRef.current;
    if (
      previousCenter &&
      distanceKm(previousCenter, targetCenter) < EVENT_RELOAD_DISTANCE_KM
    ) {
      return;
    }

    const delay = previousCenter === null ? 0 : MOVE_DEBOUNCE_MS;
    const currentRequestId = ++requestIdRef.current;

    const timer = setTimeout(() => {
      if (!mountedRef.current || currentRequestId !== requestIdRef.current) {
        return;
      }
      setLoading(true);
      setError(null);

      getNearbyEvents(centerLat, centerLng)
        .then(result => {
          if (!mountedRef.current || currentRequestId !== requestIdRef.current) {
            return;
          }
          lastLoadedCenterRef.current = targetCenter;
          setEvents(result.events ?? []);
          setError(null);
        })
        .catch(failure => {
          if (!mountedRef.current || currentRequestId !== requestIdRef.current) {
            return;
          }
          // Keep existing pins if a background refresh fails so the map remains usable.
          setError(
            failure instanceof Error
              ? failure.message
              : 'Could not load nearby events.',
          );
        })
        .finally(() => {
          if (mountedRef.current && currentRequestId === requestIdRef.current) {
            setLoading(false);
          }
        });
    }, delay);

    return () => {
      clearTimeout(timer);
    };
  }, [signedIn, mapReady, centerLng, centerLat]);

  const pins: EventPin[] = useMemo(
    () => (signedIn ? toEventPins(events) : []),
    [signedIn, events],
  );

  const geoJSON: EventPinsFeatureCollection = useMemo(
    () => toEventPinsGeoJSON(pins),
    [pins],
  );

  const selectedPin: EventPin | null = useMemo(() => {
    if (!signedIn || !selection) {
      return null;
    }
    return pins.find(pin => pin.id === selection.pinId) ?? null;
  }, [signedIn, selection, pins]);

  const selectedEventIndex = useMemo(() => {
    if (!selectedPin || !selection) {
      return 0;
    }
    return Math.min(
      Math.max(0, selection.eventIndex),
      Math.max(0, selectedPin.events.length - 1),
    );
  }, [selectedPin, selection]);

  const selectedEvent: UpcomingEvent | null = selectedPin
    ? (selectedPin.events[selectedEventIndex] ?? null)
    : null;

  const selectPin = useCallback((pinId: string) => {
    setSelection(current =>
      current && current.pinId === pinId ? current : { pinId, eventIndex: 0 },
    );
  }, []);

  const clearSelection = useCallback(() => {
    setSelection(null);
  }, []);

  const nextEvent = useCallback(() => {
    setSelection(current => {
      if (!current) return null;
      const pin = pins.find(item => item.id === current.pinId);
      if (!pin || pin.events.length <= 1) return current;
      return {
        pinId: current.pinId,
        eventIndex: (current.eventIndex + 1) % pin.events.length,
      };
    });
  }, [pins]);

  const previousEvent = useCallback(() => {
    setSelection(current => {
      if (!current) return null;
      const pin = pins.find(item => item.id === current.pinId);
      if (!pin || pin.events.length <= 1) return current;
      return {
        pinId: current.pinId,
        eventIndex:
          (current.eventIndex - 1 + pin.events.length) % pin.events.length,
      };
    });
  }, [pins]);

  return {
    pins,
    geoJSON,
    loading: signedIn ? loading : false,
    error: signedIn ? error : null,
    selectedPin,
    selectedPinId: selectedPin?.id ?? null,
    selectedEvent,
    selectedEventIndex,
    selectedPinCount: selectedPin?.events.length ?? 0,
    selectPin,
    clearSelection,
    nextEvent,
    previousEvent,
  };
}
