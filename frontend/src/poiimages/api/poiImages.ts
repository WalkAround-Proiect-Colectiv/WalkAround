import { Platform } from 'react-native';
import type { ImagePickerAsset } from 'expo-image-picker';
import { authenticatedRequest } from '../../login/api/session';
import { ApiError } from '../../login/api/http';
import { MAX_UPLOAD_BYTES } from '../limits';

export { CAPTION_MAX_LENGTH, MAX_UPLOAD_BYTES } from '../limits';

export interface PoiImage {
  id: string;
  poiId: string;
  caption: string | null;
  width: number;
  height: number;
  uploadedByName: string;
  /** The current user added this photo and may delete it. */
  mine: boolean;
  createdAt: string;
  /** Signed ImageKit URL; stops working after about an hour, so photos are listed again when the card opens. */
  imageUrl: string;
  /** Signed ImageKit URL of a small version made by ImageKit. */
  thumbnailUrl: string;
}

/** Turns API errors into messages for the photo section, keeping the backend's explanation when it gives one. */
async function call<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    return await authenticatedRequest<T>(path, options);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 413) throw new Error(error.detail ?? 'The selected photo is too large.');
      if ([400, 403, 404, 409, 503].includes(error.status) && error.detail) throw new Error(error.detail);
    }
    throw error;
  }
}

export function listPoiImages(poiId: string) {
  return call<PoiImage[]>(`/api/poi-images?${new URLSearchParams({ poiId })}`);
}

export async function uploadPoiImage(poiId: string, asset: ImagePickerAsset, caption: string) {
  if (typeof asset.fileSize === 'number' && asset.fileSize > MAX_UPLOAD_BYTES) {
    throw new Error('The selected photo is too large. Choose one under 5 MB.');
  }
  const body = new FormData();
  if (Platform.OS === 'web') {
    if (!asset.file) throw new Error('Could not read the selected photo. Please choose it again.');
    body.append('file', asset.file, asset.fileName ?? 'photo.jpg');
  } else {
    // React Native's FormData accepts a local URI; the web implementation uses a File.
    body.append('file', { uri: asset.uri, name: asset.fileName ?? 'photo.jpg', type: asset.mimeType ?? 'image/jpeg' } as unknown as Blob);
  }
  const trimmed = caption.trim();
  if (trimmed) body.append('caption', trimmed);
  return call<PoiImage>(`/api/poi-images?${new URLSearchParams({ poiId })}`, { method: 'POST', body });
}

export function deletePoiImage(id: string) {
  return call<void>(`/api/poi-images/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
