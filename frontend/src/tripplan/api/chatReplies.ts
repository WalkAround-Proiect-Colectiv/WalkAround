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
