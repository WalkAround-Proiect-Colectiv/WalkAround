# Text-to-Speech (`tts`)

This package exposes authenticated text-to-speech synthesis for turn-by-turn pedestrian route navigation via the local Piper container.

## Endpoint

- **`GET /api/v1/tts?text=...`**
  - **Authentication:** Required (JWT Bearer token). The endpoint is intentionally not whitelisted in `SecurityConfiguration`, so unauthenticated callers receive `401 Unauthorized`.
  - **Validation:** `text` is normalized (trimmed and collapsed whitespace) and must be non-blank and at most `app.tts.max-text-length` characters (default `200`). Invalid requests return `400 Bad Request` (`ProblemDetail`).
  - **Response:** `200 OK` with `Content-Type: audio/wav` and `Cache-Control: private, max-age=86400`.
  - **Upstream Failures:** If the Piper container is unreachable, times out, or returns an invalid payload, the handler logs only the failure category (never the user's text or upstream body) and returns `502 Bad Gateway` (`ProblemDetail`: `"Audio directions are temporarily unavailable"`).

## Caching & Provider

- `TextToSpeechServiceImpl` normalizes the requested phrase and checks a bounded in-memory LRU cache (`app.tts.cache-max-entries`, default `500`) keyed by the SHA-256 digest of the normalized text.
- On a cache miss, `PiperTextToSpeechProvider` (`app.tts.provider=piper`) sends `POST /synthesize` with `{"text": "..."}` to `app.tts.base-url` (default `http://localhost:5000`) using a `RestClient` configured with `app.tts.timeout` (default `PT5S`).
- Only valid WAV payloads (`RIFF`/`WAVE` header) are cached and returned.
