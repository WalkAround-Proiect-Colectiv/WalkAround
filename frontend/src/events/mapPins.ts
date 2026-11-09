import { isInCoverage } from '../map/config';
import type { UpcomingEvent } from './api/events';

export interface EventPin {
  id: string;
  coordinates: [longitude: number, latitude: number];
  events: UpcomingEvent[];
}

export interface EventPinFeatureProperties {
  pinId: string;
  count: number;
}

export interface EventPinFeature {
  type: 'Feature';
  geometry: {
    type: 'Point';
    coordinates: [number, number];
  };
  properties: EventPinFeatureProperties;
}

export interface EventPinsFeatureCollection {
  type: 'FeatureCollection';
  features: EventPinFeature[];
}

const EARTH_RADIUS_KM = 6371;

/**
 * Groups upcoming events with valid in-coverage coordinates into map pins.
 * Co-located events at the same venue (rounded to 5 decimal places, ~1.1 m) share one pin.
 */
export function toEventPins(events: UpcomingEvent[] | null | undefined): EventPin[] {
  if (!events || events.length === 0) {
    return [];
  }

  const byCoordKey = new Map<string, EventPin>();

  for (const event of events) {
    const lat = event.latitude;
    const lng = event.longitude;
    if (
      typeof lat !== 'number' ||
      typeof lng !== 'number' ||
      !Number.isFinite(lat) ||
      !Number.isFinite(lng) ||
      !isInCoverage(lng, lat)
    ) {
      continue;
    }

    const coordKey = `${lat.toFixed(5)},${lng.toFixed(5)}`;
    const existing = byCoordKey.get(coordKey);
    if (existing) {
      if (!existing.events.some(item => item.key === event.key)) {
        existing.events.push(event);
      }
    } else {
      byCoordKey.set(coordKey, {
        id: `event-pin:${coordKey}`,
        coordinates: [lng, lat],
        events: [event],
      });
    }
  }

  return Array.from(byCoordKey.values());
}

/** Converts grouped event pins into a GeoJSON FeatureCollection for MapLibre symbol/circle layers. */
export function toEventPinsGeoJSON(pins: EventPin[]): EventPinsFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: pins.map(pin => ({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: pin.coordinates,
      },
      properties: {
        pinId: pin.id,
        count: pin.events.length,
      },
    })),
  };
}

/** Great-circle distance in kilometers between two `[longitude, latitude]` coordinates. */
export function distanceKm(
  first: [longitude: number, latitude: number],
  second: [longitude: number, latitude: number],
): number {
  const [lng1, lat1] = first;
  const [lng2, lat2] = second;
  const dLat = toRadians(lat2 - lat1);
  const dLng = toRadians(lng2 - lng1);
  const sinLat = Math.sin(dLat / 2);
  const sinLng = Math.sin(dLng / 2);
  const a =
    sinLat * sinLat +
    Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * sinLng * sinLng;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
  return EARTH_RADIUS_KM * c;
}

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}
