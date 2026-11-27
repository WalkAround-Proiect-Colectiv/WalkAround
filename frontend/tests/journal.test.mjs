import { test } from 'node:test';
import assert from 'node:assert/strict';
import { addNights, dateError, departureWindow, isDate } from '../src/journal/dates.ts';
import { createJournalApi } from '../src/journal/api.ts';

test('journal dates allow same-day trips and reject past, reversed and invalid dates', () => {
  assert.equal(dateError('2028-02-28', '2028-02-28', '2028-02-28'), null);
  assert.match(dateError('2028-02-28', '2028-02-27', '2028-03-01'), /today/);
  assert.match(dateError('2028-02-28', '2028-03-01', '2028-02-28'), /end date/);
  assert.equal(isDate('2028-02-30'), false);
  assert.equal(isDate('not a date'), false);
});

test('saved nights use calendar dates across leap years, new years and daylight saving', () => {
  assert.equal(addNights('2028-02-28', 2), '2028-03-01');
  assert.equal(addNights('2028-12-31', 2), '2029-01-02');
  assert.equal(addNights('2028-03-25', 2), '2028-03-27');
  assert.equal(addNights('', 2), '');
});

test('departure windows remove past dates but expired plans can be shown for custom-date imports', () => {
  assert.deepEqual(departureWindow('2028-02-25T10:00:00', '2028-03-01T22:00:00', '2028-02-28'),
    { minimum: '2028-02-28', maximum: '2028-03-01', available: true });
  assert.equal(departureWindow('2025-01-01', '2025-01-03', '2028-02-28').available, false);
});

test('journal requests use private endpoints, stable create request IDs and owner-scoped PUT/DELETE routes', async () => {
  const calls = [];
  const api = createJournalApi(async (path, options) => { calls.push({ path, ...options }); return {}; });
  const input = { title: 'Weekend', description: 'A trip', destinationCity: 'Cluj-Napoca', countryCode: 'RO', startDate: '2028-03-01', endDate: null,
    sourcePlanId: 'plan', startMode: 'CUSTOM', keepNights: true };
  await api.create(input, 'attempt'); await api.create(input, 'attempt');
  assert.equal(calls[0].body, calls[1].body);
  assert.equal(JSON.parse(calls[0].body).requestId, 'attempt');
  await api.plans(2); assert.equal(calls[2].path, '/api/trip-plans?page=2');
  await api.update('trip', { ...input, sourcePlanId: null, startMode: null, keepNights: null });
  assert.equal(calls[3].method, 'PUT');
  assert.equal(JSON.parse(calls[3].body).requestId, 'trip');
  await api.remove('trip'); assert.equal(calls[4].method, 'DELETE');
});