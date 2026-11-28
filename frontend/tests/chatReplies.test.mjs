import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadReplyHistory, quoteMessage, readChatReply, replyRequest } from '../src/tripplan/api/chatReplies.ts';

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

const history = (first, last) => Array.from({ length: last - first + 1 }, (_, index) =>
  ({ id: `message-${first + index}`, sequence: first + index }));

test('navigating to an already loaded original does not request history', async () => {
  assert.equal(await loadReplyHistory('message-75', {
    messages: history(51, 100), hasOlder: true, pageSize: 50, isActive: () => true,
    loadEarlier: async () => assert.fail('The original is already loaded'),
  }), true);
});

test('reply navigation loads consecutive older pages and stops as soon as the original is found', async () => {
  const cursors = [];
  assert.equal(await loadReplyHistory('message-7', {
    messages: history(151, 200), hasOlder: true, pageSize: 50, isActive: () => true,
    loadEarlier: async (before) => {
      cursors.push(before);
      return history(before - 50, before - 1);
    },
  }), true);
  assert.deepEqual(cursors, [151, 101, 51]);
});

test('missing originals stop at the end of history rather than loading indefinitely', async () => {
  for (const lastPage of [[], history(1, 12)]) {
    let requests = 0;
    await assert.rejects(loadReplyHistory('missing', {
      messages: history(13, 62), hasOlder: true, pageSize: 50, isActive: () => true,
      loadEarlier: async () => { requests++; return lastPage; },
    }), /original message is no longer available/i);
    assert.equal(requests, 1);
  }
  await assert.rejects(loadReplyHistory('missing', {
    messages: history(1, 12), hasOlder: false, pageSize: 50, isActive: () => true,
    loadEarlier: async () => assert.fail('History is exhausted'),
  }), /original message is no longer available/i);
});

test('cancelling while a page is in flight prevents any further history requests', async () => {
  let active = true;
  let finishPage;
  let requests = 0;
  const result = loadReplyHistory('message-1', {
    messages: history(101, 150), hasOlder: true, pageSize: 50, isActive: () => active,
    loadEarlier: () => {
      requests++;
      return new Promise((resolve) => { finishPage = resolve; });
    },
  });
  active = false;
  finishPage(history(51, 100));
  assert.equal(await result, false);
  assert.equal(requests, 1);
});

test('history failures and non-advancing cursors surface errors without looping', async () => {
  const options = { messages: history(51, 100), hasOlder: true, pageSize: 50, isActive: () => true };
  await assert.rejects(loadReplyHistory('message-1', {
    ...options, loadEarlier: async () => { throw new Error('Connection lost'); },
  }), /Connection lost/);
  await assert.rejects(loadReplyHistory('message-1', {
    ...options, loadEarlier: async () => history(51, 100),
  }), /Could not load earlier messages/);
});