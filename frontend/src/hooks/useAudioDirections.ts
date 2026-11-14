import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { setAudioModeAsync, useAudioPlayer } from 'expo-audio';
import {
  clearSpeechCache,
  prefetchSpeech,
  resolveSpeechSource,
  type ResolvedSpeechSource,
} from '../audio-directions/api/ttsPlayback';
import {
  advanceDangerTracker,
  buildDangerIndex,
  createDangerTracker,
  type DangerTracker,
  type DangerZoneInput,
  type DangerIndex,
  type LngLat,
} from '../audio-directions/dangerTracker';
import {
  arrivalPhrase,
  startPhrase,
  type RouteInstruction,
} from '../audio-directions/phrases';
import {
  advanceTracker,
  collectPrefetchPhrases,
  createTracker,
  type ManeuverTracker,
} from '../audio-directions/tracker';
import { ApiError } from '../login/api/http';
import type { UserLocation } from '../map/MapCanvas.types';

/** `full`: turn-by-turn directions. `danger`: only danger-zone alerts. `off`: silent. */
export type AudioMode = 'full' | 'danger' | 'off';

const NEXT_MODE: Record<AudioMode, AudioMode> = {
  full: 'danger',
  danger: 'off',
  off: 'full',
};

type CueKind = 'route' | 'danger';

export type UseAudioDirectionsParams = {
  instructions: RouteInstruction[];
  userLocation: UserLocation;
  isNavigating: boolean;
  signedIn: boolean;
  dangerZones: DangerZoneInput[];
  routeCoordinates: LngLat[];
};

export type UseAudioDirectionsResult = {
  available: boolean;
  mode: AudioMode;
  cycleMode: () => void;
  speakStartCue: (overrideInstructions?: RouteInstruction[]) => void;
  speakArrivalCue: () => void;
};

