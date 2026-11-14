# Text-to-Speech Service (Piper)

This directory contains the Docker build definition for the local **Piper** neural text-to-speech (TTS) service used to synthesize spoken turn-by-turn pedestrian directions.

## Overview

The container wraps `piper.http_server` from [`piper-tts`](https://pypi.org/project/piper-tts/) and pre-downloads the configured voice model into `/voices` at image build time so synthesis requires no runtime downloads.

The container is bound to `127.0.0.1:${PIPER_HOST_PORT}` on the host. Client applications never call Piper directly; requests go through the **Application API** (`GET /api/v1/tts`), which enforces authentication, input validation, and response caching.

### HTTP Interface (Internal)

- **Endpoint:** `POST /synthesize`
- **Content-Type:** `application/json`
- **Body:** `{"text": "Turn left in 50 meters"}`
- **Response:** `200 OK` (`audio/wav` bytes)

## Voice Selection & Configuration

By default, the service uses the English female voice **`en_US-lessac-medium`**, paired with `en-US` directions from Valhalla.

Configure the service in the root `.env` file (based on `.env.example`):

```dotenv
PIPER_VOICE=en_US-lessac-medium
PIPER_HOST_PORT=5000
PIPER_CONTAINER_PORT=5000
```

### Changing the Voice

1. Browse available voices in the [Piper voices catalog](https://huggingface.co/rhasspy/piper-voices).
2. Update `PIPER_VOICE` in the root `.env` file (for example, `en_US-amy-medium` or `en_US-hfc_female-medium`).
3. Rebuild and restart the container so the new `.onnx` and `.onnx.json` model files are baked into the image:
   ```sh
   docker compose up -d --build piper
   ```

## Licensing

Piper software and its voice models are licensed separately:

1. **Piper Engine (`piper-tts`):** Open-source under GPL-3.0 / MIT (see the upstream repository for version-specific license terms).
2. **Voice Models (`rhasspy/piper-voices`):** Each voice inherits the license of its training dataset, documented in its `MODEL_CARD` in the Hugging Face repository:
   - `en_US-lessac-medium`: Trained on the Lessac corpus (Blizzard Challenge 2013), which is restricted to non-commercial / research use. Suitable for university prototype development.
   - For commercial or public redistribution, switch `PIPER_VOICE` to a permissively licensed female voice (such as `en_US-hfc_female-medium` under CC0 or `en_US-amy-medium`) and include any required attribution in the application's credits.
