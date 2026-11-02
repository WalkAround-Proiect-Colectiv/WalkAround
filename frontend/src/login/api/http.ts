import { environment } from '../../config/environment';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message); }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (!environment.apiBaseUrl) throw new Error('Sign-in is currently unavailable.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(environment.apiBaseUrl + path, { ...options, signal: controller.signal });
    if (!response.ok) {
      const messages: Record<number, string> = {
        400: 'Please try signing in again.',
        401: 'Your session has expired. Please sign in again.',
        403: 'Sign-in is not available from this address.',
        404: 'No account found. Please create your WalkAround account first.',
        503: 'Sign-in is currently unavailable. Please try again later.',
      };
      throw new ApiError(response.status, messages[response.status] ?? 'The request failed. Please try again.');
    }
    return response.status === 204 ? undefined as T : await response.json();
  } catch (error) {
    if (controller.signal.aborted) throw new Error('The request timed out. Please try again.');
    if (error instanceof TypeError) throw new Error('Could not reach WalkAround. Check your connection.');
    throw error;
  } finally { clearTimeout(timeout); }
}

export function jsonPost(body: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}
