# Audio Directions

This module provides foreground turn-by-turn voice narration for started walking routes on `/map`.

## Architecture

- [`phrases.ts`](phrases.ts): Pure helpers (`formatDistance`, `approachPhrase`, `immediatePhrase`, `startPhrase`, `arrivalPhrase`) that turn Valhalla maneuver metadata and live distance into concise English spoken prompts.
- [`tracker.ts`](tracker.ts): Pure state machine (`createTracker`, `advanceTracker`, `collectPrefetchPhrases`) that tracks the user's progress through `routeInstructions` using Euclidean distance in meters (`distanceMeters` from `src/utils/geohash.ts`).
- [`api/ttsApi.ts`](api/ttsApi.ts): Builds `/api/v1/tts?text=...` URLs and resolves/refreshes the current JWT access token from `SessionContext` (`getValidAccessToken`, `fetchSpeech`) with a 30-second expiry margin, a single `401` refresh retry, and an 8-second `AbortController` timeout.
- [`api/ttsPlayback.ts`](api/ttsPlayback.ts) & [`api/ttsPlayback.web.ts`](api/ttsPlayback.web.ts): Platform-specific `AudioSource` resolvers for `expo-audio`:
  - **Native (`ttsPlayback.ts`):** Returns `{ uri, headers: { Authorization: 'Bearer <token>' } }` so the native player streams directly from the authenticated endpoint, plus lightweight background prefetching to warm the backend LRU cache.
  - **Web (`ttsPlayback.web.ts`):** Because `expo-audio` on web uses `HTMLAudioElement` (`new Audio(uri)`) and ignores custom `headers`, the web adapter fetches the WAV `Blob` with `Authorization: Bearer <token>`, caches up to 24 blobs in memory, and passes a blob object URL (`URL.createObjectURL`) to the player.
- [`../hooks/useAudioDirections.ts`](../hooks/useAudioDirections.ts): React hook that configures `expo-audio` (`playsInSilentMode: true`, `interruptionMode: 'duckOthers'`), runs the maneuver tracker on `userLocation` updates while a route is active, prefetches the next two maneuver phrases, and exposes `isMuted`, `toggleMute`, `speakStartCue`, and `speakArrivalCue`.

## Distance Thresholds

| Constant | Value | Behavior |
| --- | --- | --- |
| `APPROACH_METERS` | `50 m` | Emits the approach prompt (e.g., `"In 50 meters, turn left onto Strada Memorandumului."`) when the user enters within 50 m of the maneuver point (`> 20 m`). |
| `SKIP_APPROACH_METERS` | `20 m` | If the user starts or jumps within 20 m of a maneuver, skips the approach prompt so approach and immediate cues do not play back-to-back. |
| `IMMEDIATE_METERS` | `15 m` | Emits the immediate prompt (`verbalPre` from Valhalla or `instruction`) when within 15 m of the maneuver point. |
| `ADVANCE_METERS` | `12 m` | Advances `currentIndex` to the next maneuver once the user is within 12 m (or after the immediate prompt fires). |

## Authentication & Failure Handling

- **Authenticated users only:** `GET /api/v1/tts` requires a valid JWT Bearer token. When `session` is `null`, `useAudioDirections` makes no network requests, `MapScreen` hides the mute button, and the active navigation header displays a `"Sign in to hear audio directions"` shortcut to `/login`.
- **Single-slot playback:** Calling `speak` replaces any currently playing phrase (`player.replace(source)` + `player.play()`) so stale cues never queue up behind newer turns.
- **Silent fallback:** Network errors, timeouts, or `502` responses are caught and ignored so visual navigation continues uninterrupted.
