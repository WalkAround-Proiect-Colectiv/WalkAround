import * as SecureStore from 'expo-secure-store';
import { ApiError, jsonPost, request } from './http';
import type { Session, MobileSession } from './types';

const key = 'walkaround.refreshToken';
const path = '/api/mobile/sessions';

async function save(result: MobileSession): Promise<Session> {
  try {
    await SecureStore.setItemAsync(key, result.refreshToken, {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    });
    return result.session;
  } catch {
    await request(path + '/logout', jsonPost({ refreshToken: result.refreshToken })).catch(() => {});
    await SecureStore.deleteItemAsync(key);
    throw new Error('Could not securely save your session. Please try again.');
  }
}

export async function login(idToken: string): Promise<Session> {
  return save(await request<MobileSession>(path + '/google', jsonPost({ idToken })));
}

export async function refresh(): Promise<Session | null> {
  const refreshToken = await SecureStore.getItemAsync(key);
  if (!refreshToken) return null;
  try {
    return await save(await request<MobileSession>(path + '/refresh', jsonPost({ refreshToken })));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) await SecureStore.deleteItemAsync(key);
    throw error;
  }
}

export async function logout(): Promise<void> {
  const refreshToken = await SecureStore.getItemAsync(key);
  if (refreshToken) await request(path + '/logout', jsonPost({ refreshToken }));
  await SecureStore.deleteItemAsync(key);
}
