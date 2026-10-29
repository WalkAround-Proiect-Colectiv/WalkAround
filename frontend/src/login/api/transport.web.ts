import { ApiError, jsonPost, request } from './http';
import type { Session } from './types';

const path = '/api/sessions';

export function login(idToken: string): Promise<Session> {
  return request(path + '/google', { ...jsonPost({ idToken }), credentials: 'include' });
}

export async function refresh(): Promise<Session | null> {
  const rotate = async () => {
    try { return await request<Session>(path + '/refresh', { method: 'POST', credentials: 'include' }); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) return null;
      throw error;
    }
  };
  // Serialize cookie rotation across tabs when the browser supports Web Locks.
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request('walkaround-refresh', rotate);
  }
  return rotate();
}

export function logout(): Promise<void> {
  return request(path + '/logout', { method: 'POST', credentials: 'include' });
}
