import { test } from 'node:test';
import assert from 'node:assert/strict';
import { commentError, createCommentsApi, mergeComments } from '../src/poi-comments/api.ts';

test('comment reads are public and preserve namespaced POI identities in query parameters', async () => {
  const api = createCommentsApi(async path => {
    const url = new URL(path, 'http://localhost');
    assert.equal(url.pathname, '/api/poi-comments');
    assert.equal(url.searchParams.get('poiId'), 'osm:n/123');
    assert.equal(url.searchParams.get('cursor'), 'page+two');
    return { items: [], nextCursor: null };
  }, async () => assert.fail('Public reads must not refresh or require a session'));
  assert.deepEqual(await api.list('osm:n/123', 'page+two'), { items: [], nextCursor: null });
});

test('posting, editing, deleting and reports use only the authenticated sender', async () => {
  const calls = [];
  const api = createCommentsApi(async () => assert.fail('Writes must require authentication'), async (path, options) => {
    calls.push({ path, ...options });
    return { id: 'comment' };
  });
  await api.create('osm:w/123', '  A useful tip  ');
  await api.edit('comment', '  Updated tip  ');
  await api.remove('comment');
  await api.report('comment', '  Spam  ');
  assert.deepEqual(calls.map(call => call.method), ['POST', 'PUT', 'DELETE', 'POST']);
  assert.deepEqual(JSON.parse(calls[0].body), { poiId: 'osm:w/123', body: 'A useful tip' });
  assert.deepEqual(JSON.parse(calls[1].body), { body: 'Updated tip' });
  assert.equal(calls[3].path, '/api/poi-comments/comment/reports');
  assert.deepEqual(JSON.parse(calls[3].body), { reason: 'Spam' });
});

test('loading additional pages does not duplicate comments already shown', () => {
  assert.deepEqual(mergeComments([{ id: 'one' }], [{ id: 'one' }, { id: 'two' }, { id: 'two' }]), [{ id: 'one' }, { id: 'two' }]);
});

test('comment errors are useful without exposing server responses or identity details', () => {
  assert.match(commentError({ status: 401 }), /sign in/i);
  assert.match(commentError({ status: 403 }), /permission/);
  assert.match(commentError({ status: 429 }), /wait a minute/);
  assert.doesNotMatch(commentError({ status: 500, detail: 'private SQL error' }), /private SQL/);
});