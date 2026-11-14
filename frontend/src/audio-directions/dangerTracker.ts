import { getResolution, latLngToCell } from 'h3-js';
import { dangerAheadPhrase, dangerEnterPhrase, dangerLeavePhrase } from './phrases';
import { distanceBetweenMeters } from './tracker';

export const DANGER_LOOKAHEAD_METERS = 120;
const SAMPLE_STEP_METERS = 10;
const MAX_OFF_ROUTE_METERS = 60;
const LEAVE_CONFIRMATIONS = 2;

export type DangerZoneInput = { h3Index: string; topCategories?: string[] };

export type DangerIndex = {
  resolution: number;
  cells: Map<string, string[]>;
};

export type DangerTracker = {
  inside: boolean;
  aheadSpoken: boolean;
  outsideStreak: number;
};

export type LngLat = [number, number];

export function buildDangerIndex(zones: DangerZoneInput[]): DangerIndex | null {
  if (zones.length === 0) return null;
  const cells = new Map<string, string[]>();
  for (const zone of zones) {
    if (typeof zone.h3Index === 'string' && zone.h3Index.length > 0) {
      cells.set(zone.h3Index, zone.topCategories ?? []);
    }
  }
  if (cells.size === 0) return null;
  const firstCell = cells.keys().next().value as string;
  return { resolution: getResolution(firstCell), cells };
}

export function createDangerTracker(): DangerTracker {
  return { inside: false, aheadSpoken: false, outsideStreak: 0 };
}

function categoriesAt(index: DangerIndex, lat: number, lng: number): string[] | null {
  const cell = latLngToCell(lat, lng, index.resolution);
  return index.cells.get(cell) ?? null;
}

function findZoneAhead(
  index: DangerIndex,
  position: { lat: number; lng: number },
  route: LngLat[]
): { meters: number; categories: string[] } | null {
  if (route.length < 2) return null;

  let nearest = 0;
  let nearestDistance = Infinity;
  route.forEach(([lng, lat], routeIndex) => {
    const distance = distanceBetweenMeters(position.lat, position.lng, lat, lng);
    if (distance < nearestDistance) {
      nearestDistance = distance;
      nearest = routeIndex;
    }
  });
  if (nearestDistance > MAX_OFF_ROUTE_METERS) return null;

  const path: LngLat[] = [[position.lng, position.lat], ...route.slice(nearest)];
  let travelled = 0;
  for (let i = 1; i < path.length; i += 1) {
    const [fromLng, fromLat] = path[i - 1];
    const [toLng, toLat] = path[i];
    const segment = distanceBetweenMeters(fromLat, fromLng, toLat, toLng);
    const steps = Math.max(1, Math.ceil(segment / SAMPLE_STEP_METERS));
    const stepLength = segment / steps;
    for (let step = 1; step <= steps; step += 1) {
      travelled += stepLength;
      if (travelled > DANGER_LOOKAHEAD_METERS) return null;
      const t = step / steps;
      const categories = categoriesAt(
        index,
        fromLat + (toLat - fromLat) * t,
        fromLng + (toLng - fromLng) * t
      );
      if (categories) return { meters: travelled, categories };
    }
  }
  return null;
}

/**
 * Advances the danger-zone tracker for a GPS fix and returns the phrases to speak (usually zero or one).
 * It announces a zone ahead on the route once, entering a zone, and leaving it.
 */
export function advanceDangerTracker(
  tracker: DangerTracker,
  index: DangerIndex | null,
  position: { lat: number; lng: number },
  route: LngLat[]
): string[] {
  if (!index) {
    tracker.inside = false;
    tracker.aheadSpoken = false;
    tracker.outsideStreak = 0;
    return [];
  }

  const here = categoriesAt(index, position.lat, position.lng);
  if (here) {
    tracker.outsideStreak = 0;
    tracker.aheadSpoken = true;
    if (!tracker.inside) {
      tracker.inside = true;
      return [dangerEnterPhrase(here)];
    }
    return [];
  }

  if (tracker.inside) {
    tracker.outsideStreak += 1;
    if (tracker.outsideStreak >= LEAVE_CONFIRMATIONS) {
      tracker.inside = false;
      tracker.outsideStreak = 0;
      tracker.aheadSpoken = false;
      return [dangerLeavePhrase()];
    }
    return [];
  }

  const ahead = findZoneAhead(index, position, route);
  if (!ahead) {
    tracker.aheadSpoken = false;
    return [];
  }
  if (tracker.aheadSpoken) return [];
  tracker.aheadSpoken = true;
  return [dangerAheadPhrase(ahead.meters, ahead.categories)];
}
