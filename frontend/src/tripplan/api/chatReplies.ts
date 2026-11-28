import type { GroupChatMessage } from './groupChat';

export type GroupChatReply = {
  messageId: string;
  senderName: string;
  messageType: 'TEXT' | 'POLL' | 'IMAGE' | 'DOCUMENT';
  preview: string;
};

export function readChatReply(value: unknown): GroupChatReply | null {
  if (!value || typeof value !== 'object') return null;
  const reply = value as Record<string, unknown>;
  if (typeof reply.messageId !== 'string' || !reply.messageId
      || typeof reply.senderName !== 'string' || typeof reply.preview !== 'string'
      || !['TEXT', 'POLL', 'IMAGE', 'DOCUMENT'].includes(String(reply.messageType))) return null;
  return {
    messageId: reply.messageId, senderName: reply.senderName,
    messageType: reply.messageType as GroupChatReply['messageType'],
    preview: Array.from(reply.preview).slice(0, 160).join(''),
  };
}

export function quoteMessage(message: { id: string; senderName: string; messageType: GroupChatReply['messageType']; body: string;
  document?: { fileName: string } | null }): GroupChatReply {
  const text = message.messageType === 'IMAGE' ? 'Photo'
    : message.messageType === 'DOCUMENT' ? `Document: ${message.document?.fileName ?? 'Attachment'}`
    : message.messageType === 'POLL' ? `Poll: ${message.body}` : message.body;
  return {
    messageId: message.id, senderName: message.senderName, messageType: message.messageType,
    preview: Array.from(text.replace(/\s+/g, ' ').trim()).slice(0, 160).join(''),
  };
}

export function replyRequest(reply?: GroupChatReply | null): { replyToMessageId?: string } {
  return reply ? { replyToMessageId: reply.messageId } : {};
}

export async function loadReplyHistory(messageId: string, options: {
  messages: GroupChatMessage[];
  hasOlder: boolean;
  pageSize: number;
  loadEarlier: (beforeSequence: number) => Promise<GroupChatMessage[]>;
  isActive: () => boolean;
}): Promise<boolean> {
  let page = options.messages;
  let hasOlder = options.hasOlder;
  while (options.isActive()) {
    if (page.some((message) => message.id === messageId)) return true;
    const first = page[0];
    if (!hasOlder || !first) throw new Error('The original message is no longer available.');
    page = await options.loadEarlier(first.sequence);
    if (!options.isActive()) return false;
    if (page.length > 0 && page[0].sequence >= first.sequence) {
      throw new Error('Could not load earlier messages. Please try again.');
    }
    hasOlder = page.length === options.pageSize;
  }
  return false;
}
