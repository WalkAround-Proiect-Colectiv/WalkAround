import { cellToLatLng, getResolution, latLngToCell } from 'h3-js';
import {
  dangerAheadPhrase,
  dangerClearPhrase,
  dangerEnterPhrase,
  dangerLeavePhrase,
} from './phrases';
import { distanceBetweenMeters } from './tracker';

export const DANGER_LOOKAHEAD_METERS = 120;
const MAX_ROUTE_SCAN_METERS = 5000;
const PROXIMITY_METERS = 35;
const SAMPLE_STEP_METERS = 10;
const MAX_OFF_ROUTE_METERS = 100;
const LEAVE_CONFIRMATIONS = 2;

export type DangerZoneInput = { h3Index: string; topCategories?: string[] };

export type DangerCenter = {
  cell: string;
  lat: number;
  lng: number;
  categories: string[];
};

export type DangerIndex = {
  resolutions: number[];
  cells: Map<string, string[]>;
  centers: DangerCenter[];
};

export type DangerTracker = {
  insideCell: string | null;
  outsideStreak: number;
  warnedFarCells: Set<string>;
  warnedCloseCells: Set<string>;
};

export type LngLat = [number, number];

export function buildDangerIndex(zones: DangerZoneInput[]): DangerIndex | null {
  if (zones.length === 0) return null;
  const cells = new Map<string, string[]>();
  const resolutionsSet = new Set<number>();
  const centers: DangerCenter[] = [];

  for (const zone of zones) {
    if (typeof zone.h3Index !== 'string' || zone.h3Index.length === 0) continue;
    try {
      const resolution = getResolution(zone.h3Index);
      const [lat, lng] = cellToLatLng(zone.h3Index);
      const categories = zone.topCategories ?? [];
      cells.set(zone.h3Index, categories);
      resolutionsSet.add(resolution);
      centers.push({ cell: zone.h3Index, lat, lng, categories });
    } catch {
      // Ignore malformed H3 indexes.
    }
  }

  if (cells.size === 0) return null;
  return {
    resolutions: Array.from(resolutionsSet).sort((a, b) => b - a),
    cells,
    centers,
  };
}

export function createDangerTracker(): DangerTracker {
  return {
    insideCell: null,
    outsideStreak: 0,
    warnedFarCells: new Set(),
    warnedCloseCells: new Set(),
  };
}

function hitAt(
  index: DangerIndex,
  lat: number,
  lng: number
): { cell: string; categories: string[] } | null {
  for (const resolution of index.resolutions) {
    const cell = latLngToCell(lat, lng, resolution);
    const categories = index.cells.get(cell);
    if (categories) {
      return { cell, categories };
    }
  }

  let closest: DangerCenter | null = null;
  let closestDistance = PROXIMITY_METERS;
  for (const center of index.centers) {
    const distance = distanceBetweenMeters(lat, lng, center.lat, center.lng);
    if (distance <= closestDistance) {
      closestDistance = distance;
      closest = center;
    }
  }
  return closest ? { cell: closest.cell, categories: closest.categories } : null;
}

function projectOntoRoute(
  position: { lat: number; lng: number },
  route: LngLat[]
): { distance: number; startPoint: LngLat; nextIndex: number } {
  const scaleLng = Math.cos((position.lat * Math.PI) / 180) || 1;
  let bestDistance = Infinity;
  let bestPoint: LngLat = route[0];
  let bestNextIndex = 1;

  for (let i = 0; i < route.length - 1; i += 1) {
    const [aLng, aLat] = route[i];
    const [bLng, bLat] = route[i + 1];
    const dx = (bLng - aLng) * scaleLng;
    const dy = bLat - aLat;
    const lenSq = dx * dx + dy * dy;
    const t =
      lenSq > 0
        ? Math.max(0, Math.min(1, (((position.lng - aLng) * scaleLng) * dx + (position.lat - aLat) * dy) / lenSq))
        : 0;
    const projLng = aLng + (bLng - aLng) * t;
    const projLat = aLat + (bLat - aLat) * t;
    const distance = distanceBetweenMeters(position.lat, position.lng, projLat, projLng);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestPoint = [projLng, projLat];
      bestNextIndex = i + 1;
    }
  }

  return { distance: bestDistance, startPoint: bestPoint, nextIndex: bestNextIndex };
}