export function useAudioDirections({
  instructions,
  userLocation,
  isNavigating,
  signedIn,
  dangerZones,
  routeCoordinates,
}: UseAudioDirectionsParams): UseAudioDirectionsResult {
  const player = useAudioPlayer(null);
  const [mode, setMode] = useState<AudioMode>('full');

  const modeRef = useRef(mode);
  useEffect(() => {
    modeRef.current = mode;
  }, [mode]);

  const signedInRef = useRef(signedIn);
  useEffect(() => {
    signedInRef.current = signedIn;
    if (!signedIn) {
      clearSpeechCache();
    }
  }, [signedIn]);

  const authFailedRef = useRef(false);

  const instructionsRef = useRef(instructions);
  useEffect(() => {
    instructionsRef.current = instructions;
  }, [instructions]);

  const dangerIndex = useMemo<DangerIndex | null>(() => buildDangerIndex(dangerZones), [dangerZones]);
  const dangerIndexRef = useRef(dangerIndex);
  useEffect(() => {
    dangerIndexRef.current = dangerIndex;
  }, [dangerIndex]);

  const routeCoordinatesRef = useRef(routeCoordinates);
  useEffect(() => {
    routeCoordinatesRef.current = routeCoordinates;
  }, [routeCoordinates]);

  const trackerRef = useRef<ManeuverTracker>(createTracker(instructions));
  const dangerTrackerRef = useRef<DangerTracker>(createDangerTracker());
  const activeAbortRef = useRef<AbortController | null>(null);
  const prefetchAbortRef = useRef<AbortController | null>(null);
  const activeSourceDisposeRef = useRef<(() => void) | null>(null);
  const prefetchedSetRef = useRef<Set<string>>(new Set());
  const warnedForTripRef = useRef(false);

  const cueAllowed = useCallback((kind: CueKind) => {
    return modeRef.current === (kind === 'route' ? 'full' : 'danger');
  }, []);

  const stopPlayback = useCallback(() => {
    activeAbortRef.current?.abort();
    activeAbortRef.current = null;
    try {
      player.pause();
    } catch {
      // Ignore if player is already released.
    }
    if (activeSourceDisposeRef.current) {
      activeSourceDisposeRef.current();
      activeSourceDisposeRef.current = null;
    }
  }, [player]);

  const handleFailure = useCallback((error: unknown) => {
    if (error instanceof ApiError && error.status === 401) {
      authFailedRef.current = true;
    }
    if (!warnedForTripRef.current) {
      warnedForTripRef.current = true;
      console.warn('Audio directions cue skipped:', error instanceof Error ? error.message : error);
    }
  }, []);

  const triggerPrefetch = useCallback(
    (phrases: string[]) => {
      if (!signedInRef.current || authFailedRef.current || !cueAllowed('route')) return;
      const unseen = phrases.filter(text => text && !prefetchedSetRef.current.has(text)).slice(0, 2);
      if (unseen.length === 0) return;

      prefetchAbortRef.current?.abort();
      const controller = new AbortController();
      prefetchAbortRef.current = controller;

      unseen.forEach(phrase => {
        prefetchedSetRef.current.add(phrase);
        void prefetchSpeech(phrase, controller.signal).catch(error => {
          prefetchedSetRef.current.delete(phrase);
          if (controller.signal.aborted) return;
          handleFailure(error);
        });
      });
    },
    [cueAllowed, handleFailure]
  );

  const playPhrase = useCallback(
    (phrase: string, kind: CueKind) => {
      const trimmed = phrase.trim();
      if (!trimmed || !signedInRef.current || !cueAllowed(kind) || authFailedRef.current) {
        return;
      }

      // Single-slot replacement queue: abort any in-flight resolution and replace current playback.
      activeAbortRef.current?.abort();
      const controller = new AbortController();
      activeAbortRef.current = controller;

      void (async () => {
        let resolved: ResolvedSpeechSource | null = null;
        try {
          resolved = await resolveSpeechSource(trimmed, controller.signal);
          if (!resolved || controller.signal.aborted || !cueAllowed(kind) || !signedInRef.current) {
            resolved?.dispose?.();
            return;
          }

          if (activeSourceDisposeRef.current) {
            activeSourceDisposeRef.current();
          }
          activeSourceDisposeRef.current = resolved.dispose ?? null;

          player.pause();
          player.replace(resolved.source);
          player.play();
        } catch (error) {
          resolved?.dispose?.();
          if (controller.signal.aborted) return;
          handleFailure(error);
        }
      })();
    },
    [cueAllowed, handleFailure, player]
  );

  // Configure audio session when navigation starts for signed-in users.
  useEffect(() => {
    if (!isNavigating || !signedIn) return;
    void setAudioModeAsync({
      playsInSilentMode: true,
      interruptionMode: 'duckOthers',
    }).catch(() => {});
  }, [isNavigating, signedIn]);

  // Reset trackers and state when instructions change or navigation stops.
  useEffect(() => {
    trackerRef.current = createTracker(instructions);
    dangerTrackerRef.current = createDangerTracker();
    prefetchedSetRef.current.clear();

    if (!isNavigating) {
      warnedForTripRef.current = false;
      authFailedRef.current = false;
      prefetchAbortRef.current?.abort();
      prefetchAbortRef.current = null;
      stopPlayback();
      return;
    }

    triggerPrefetch(collectPrefetchPhrases(trackerRef.current));
  }, [instructions, isNavigating, stopPlayback, triggerPrefetch]);

  // Advance maneuver and danger-zone trackers as GPS updates arrive during active navigation.
  useEffect(() => {
    if (!isNavigating || !signedIn || authFailedRef.current || !userLocation) return;

    const [lng, lat] = userLocation.coordinates;
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

    const { announcements, prefetchPhrases } = advanceTracker(trackerRef.current, { lat, lng });
    triggerPrefetch(prefetchPhrases);

    const dangerAnnouncements = advanceDangerTracker(
      dangerTrackerRef.current,
      dangerIndexRef.current,
      { lat, lng },
      routeCoordinatesRef.current
    );

    if (cueAllowed('route') && announcements.length > 0) {
      playPhrase(announcements[announcements.length - 1], 'route');
    } else if (cueAllowed('danger') && dangerAnnouncements.length > 0) {
      playPhrase(dangerAnnouncements[dangerAnnouncements.length - 1], 'danger');
    }
  }, [cueAllowed, isNavigating, playPhrase, signedIn, triggerPrefetch, userLocation]);

  // Clean up on unmount.
  useEffect(() => {
    return () => {
      prefetchAbortRef.current?.abort();
      stopPlayback();
    };
  }, [stopPlayback]);

  const cycleMode = useCallback(() => {
    const next = NEXT_MODE[modeRef.current];
    modeRef.current = next;
    setMode(next);
    stopPlayback();
  }, [stopPlayback]);

  const speakStartCue = useCallback(
    (overrideInstructions?: RouteInstruction[]) => {
      authFailedRef.current = false;
      if (!signedInRef.current) return;
      const list = overrideInstructions ?? instructionsRef.current;
      playPhrase(startPhrase(list[0] ?? null), 'route');
    },
    [playPhrase]
  );

  const speakArrivalCue = useCallback(() => {
    if (!signedInRef.current || authFailedRef.current) return;
    playPhrase(arrivalPhrase(), 'route');
  }, [playPhrase]);

  return {
    available: signedIn,
    mode,
    cycleMode,
    speakStartCue,
    speakArrivalCue,
  };
}
