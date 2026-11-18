import { Platform } from 'react-native';
import type { ImagePickerAsset } from 'expo-image-picker';
import { ApiError } from '../../login/api/http';
import { authenticatedRequest } from '../../login/api/session';

export interface CreatedTripGroup {
  id: string;
  name: string;
  description: string | null;
  memberRole: 'ADMIN' | 'MEMBER';
  pictureUrl: string | null;
  createdAt: string;
}

export async function createTripGroup(name: string, description: string | null,
                                      picture: ImagePickerAsset | null): Promise<CreatedTripGroup> {
  const body = new FormData();
  body.append('name', name);
  if (description) body.append('description', description);

  if (picture) {
    if (Platform.OS === 'web') {
      if (!picture.file) throw new Error('Could not read the selected photo. Choose it again.');
      body.append('picture', picture.file, picture.fileName ?? 'group.jpg');
    } else {
      body.append('picture', {
        uri: picture.uri,
        name: picture.fileName ?? 'group.jpg',
        type: picture.mimeType ?? 'image/jpeg',
      } as unknown as Blob);
    }
  }

  try {
    return await authenticatedRequest<CreatedTripGroup>('/api/trip-plan-groups', {
      method: 'POST', body,
    });
  } catch (error) {
    if (error instanceof ApiError && error.detail) throw new Error(error.detail);
    throw error;
  }
}
