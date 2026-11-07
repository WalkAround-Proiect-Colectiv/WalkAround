import { environment } from '../../config/environment';
import { getSession, refreshSession } from '../../login/api/session';

export interface TicketLink {
  source: string | null;
  url: string;
}

export interface UpcomingEvent {
  key: string;
  localityKey: string;
  title: string;
  startsAt: string;
  allDay: boolean;
  whenText: string | null;
  venueName: string | null;
  address: string | null;
  thumbnailUrl: string | null;
  tickets: TicketLink[];
  interested: boolean;
  remindBeforeMinutes: number | null;
}

export interface Locality {
  key: string;
  name: string;
  county: string | null;
}

export interface NearbyEvents {
  locality: Locality | null;
  events: UpcomingEvent[];
  fetchedAt: string | null;
  stale: boolean;
  attribution: string;
}

export interface EventInterest {
  eventKey: string;
  localityKey: string;
  title: string;
  startsAt: string;
  allDay: boolean;
  whenText: string | null;
  venueName: string | null;
  address: string | null;
  description: string | null;
  thumbnailUrl: string | null;
  ticketSource: string | null;
  ticketUrl: string | null;
  remindBeforeMinutes: number | null;
  remindAt: string | null;
  remindedAt: string | null;
}

export interface EventDetails {
  event: UpcomingEvent | null;
  interest: EventInterest | null;
}

// The first request for a town can wait on two provider searches, so allow more than the sign-in timeout.
const TIMEOUT_MS = 60_000;

async function send<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!environment.apiBaseUrl) throw new Error('Events are not configured yet.');
  let session = getSession();
  if (!session || Date.parse(session.expiresAt) <= Date.now() + 30_000) session = await refreshSession();
  if (!session) throw new Error('Please sign in to see upcoming events.');

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${session.accessToken}`);
    const response = await fetch(`${environment.apiBaseUrl}${path}`, { ...init, headers, signal: controller.signal });
    if (response.status === 401) throw new Error('Your session has expired. Please sign in again.');
    if (!response.ok) {
      const problem = await response.json().catch(() => null) as { detail?: string } | null;
      throw new Error(problem?.detail || 'The request could not be completed. Please try again.');
    }
    return response.status === 204 ? undefined as T : await response.json() as T;
  } catch (error) {
    if (controller.signal.aborted) throw new Error('The request took too long. Please try again.');
    if (error instanceof TypeError) throw new Error('Could not reach WalkAround. Check your connection.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

const json = (method: string, body: unknown): RequestInit => ({
  method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});

export function getNearbyEvents(latitude: number, longitude: number) {
  const query = new URLSearchParams({ lat: latitude.toFixed(5), lng: longitude.toFixed(5) });
  return send<NearbyEvents>(`/api/events/nearby?${query}`);
}

export function getEventDetails(localityKey: string, eventKey: string) {
  const query = new URLSearchParams({ localityKey, eventKey });
  return send<EventDetails>(`/api/events/details?${query}`);
}

export function getInterests() {
  return send<EventInterest[]>('/api/events/interests');
}

/** Marks an event, or changes its reminder. `remindBeforeMinutes` null means no reminder. */
export function markInterest(localityKey: string, eventKey: string, remindBeforeMinutes: number | null) {
  return send<EventInterest>('/api/events/interests', json('PUT', { localityKey, eventKey, remindBeforeMinutes }));
}

export function unmarkInterest(eventKey: string) {
  return send<void>(`/api/events/interests/${encodeURIComponent(eventKey)}`, { method: 'DELETE' });
}

export function getUnseenReminders() {
  return send<EventInterest[]>('/api/events/reminders/unseen');
}

export function markReminderSeen(eventKey: string) {
  return send<void>(`/api/events/interests/${encodeURIComponent(eventKey)}/reminder-seen`, { method: 'POST' });
}
