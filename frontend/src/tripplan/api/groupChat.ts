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
}

export interface GroupChatSocketTicket {
  token: string;
  conversationId: string;
  expiresAt: string;
}

async function chatRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    return await authenticatedRequest<T>(path, options);
  } catch (error) {
    if (error instanceof ApiError && error.detail) throw new Error(error.detail);
    throw error;
  }
}

const chatPath = (groupId: string) => `/api/trip-plan-groups/${encodeURIComponent(groupId)}/chat`;

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
                                     body: string): Promise<GroupChatMessage> {
  return chatRequest(`${chatPath(groupId)}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageId, body }),
  });
}
