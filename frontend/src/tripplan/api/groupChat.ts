import { Platform } from 'react-native';
import type { ImagePickerAsset } from 'expo-image-picker';
import { ApiError } from '../../login/api/http';
import { authenticatedRequest } from '../../login/api/session';

export interface GroupChatMessage {
  id: string;
  conversationId: string;
  sequence: number;
  senderId: string | null;
  senderName: string;
  body: string;
  createdAt: string;
  messageType: 'TEXT' | 'POLL' | 'IMAGE';
  poll: GroupChatPoll | null;
  image: GroupChatImage | null;
}

export interface GroupChatImage {
  url: string;
  width: number;
  height: number;
}

export interface GroupChatPoll {
  revision: number;
  closedAt: string | null;
  selectedOptionId: string | null;
  options: { id: string; label: string; votes: number }[];
}

export interface GroupChatEvent {
  eventSequence: number;
  message: GroupChatMessage;
}

export interface GroupChatSocketTicket {
  token: string;
  conversationId: string;
  expiresAt: string;
}

async function chatRequest<T>(path: string, options: RequestInit = {},
                              timeoutMs = 15000): Promise<T> {
  try {
    return await authenticatedRequest<T>(path, options, 'json', timeoutMs);
  } catch (error) {
    if (error instanceof ApiError && error.detail) throw new Error(error.detail);
    throw error;
  }
}

const chatPath = (groupId: string) => `/api/trip-plan-groups/${encodeURIComponent(groupId)}/chat`;
const IMAGE_UPLOAD_TIMEOUT_MS = 60000;

export function getGroupChatTicket(groupId: string): Promise<GroupChatSocketTicket> {
  return chatRequest(`${chatPath(groupId)}/socket-ticket`);
}

export function getGroupChatMessages(groupId: string, options: {
  afterSequence?: number;
  beforeSequence?: number;
  limit?: number;
} = {}): Promise<GroupChatMessage[]> {
  const query = new URLSearchParams();
  if (options.afterSequence !== undefined) query.set('afterSequence', String(options.afterSequence));
  if (options.beforeSequence !== undefined) query.set('beforeSequence', String(options.beforeSequence));
  if (options.limit !== undefined) query.set('limit', String(options.limit));
  const suffix = query.size > 0 ? `?${query.toString()}` : '';
  return chatRequest(`${chatPath(groupId)}/messages${suffix}`);
}

export function sendGroupChatMessage(groupId: string, messageId: string,
                                     body: string, messageType: 'TEXT' | 'POLL' = 'TEXT',
                                     options?: string[]): Promise<GroupChatMessage> {
  return chatRequest(`${chatPath(groupId)}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageId, body, messageType, options }),
  });
}

export function sendGroupChatImage(groupId: string, messageId: string,
                                   caption: string, image: ImagePickerAsset): Promise<GroupChatMessage> {
  const body = new FormData();
  body.append('messageId', messageId);
  body.append('caption', caption);
  if (Platform.OS === 'web') {
    if (!image.file) throw new Error('Could not read the selected image. Choose it again.');
    body.append('image', image.file, image.fileName ?? 'chat.jpg');
  } else {
    body.append('image', {
      uri: image.uri,
      name: image.fileName ?? 'chat.jpg',
      type: image.mimeType ?? 'image/jpeg',
    } as unknown as Blob);
  }
  return chatRequest(`${chatPath(groupId)}/messages/images`, { method: 'POST', body }, IMAGE_UPLOAD_TIMEOUT_MS);
}

export function getGroupChatImageUrl(groupId: string, messageId: string): Promise<GroupChatImage> {
  return chatRequest(`${chatPath(groupId)}/messages/${encodeURIComponent(messageId)}/image-url`);
}

export function voteInGroupChatPoll(groupId: string, messageId: string,
                                    optionId: string): Promise<GroupChatMessage> {
  return chatRequest(`${chatPath(groupId)}/messages/${encodeURIComponent(messageId)}/votes`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ optionId }),
  });
}

export function closeGroupChatPoll(groupId: string, messageId: string): Promise<GroupChatMessage> {
  return chatRequest(`${chatPath(groupId)}/messages/${encodeURIComponent(messageId)}/poll/closure`, {
    method: 'PUT',
  });
}

export function getGroupChatEventCursor(groupId: string): Promise<{ eventSequence: number }> {
  return chatRequest(`${chatPath(groupId)}/events/cursor`);
}

export function getGroupChatEvents(groupId: string, afterEventSequence: number,
                                   limit: number): Promise<GroupChatEvent[]> {
  const query = new URLSearchParams({ afterEventSequence: String(afterEventSequence), limit: String(limit) });
  return chatRequest(`${chatPath(groupId)}/events?${query.toString()}`);
}
