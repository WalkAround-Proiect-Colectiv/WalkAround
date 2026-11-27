import type { SavedTripPlan } from '../tripplan/api/tripPlanning';

export type JournalTrip = {
  id: string; title: string; description: string; destinationCity: string; countryCode: string; countryName: string;
  startDate: string; endDate: string; sourcePlanId: string | null; createdAt: string; updatedAt: string;
};
export type TripInput = {
  title: string; description: string; destinationCity: string; countryCode: string; startDate: string; endDate: string | null;
  sourcePlanId: string | null; startMode: 'PLANNED' | 'CUSTOM' | null; keepNights: boolean | null;
};
export type Page<Item> = { items: Item[]; nextPage: number | null };
type Sender = <Result>(path: string, options?: RequestInit) => Promise<Result>;

export function createJournalApi(send: Sender) {
  const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  return {
    settings: () => send<{ today: string; timeZone: string }>('/api/journal/trips/settings'),
    list: (page = 0) => send<Page<JournalTrip>>(`/api/journal/trips?page=${page}`),
    get: (id: string) => send<JournalTrip>(`/api/journal/trips/${encodeURIComponent(id)}`),
    plans: (page = 0) => send<Page<SavedTripPlan>>(`/api/trip-plans?page=${page}`),
    create: (input: TripInput, requestId: string) => send<JournalTrip>('/api/journal/trips', json('POST', { ...input, requestId })),
    update: (id: string, input: TripInput) => send<JournalTrip>(`/api/journal/trips/${encodeURIComponent(id)}`, json('PUT', { ...input, requestId: id })),
    remove: (id: string) => send<void>(`/api/journal/trips/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  };
}

export function journalError(error: unknown): string {
  const problem = error as { status?: number; detail?: string } | null;
  if (problem?.status === 401) return 'Please sign in again to open your private journal.';
  if (problem?.status === 404) return 'This trip or personal saved plan is no longer available.';
  if (problem?.status === 400 || problem?.status === 409) return problem.detail || 'Check the trip fields and dates, then try again.';
  return 'Your journal is unavailable right now. Please try again.';
}