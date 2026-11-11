import { useCallback, useEffect, useRef, useState } from 'react';
import { setAudioModeAsync, useAudioPlayer } from 'expo-audio';
import {
  clearSpeechCache,
  prefetchSpeech,
  resolveSpeechSource,
  type ResolvedSpeechSource,
} from '../audio-directions/api/ttsPlayback';
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

export type UseAudioDirectionsParams = {
  instructions: RouteInstruction[];
  userLocation: UserLocation;
  isNavigating: boolean;
  signedIn: boolean;
};

export type UseAudioDirectionsResult = {
  available: boolean;
  muted: boolean;
  toggleMuted: () => void;
  speakStartCue: (overrideInstructions?: RouteInstruction[]) => void;
  speakArrivalCue: () => void;
};

export function useAudioDirections({
  instructions,
  userLocation,
  isNavigating,
  signedIn,
}: UseAudioDirectionsParams): UseAudioDirectionsResult {
  const player = useAudioPlayer(null);
  const [muted, setMuted] = useState(false);

  const mutedRef = useRef(muted);
  useEffect(() => {
    mutedRef.current = muted;
  }, [muted]);

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

  const trackerRef = useRef<ManeuverTracker>(createTracker(instructions));
  const activeAbortRef = useRef<AbortController | null>(null);
  const prefetchAbortRef = useRef<AbortController | null>(null);
  const activeSourceDisposeRef = useRef<(() => void) | null>(null);
  const prefetchedSetRef = useRef<Set<string>>(new Set());
  const warnedForTripRef = useRef(false);

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
      if (!signedInRef.current || authFailedRef.current) return;
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
    [handleFailure]
  );

  const playPhrase = useCallback(
    (phrase: string) => {
      const trimmed = phrase.trim();
      if (!trimmed || !signedInRef.current || mutedRef.current || authFailedRef.current) {
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
          if (!resolved || controller.signal.aborted || mutedRef.current || !signedInRef.current) {
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
    [handleFailure, player]
  );

  // Configure audio session when navigation starts for signed-in users.
  useEffect(() => {
    if (!isNavigating || !signedIn) return;
    void setAudioModeAsync({
      playsInSilentMode: true,
      interruptionMode: 'duckOthers',
    }).catch(() => {});
  }, [isNavigating, signedIn]);

  // Reset tracker and state when instructions change or navigation stops.
  useEffect(() => {
    trackerRef.current = createTracker(instructions);
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

  // Advance maneuver tracker as GPS updates arrive during active navigation.
  useEffect(() => {
    if (!isNavigating || !signedIn || authFailedRef.current || !userLocation) return;

    const [lng, lat] = userLocation.coordinates;
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

    const { announcements, prefetchPhrases } = advanceTracker(trackerRef.current, { lat, lng });
    triggerPrefetch(prefetchPhrases);

    if (announcements.length > 0 && !mutedRef.current) {
      const latestAnnouncement = announcements[announcements.length - 1];
      playPhrase(latestAnnouncement);
    }
  }, [isNavigating, playPhrase, signedIn, triggerPrefetch, userLocation]);

  // Clean up on unmount.
  useEffect(() => {
    return () => {
      prefetchAbortRef.current?.abort();
      stopPlayback();
    };
  }, [stopPlayback]);

  const toggleMuted = useCallback(() => {
    setMuted(prev => {
      const next = !prev;
      mutedRef.current = next;
      if (next) {
        stopPlayback();
      }
      return next;
    });
  }, [stopPlayback]);

  const speakStartCue = useCallback(
    (overrideInstructions?: RouteInstruction[]) => {
      authFailedRef.current = false;
      if (!signedInRef.current || mutedRef.current) return;
      const list = overrideInstructions ?? instructionsRef.current;
      const phrase = startPhrase(list[0] ?? null);
      playPhrase(phrase);
    },
    [playPhrase]
  );

  const speakArrivalCue = useCallback(() => {
    if (!signedInRef.current || mutedRef.current || authFailedRef.current) return;
    playPhrase(arrivalPhrase());
  }, [playPhrase]);

  return {
    available: signedIn,
    muted,
    toggleMuted,
    speakStartCue,
    speakArrivalCue,
  };
}
