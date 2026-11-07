import { Platform } from 'react-native';
import type { ImagePickerAsset } from 'expo-image-picker';
import type { RegisteredUser } from '../../register/api/registration';
import { authenticatedRequest, updateSessionUser } from '../../login/api/session';
import { ApiError } from '../../login/api/http';

export type ProfileFields = Pick<RegisteredUser, 'username' | 'firstName' | 'lastName' | 'description'>;

async function change(options: RequestInit, suffix = '') {
  try {
    const profile = await authenticatedRequest<RegisteredUser>('/api/users/me' + suffix, options);
    updateSessionUser(profile);
    return profile;
  } catch (error) {
    if (error instanceof ApiError && [400, 409, 413, 415].includes(error.status)) {
      throw new Error(error.detail ?? (error.status === 413 ? 'The selected photo is too large.' : 'Check your profile details and try again.'));
    }
    throw error;
  }
}

export function saveProfile(fields: ProfileFields) {
  return change({ method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fields) });
}

export function uploadPicture(asset: ImagePickerAsset) {
  const body = new FormData();
  if (Platform.OS === 'web') {
    if (!asset.file) throw new Error('Could not read the selected photo. Please choose it again.');
    body.append('file', asset.file, asset.fileName ?? 'profile.jpg');
  } else {
    // React Native's FormData accepts a local URI; the web implementation uses a File.
    body.append('file', { uri: asset.uri, name: asset.fileName ?? 'profile.jpg', type: asset.mimeType ?? 'image/jpeg' } as unknown as Blob);
  }
  return change({ method: 'PUT', body }, '/picture');
}

export const removePicture = () => change({ method: 'DELETE' }, '/picture');

export async function pictureSource(url: string | null): Promise<string | null> {
  if (!url) return null;
  if (!url.startsWith('/api/users/me/picture')) return url;
  const blob = await authenticatedRequest<Blob>(url, {}, 'blob');
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : null);
    reader.onerror = () => reject(new Error('Could not load your profile photo.'));
    reader.readAsDataURL(blob);
  });
}
