import type { AudioSource } from 'expo-audio';
import { buildTtsUrl, fetchSpeech, getValidAccessToken } from './ttsApi';

export type ResolvedSpeechSource = {
  source: AudioSource;
  dispose?: () => void;
};

export async function resolveSpeechSource(
  text: string,
  signal?: AbortSignal
): Promise<ResolvedSpeechSource | null> {
  const url = buildTtsUrl(text);
  if (!url || signal?.aborted) return null;
  const token = await getValidAccessToken();
  if (!token || signal?.aborted) return null;
  return {
    source: {
      uri: url,
      headers: { Authorization: `Bearer ${token}` },
    },
  };
}

export async function prefetchSpeech(text: string, signal?: AbortSignal): Promise<void> {
  await fetchSpeech(text, signal);
}

export function clearSpeechCache(): void {
  // Native relies on the backend LRU cache and HTTP cache headers.
}
