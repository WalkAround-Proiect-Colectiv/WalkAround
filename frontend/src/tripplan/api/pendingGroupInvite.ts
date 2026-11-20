import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

const KEY = 'walkaround_pending_group_invite';
let memoryToken: string | null = null;

export async function savePendingGroupInvite(token: string): Promise<void> {
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new Error('This invitation link is invalid.');
  memoryToken = token;
  if (Platform.OS === 'web') {
    window.sessionStorage.setItem(KEY, token);
  } else {
    await SecureStore.setItemAsync(KEY, token);
  }
}

export async function readPendingGroupInvite(): Promise<string | null> {
  if (memoryToken) return memoryToken;
  const token = Platform.OS === 'web'
    ? window.sessionStorage.getItem(KEY)
    : await SecureStore.getItemAsync(KEY);
  memoryToken = token && /^[A-Za-z0-9_-]{43}$/.test(token) ? token : null;
  return memoryToken;
}

export async function clearPendingGroupInvite(): Promise<void> {
  memoryToken = null;
  if (Platform.OS === 'web') {
    window.sessionStorage.removeItem(KEY);
  } else {
    await SecureStore.deleteItemAsync(KEY);
  }
}
