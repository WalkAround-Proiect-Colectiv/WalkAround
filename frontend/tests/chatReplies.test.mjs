import { test } from 'node:test';
import assert from 'node:assert/strict';
import { quoteMessage, readChatReply, replyRequest } from '../src/tripplan/api/chatReplies.ts';

test('reply previews handle text, polls, images and Unicode without nested content', () => {
  const parent = { id: 'parent', senderName: 'Walker', messageType: 'TEXT', body: ' Meet\n at the entrance ', replyTo: { private: 'not copied' } };
  assert.deepEqual(quoteMessage(parent), { messageId: 'parent', senderName: 'Walker', messageType: 'TEXT', preview: 'Meet at the entrance' });
  assert.equal(quoteMessage({ ...parent, messageType: 'IMAGE', body: 'private media URL' }).preview, 'Photo');
  assert.equal(quoteMessage({ ...parent, messageType: 'POLL', body: 'Where?' }).preview, 'Poll: Where?');
  const emoji = String.fromCodePoint(0x1f600);
  assert.equal(quoteMessage({ ...parent, body: emoji.repeat(170) }).preview, emoji.repeat(160));
});

test('socket reply parsing accepts legacy messages and copies only supported preview fields', () => {
  assert.equal(readChatReply(undefined), null);
  assert.equal(readChatReply(null), null);
  assert.equal(readChatReply({ messageId: 'id', preview: 42 }), null);
  assert.equal(readChatReply({ messageId: 'id', senderName: 'Walker', messageType: 'UNKNOWN', preview: '' }), null);
  assert.deepEqual(readChatReply({ messageId: 'id', senderName: 'Walker', messageType: 'TEXT', preview: 'Hello', private: 'not copied' }),
    { messageId: 'id', senderName: 'Walker', messageType: 'TEXT', preview: 'Hello' });
});

test('send and retry transmit only the captured parent identity, not a client-authored quote', () => {
  const reply = { messageId: 'original', senderName: 'Walker', messageType: 'TEXT', preview: 'Hello' };
  const pending = { id: 'retry-id', body: 'Answer', replyTo: reply };
  let selected = reply;
  selected = null;
  assert.deepEqual(replyRequest(pending.replyTo), { replyToMessageId: 'original' });
  assert.deepEqual(replyRequest(selected), {});
  assert.deepEqual(replyRequest(undefined), {});
});