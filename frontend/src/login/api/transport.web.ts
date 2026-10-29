import { ApiError, jsonPost, request } from './http';
import type { Session } from './types';

const path = '/api/sessions';

function withSessionLock<T>(operation: () => Promise<T>): Promise<T> {
  // Login/logout must use the same lock as rotation so another tab cannot
  // replace the refresh cookie while a logout request is in flight.
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request('walkaround-refresh', operation);
  }
  return operation();
}

export function login(idToken: string): Promise<Session> {
  return withSessionLock(() => request<Session>(path + '/google', { ...jsonPost({ idToken }), credentials: 'include' }));
}

export async function refresh(): Promise<Session | null> {
  const rotate = async () => {
    try { return await request<Session>(path + '/refresh', { method: 'POST', credentials: 'include' }); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) return null;
      throw error;
    }
  };
  return withSessionLock(rotate);
}

export function logout(): Promise<void> {
  return withSessionLock(() => request<void>(path + '/logout', { method: 'POST', credentials: 'include' }));
}
