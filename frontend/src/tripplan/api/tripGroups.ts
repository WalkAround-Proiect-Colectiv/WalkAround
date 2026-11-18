import { Platform } from 'react-native';
import type { ImagePickerAsset } from 'expo-image-picker';
import { ApiError } from '../../login/api/http';
import { authenticatedRequest } from '../../login/api/session';
import type { SavedTripPlan } from './tripPlanning';

export interface CreatedTripGroup {
  id: string;
  name: string;
  description: string | null;
  memberRole: 'ADMIN' | 'MEMBER';
  pictureUrl: string | null;
  createdAt: string;
}

export interface TripGroupInvite {
  id: string;
  token: string;
  expiresAt: string;
}

export interface TripGroupInviteSummary {
  id: string;
  createdAt: string;
  expiresAt: string;
  usedAt: string | null;
  revokedAt: string | null;
}

export interface TripGroupInvitePreview {
  groupName: string;
  expiresAt: string;
  pictureAvailable: boolean;
}

async function groupRequest<T>(path: string, options: RequestInit = {},
                               responseType: 'json' | 'blob' = 'json'): Promise<T> {
  try {
    return await authenticatedRequest<T>(path, options, responseType);
  } catch (error) {
    if (error instanceof ApiError && error.detail) throw new Error(error.detail);
    throw error;
  }
}

export const listTripGroups = () => groupRequest<CreatedTripGroup[]>('/api/trip-plan-groups');

export const getTripGroup = (id: string) =>
  groupRequest<CreatedTripGroup>(`/api/trip-plan-groups/${encodeURIComponent(id)}`);

export const getTripGroupPicture = (id: string) =>
  groupRequest<Blob>(`/api/trip-plan-groups/${encodeURIComponent(id)}/picture`, {}, 'blob');

export const listGroupPlans = (groupId: string) =>
  groupRequest<SavedTripPlan[]>(`/api/trip-plan-groups/${encodeURIComponent(groupId)}/plans`);

export const makeGroupPlanCurrent = (groupId: string, planId: string) =>
  groupRequest<SavedTripPlan>(
    `/api/trip-plan-groups/${encodeURIComponent(groupId)}/plans/${encodeURIComponent(planId)}/current`,
    { method: 'PUT' },
  );

export const createGroupInvite = (groupId: string) =>
  groupRequest<TripGroupInvite>(`/api/trip-plan-groups/${encodeURIComponent(groupId)}/invitations`, {
    method: 'POST',
  });

export const listGroupInvites = (groupId: string) =>
  groupRequest<TripGroupInviteSummary[]>(`/api/trip-plan-groups/${encodeURIComponent(groupId)}/invitations`);

export const revokeGroupInvite = (groupId: string, invitationId: string) =>
  groupRequest<void>(`/api/trip-plan-groups/${encodeURIComponent(groupId)}/invitations/${encodeURIComponent(invitationId)}`, {
    method: 'DELETE',
  });

const invitationBody = (token: string): RequestInit => ({
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
});

export const previewGroupInvite = (token: string) =>
  groupRequest<TripGroupInvitePreview>('/api/trip-plan-groups/invitations/preview', invitationBody(token));

export const getGroupInvitePicture = (token: string) =>
  groupRequest<Blob>('/api/trip-plan-groups/invitations/picture', invitationBody(token), 'blob');

export const acceptGroupInvite = (token: string) =>
  groupRequest<CreatedTripGroup>('/api/trip-plan-groups/invitations/accept', invitationBody(token));

function groupFormData(name: string, description: string | null,
                       picture: ImagePickerAsset | null): FormData {
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

  return body;
}

export async function createTripGroup(name: string, description: string | null,
                                      picture: ImagePickerAsset | null): Promise<CreatedTripGroup> {
  const body = groupFormData(name, description, picture);
  return groupRequest<CreatedTripGroup>('/api/trip-plan-groups', { method: 'POST', body });
}

export async function updateTripGroup(id: string, name: string, description: string | null,
                                      picture: ImagePickerAsset | null, removePicture: boolean): Promise<CreatedTripGroup> {
  const body = groupFormData(name, description, picture);
  body.append('removePicture', String(removePicture));
  return groupRequest<CreatedTripGroup>(`/api/trip-plan-groups/${encodeURIComponent(id)}`, {
    method: 'PUT', body,
  });
}
