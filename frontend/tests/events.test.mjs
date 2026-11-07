import { test } from 'node:test';
import assert from 'node:assert/strict';
import { REMINDER_OPTIONS, formatEventDate, isSafeLink, reminderLabel } from '../src/events/format.ts';

test('reminder options match the backend limits and include turning reminders off', () => {
  assert.equal(REMINDER_OPTIONS[0].minutes, null);
  for (const option of REMINDER_OPTIONS.slice(1)) {
    assert.ok(option.minutes >= 1 && option.minutes <= 43200, option.label);
  }
  assert.equal(reminderLabel(1440), '1 day before');
  assert.equal(reminderLabel(null), 'Off');
  assert.equal(reminderLabel(30), '30 minutes before');
});

test('event dates show the time only for timed events', () => {
  const timed = formatEventDate('2026-11-06T18:00:00Z', false);
  const allDay = formatEventDate('2026-11-06T00:00:00Z', true);
  assert.match(timed, /Nov/);
  assert.match(timed, /\d{2}:\d{2}$/);
  assert.doesNotMatch(allDay, /:/);
  assert.equal(formatEventDate('not a date', false), '');
});

test('only https links from provider data can be opened', () => {
  assert.equal(isSafeLink('https://www.bilete.ro/the-rite-of-spring/'), true);
  assert.equal(isSafeLink('http://example.com'), false);
  assert.equal(isSafeLink('javascript:alert(1)'), false);
  assert.equal(isSafeLink('https://example.com/a b'), false);
  assert.equal(isSafeLink(null), false);
});
