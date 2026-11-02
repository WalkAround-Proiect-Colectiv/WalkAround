import * as transport from './transport';
import { ApiError, request } from './http';
import type { Session } from './types';
import type { RegisteredUser } from '../../register/api/registration';

let current: Session | null = null;
let refreshPromise: Promise<Session | null> | null = null;
const listeners = new Set<() => void>();

export const getSession = () => current;
export function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
function publish(session: Session | null) {
  current = session;
  listeners.forEach(listener => listener());
}

export async function refreshSession(): Promise<Session | null> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const session = await transport.refresh();
      publish(session);
      return session;
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) publish(null);
      throw error;
    } finally { refreshPromise = null; }
  })();
  return refreshPromise;
}

export async function signIn(idToken: string) {
  if (refreshPromise) await refreshPromise.catch(() => {});
  const session = await transport.login(idToken);
  publish(session);
}

export async function signOut() {
  if (refreshPromise) await refreshPromise.catch(() => {});
  await transport.logout();
  publish(null);
}

// Future feature API clients can use this helper; requests are retried only once.
export async function authenticatedRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  let session = current;
  if (!session || Date.parse(session.expiresAt) <= Date.now() + 30000) session = await refreshSession();
  if (!session) throw new ApiError(401, 'Please sign in again.');
  const send = (token: string) => {
    const headers = new Headers(options.headers);
    headers.set('Authorization', 'Bearer ' + token);
    return request<T>(path, { ...options, headers });
  };
  try { return await send(session.accessToken); }
  catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    const replacement = await refreshSession();
    if (!replacement) throw error;
    return send(replacement.accessToken);
  }
}

export function getCurrentUser() {
  return authenticatedRequest<RegisteredUser>('/api/users/me');
}
