import { environment } from '../../config/environment';
import { ApiError } from '../../login/api/http';
import { getSession, refreshSession } from '../../login/api/session';

const TTS_TIMEOUT_MS = 8000;

export function buildTtsPath(text: string): string {
  return `/api/v1/tts?text=${encodeURIComponent(text.trim())}`;
}

export function buildTtsUrl(text: string): string | null {
  const trimmed = text.trim();
  if (!trimmed || !environment.apiBaseUrl) return null;
  return `${environment.apiBaseUrl}${buildTtsPath(trimmed)}`;
}

export async function getValidAccessToken(): Promise<string | null> {
  let session = getSession();
  if (!session) return null;
  if (Date.parse(session.expiresAt) <= Date.now() + 30000) {
    session = await refreshSession();
  }
  return session?.accessToken ?? null;
}

export async function fetchSpeech(text: string, externalSignal?: AbortSignal): Promise<Blob | null> {
  const url = buildTtsUrl(text);
  if (!url || !getSession()) return null;

  const token = await getValidAccessToken();
  if (!token) throw new ApiError(401, 'Please sign in again.');

  const execute = async (accessToken: string): Promise<Blob> => {
    const controller = new AbortController();
    const onExternalAbort = () => controller.abort();
    if (externalSignal?.aborted) {
      controller.abort();
    } else {
      externalSignal?.addEventListener('abort', onExternalAbort, { once: true });
    }
    const timeout = setTimeout(() => controller.abort(), TTS_TIMEOUT_MS);

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: { Authorization: `Bearer ${accessToken}` },
        signal: controller.signal,
      });

      if (!response.ok) {
        const problem = await response.json().catch(() => null);
        const detail = typeof problem?.detail === 'string' ? problem.detail : undefined;
        throw new ApiError(
          response.status,
          detail ?? 'Audio directions are temporarily unavailable.',
          detail
        );
      }

      return await response.blob();
    } finally {
      clearTimeout(timeout);
      externalSignal?.removeEventListener('abort', onExternalAbort);
    }
  };

  try {
    return await execute(token);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    const replacement = await refreshSession();
    if (!replacement) throw error;
    return await execute(replacement.accessToken);
  }
}
