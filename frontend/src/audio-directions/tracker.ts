import { approachPhrase, immediatePhrase, type RouteInstruction } from './phrases';

export const APPROACH_METERS = 50;
export const SKIP_APPROACH_METERS = 20;
export const IMMEDIATE_METERS = 15;
export const ADVANCE_METERS = 12;
const RECEDING_MARGIN_METERS = 8;

export type ManeuverState = {
  approachSpoken: boolean;
  immediateSpoken: boolean;
  enteredApproachZone: boolean;
  minDistanceMeters: number;
};

export type ManeuverTracker = {
  instructions: RouteInstruction[];
  currentIndex: number;
  states: ManeuverState[];
};

export type TrackerStepResult = {
  announcements: string[];
  prefetchPhrases: string[];
};

export function distanceBetweenMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3;
  const phi1 = (lat1 * Math.PI) / 180;
  const phi2 = (lat2 * Math.PI) / 180;
  const deltaPhi = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLambda = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

export function createTracker(instructions: RouteInstruction[]): ManeuverTracker {
  // Index 0 is the initial departure maneuver (spoken via startPhrase on Start);
  // the last index is the destination maneuver (handled by MapScreen's arrival check).
  const initialIndex = instructions.length > 1 ? 1 : 0;
  return {
    instructions,
    currentIndex: initialIndex,
    states: instructions.map(() => ({
      approachSpoken: false,
      immediateSpoken: false,
      enteredApproachZone: false,
      minDistanceMeters: Number.POSITIVE_INFINITY,
    })),
  };
}

function isDestinationManeuver(instructions: RouteInstruction[], index: number): boolean {
  if (index >= instructions.length - 1 && instructions.length > 1) {
    return true;
  }
  const type = instructions[index]?.type;
  // Valhalla destination maneuver types: 4 (kDestination), 5 (kDestinationRight), 6 (kDestinationLeft)
  return type === 4 || type === 5 || type === 6;
}

export function collectPrefetchPhrases(tracker: ManeuverTracker): string[] {
  const phrases: string[] = [];
  for (let i = tracker.currentIndex; i < tracker.instructions.length && phrases.length < 2; i++) {
    if (isDestinationManeuver(tracker.instructions, i)) continue;
    const maneuver = tracker.instructions[i];
    const state = tracker.states[i];
    if (!maneuver || !state) continue;
    if (!state.approachSpoken) {
      const approach = approachPhrase(maneuver, APPROACH_METERS);
      if (approach) phrases.push(approach);
    }
    if (phrases.length < 2 && !state.immediateSpoken) {
      const immediate = immediatePhrase(maneuver);
      if (immediate) phrases.push(immediate);
    }
  }
  return phrases;
}

export function advanceTracker(
  tracker: ManeuverTracker,
  position: { lat: number; lng: number }
): TrackerStepResult {
  const announcements: string[] = [];

  while (tracker.currentIndex < tracker.instructions.length) {
    if (isDestinationManeuver(tracker.instructions, tracker.currentIndex)) {
      break;
    }

    const maneuver = tracker.instructions[tracker.currentIndex];
    const state = tracker.states[tracker.currentIndex];
    const coord = maneuver?.coordinate;
    if (!maneuver || !state || !Array.isArray(coord) || coord.length < 2) {
      tracker.currentIndex += 1;
      continue;
    }

    const [maneuverLng, maneuverLat] = coord;
    const distance = distanceBetweenMeters(position.lat, position.lng, maneuverLat, maneuverLng);
    const firstSightInZone = !state.enteredApproachZone;

    if (distance <= APPROACH_METERS) {
      state.enteredApproachZone = true;
    }
    if (distance < state.minDistanceMeters) {
      state.minDistanceMeters = distance;
    }

    if (distance <= IMMEDIATE_METERS) {
      state.approachSpoken = true;
      if (!state.immediateSpoken) {
        state.immediateSpoken = true;
        const phrase = immediatePhrase(maneuver);
        if (phrase) announcements.push(phrase);
      }
      if (distance <= ADVANCE_METERS) {
        tracker.currentIndex += 1;
        break;
      }
      break;
    }

    if (distance <= APPROACH_METERS) {
      if (firstSightInZone && distance < SKIP_APPROACH_METERS) {
        state.approachSpoken = true;
      } else if (!state.approachSpoken) {
        state.approachSpoken = true;
        const phrase = approachPhrase(maneuver, distance);
        if (phrase) announcements.push(phrase);
      }
      break;
    }

    if (
      state.enteredApproachZone &&
      distance > state.minDistanceMeters + RECEDING_MARGIN_METERS
    ) {
      tracker.currentIndex += 1;
      continue;
    }

    break;
  }

  return {
    announcements,
    prefetchPhrases: collectPrefetchPhrases(tracker),
  };
}