function findZoneAhead(
  index: DangerIndex,
  position: { lat: number; lng: number } | null,
  route: LngLat[]
): { cell: string; meters: number; categories: string[] } | null {
  if (route.length < 2) return null;

  let path: LngLat[] = route;
  let scanFromStart = true;

  if (position) {
    const projection = projectOntoRoute(position, route);
    if (projection.distance <= MAX_OFF_ROUTE_METERS) {
      path = [[position.lng, position.lat], projection.startPoint, ...route.slice(projection.nextIndex)];
      scanFromStart = false;
    }
  }

  if (scanFromStart) {
    const startHit = hitAt(index, path[0][1], path[0][0]);
    if (startHit) {
      return { cell: startHit.cell, meters: 0, categories: startHit.categories };
    }
  }

  let travelled = 0;
  for (let i = 1; i < path.length; i += 1) {
    const [fromLng, fromLat] = path[i - 1];
    const [toLng, toLat] = path[i];
    const segment = distanceBetweenMeters(fromLat, fromLng, toLat, toLng);
    const steps = Math.max(1, Math.ceil(segment / SAMPLE_STEP_METERS));
    const stepLength = segment / steps;
    for (let step = 1; step <= steps; step += 1) {
      travelled += stepLength;
      if (travelled > MAX_ROUTE_SCAN_METERS) return null;
      const t = step / steps;
      const hit = hitAt(
        index,
        fromLat + (toLat - fromLat) * t,
        fromLng + (toLng - fromLng) * t
      );
      if (hit) {
        return { cell: hit.cell, meters: travelled, categories: hit.categories };
      }
    }
  }
  return null;
}

/**
 * Advances the danger-zone tracker and returns phrases to speak (usually zero or one).
 * Announces entering/leaving a zone and danger zones ahead along the route.
 */
export function advanceDangerTracker(
  tracker: DangerTracker,
  index: DangerIndex | null,
  position: { lat: number; lng: number } | null,
  route: LngLat[],
  announceWhenClear = false
): string[] {
  if (!index) {
    tracker.insideCell = null;
    tracker.outsideStreak = 0;
    tracker.warnedFarCells.clear();
    tracker.warnedCloseCells.clear();
    return announceWhenClear ? [dangerClearPhrase()] : [];
  }

  if (position) {
    const here = hitAt(index, position.lat, position.lng);
    if (here) {
      tracker.outsideStreak = 0;
      tracker.warnedFarCells.add(here.cell);
      tracker.warnedCloseCells.add(here.cell);
      const wasOutside = tracker.insideCell === null;
      tracker.insideCell = here.cell;
      return wasOutside ? [dangerEnterPhrase(here.categories)] : [];
    }

    if (tracker.insideCell !== null) {
      tracker.outsideStreak += 1;
      if (tracker.outsideStreak >= LEAVE_CONFIRMATIONS) {
        tracker.insideCell = null;
        tracker.outsideStreak = 0;
        return [dangerLeavePhrase()];
      }
      return [];
    }
  }

  const ahead = findZoneAhead(index, position, route);
  if (!ahead) {
    return announceWhenClear ? [dangerClearPhrase()] : [];
  }

  if (ahead.meters <= DANGER_LOOKAHEAD_METERS) {
    if (!tracker.warnedCloseCells.has(ahead.cell)) {
      tracker.warnedCloseCells.add(ahead.cell);
      tracker.warnedFarCells.add(ahead.cell);
      return [dangerAheadPhrase(ahead.meters, ahead.categories)];
    }
    return [];
  }

  if (!tracker.warnedFarCells.has(ahead.cell)) {
    tracker.warnedFarCells.add(ahead.cell);
    return [dangerAheadPhrase(ahead.meters, ahead.categories)];
  }
  return [];
}
