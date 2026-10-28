import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { registerWithGoogle } from '../src/register/api/registration.ts';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('posts only the Google credential and returns the created profile', async () => {
  const profile = { id: 'user-id', firstName: 'Alex' };
  globalThis.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:8080/api/registrations/google');
    assert.equal(options.method, 'POST');
    assert.deepEqual(JSON.parse(options.body), { idToken: 'google-token' });
    assert.equal(options.headers['Content-Type'], 'application/json');
    return new Response(JSON.stringify(profile), { status: 201 });
  };
  assert.deepEqual(await registerWithGoogle('http://localhost:8080', 'google-token'), profile);
});

test('maps duplicate, invalid-token, and unavailable responses to useful messages', async () => {
  for (const [status, message] of [[409, /already have/], [401, /verification failed/], [503, /unavailable/], [500, /Registration failed/]]) {
    globalThis.fetch = async () => new Response('Internal details must not be shown', { status });
    await assert.rejects(registerWithGoogle('http://localhost:8080', 'token'), message);
  }
});

test('does not send credentials if the API URL has not been configured', async () => {
  globalThis.fetch = async () => { assert.fail('Must not send a request'); };
  await assert.rejects(registerWithGoogle('', 'token'), /unavailable/);
});

test('passes cancellation through to fetch', async () => {
  const controller = new AbortController();
  controller.abort();
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.signal, controller.signal);
    options.signal.throwIfAborted();
  };
  await assert.rejects(registerWithGoogle('http://localhost:8080', 'token', controller.signal), { name: 'AbortError' });
});
