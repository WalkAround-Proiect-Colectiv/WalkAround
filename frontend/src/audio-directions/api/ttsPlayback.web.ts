import type { AudioSource } from 'expo-audio';
import { fetchSpeech } from './ttsApi';

export type ResolvedSpeechSource = {
  source: AudioSource;
  dispose?: () => void;
};

const MAX_BLOB_CACHE = 24;
const blobCache = new Map<string, Blob>();
const inflightRequests = new Map<string, Promise<Blob | null>>();

function normalizeKey(text: string): string {
  return text.trim().replace(/\s+/g, ' ');
}

function storeBlob(key: string, blob: Blob): void {
  if (blobCache.has(key)) {
    blobCache.delete(key);
  } else if (blobCache.size >= MAX_BLOB_CACHE) {
    const oldest = blobCache.keys().next().value;
    if (oldest !== undefined) blobCache.delete(oldest);
  }
  blobCache.set(key, blob);
}

async function getOrFetchBlob(text: string, signal?: AbortSignal): Promise<Blob | null> {
  const key = normalizeKey(text);
  if (!key) return null;

  const cached = blobCache.get(key);
  if (cached) {
    blobCache.delete(key);
    blobCache.set(key, cached);
    return cached;
  }

  const existing = inflightRequests.get(key);
  if (existing && !signal) {
    return existing;
  }

  const request = fetchSpeech(key, signal)
    .then(blob => {
      if (blob) storeBlob(key, blob);
      return blob;
    })
    .finally(() => {
      inflightRequests.delete(key);
    });

  if (!signal) {
    inflightRequests.set(key, request);
  }
  return request;
}

export async function resolveSpeechSource(
  text: string,
  signal?: AbortSignal
): Promise<ResolvedSpeechSource | null> {
  if (signal?.aborted) return null;
  const blob = await getOrFetchBlob(text, signal);
  if (!blob || signal?.aborted) return null;
  const objectUrl = URL.createObjectURL(blob);
  return {
    source: objectUrl,
    dispose: () => {
      URL.revokeObjectURL(objectUrl);
    },
  };
}

export async function prefetchSpeech(text: string, signal?: AbortSignal): Promise<void> {
  await getOrFetchBlob(text, signal);
}

export function clearSpeechCache(): void {
  blobCache.clear();
  inflightRequests.clear();
}
