import { environment } from '../../config/environment';
import { getSession, refreshSession } from '../../login/api/session';

export interface CitySuggestion {
  name: string;
  country: string;
  countryCode: string;
  lookupName: string;
  latitude: number;
  longitude: number;
}

export interface Airport {
  id: number;
  name: string;
  iataCode: string | null;
  latitude: number;
  longitude: number;
}

export type TicketOption = 'ROUND_TRIP' | 'TWO_ONE_WAY';

export interface FlightSegment {
  departureAirport: string;
  departureTime: string;
  arrivalAirport: string;
  arrivalTime: string;
  flightNumber: string;
  airline: string;
}

export interface FlightItinerary {
  ticketOption: TicketOption;
  currency: string;
  totalPrice: number;
  outbound: FlightSegment[];
  returning: FlightSegment[];
  viewUrls: string[];
}

export interface FlightSearchInput {
  originAirport: string;
  destinationCity: string;
  countryCode: string;
  budget: number;
  departureFrom: string;
  departureUntil: string;
  stayingNights: number;
  ticketOption: TicketOption;
}

async function send<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!environment.apiBaseUrl) {
    throw new Error('The trip planner is not configured yet.');
  }

  let session = getSession();
  if (!session || Date.parse(session.expiresAt) <= Date.now() + 30_000) {
    session = await refreshSession();
  }
  if (!session) {
    throw new Error('Please sign in to plan your trip.');
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 90_000);

  try {
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${session.accessToken}`);

    const response = await fetch(`${environment.apiBaseUrl}${path}`, {
      ...init,
      headers,
      signal: controller.signal,
    });

    if (response.status === 401) {
      throw new Error('Your session has expired. Please sign in again.');
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => null) as { detail?: string } | null;
      throw new Error(problem?.detail || 'The request could not be completed. Please try again.');
    }

    return await response.json() as T;
  } catch (error) {
    if (controller.signal.aborted) {
      throw new Error('The search took too long. Please try a shorter departure period.');
    }
    if (error instanceof TypeError) {
      throw new Error('Could not reach WalkAround. Check your connection.');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function searchCities(city: string): Promise<CitySuggestion[]> {
  return send(`/api/trip-plans/cities?city=${encodeURIComponent(city.trim())}`);
}

export function getCityAirports(city: CitySuggestion): Promise<Airport[]> {
  const name = encodeURIComponent(city.lookupName || city.name);
  const country = encodeURIComponent(city.countryCode);
  return send(`/api/trip-plans/cities/${name}/airports?countryCode=${country}`);
}

export function searchFlights(input: FlightSearchInput): Promise<FlightItinerary[]> {
  return send('/api/trip-plans/flight-searches', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
}
