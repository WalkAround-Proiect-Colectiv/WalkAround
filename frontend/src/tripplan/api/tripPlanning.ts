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

export interface PlaceRecommendation {
  name: string;
  category: string | null;
  address: string | null;
  description: string | null;
  rating: number | null;
  reviews: number | null;
  latitude: number | null;
  longitude: number | null;
  mapsUrl: string;
  imageUrl: string | null;
  websiteUrl: string | null;
}

export interface PlaceRecommendationPage {
  items: PlaceRecommendation[];
  nextPage: number | null;
}

export interface SafetyAdvisorySource {
  agency: string | null;
  level: number | null;
  summary: string | null;
  url: string | null;
  updatedAt: string | null;
}

export interface SafetyAdvisory {
  maxLevel: number | null;
  summary: string | null;
  sources: SafetyAdvisorySource[];
}

export interface SafetyAlert {
  id: number | null;
  type: string | null;
  severity: string | null;
  location: string | null;
  description: string | null;
  eventDate: string | null;
  source: string | null;
  distanceKm: number;
}

export interface SafetyConflict {
  id: number | null;
  severity: string | null;
  location: string | null;
  description: string | null;
  eventDate: string | null;
  sourceUrl: string | null;
  distanceKm: number;
}

export interface DestinationSafety {
  city: string;
  country: string;
  countryCode: string;
  nearbyRadiusKm: number;
  checkedAt: string;
  advisory: SafetyAdvisory | null;
  alerts: SafetyAlert[];
  conflicts: SafetyConflict[];
  alertsPartial: boolean;
  conflictsPartial: boolean;
}

export interface WeatherDay {
  date: string;
  weatherCode: number | null;
  temperatureMaxC: number | null;
  temperatureMinC: number | null;
  precipitationProbabilityPercent: number | null;
  precipitationMm: number | null;
  windSpeedMaxKmh: number | null;
}

export interface DestinationWeather {
  city: string;
  country: string;
  countryCode: string;
  timezone: string;
  days: WeatherDay[];
}

export interface HotlineNumber {
  value: string;
  type: 'voice' | 'sms' | 'text' | 'short_code';
}

export interface CountryHotline {
  id: string;
  name: string | null;
  organization: string | null;
  category: string | null;
  generalEmergency: boolean;
  numbers: HotlineNumber[];
  lastVerified: string | null;
  verificationStatus: string | null;
  scope: string | null;
  geography: string | null;
  hours: string | null;
  cost: string | null;
  target: string | null;
  languages: string[] | null;
  website: string | null;
  chatUrl: string | null;
  email: string | null;
  notes: string | null;
  sourceLinks: string[] | null;
  source: string;
  datasetVersion: string;
}

export interface CountryHotlines {
  countryCode: string;
  hotlines: CountryHotline[];
}

export interface NewsArticle {
  id: string | null;
  title: string;
  description: string | null;
  url: string;
  imageUrl: string | null;
  author: string | null;
  language: string | null;
  categories: string[];
  publishedAt: string | null;
}

export type PlaceCategory = 'attractions' | 'restaurants' | 'museums' | 'coffee-shops';

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

export interface AccommodationSearchInput {
  destinationCity: string;
  countryCode: string;
  checkInDate: string;
  stayingNights: number;
  budget: number;
}

export interface AccommodationBookingOption {
  source: string;
  totalPrice: number;
  currency: string;
  bookingUrl: string;
}

export interface AccommodationOffer {
  name: string;
  propertyType: string | null;
  totalPrice: number;
  currency: string;
  checkInDate: string;
  checkOutDate: string;
  propertyWebsiteUrl: string | null;
  description: string | null;
  address: string | null;
  overallRating: number | null;
  reviews: number | null;
  amenities: string[];
  roomTypes: string[];
  bookingOptions: AccommodationBookingOption[];
}

export interface SaveTripPlanInput {
  title: string;
  description: string | null;
  originAirport: string;
  departureCity: string;
  departureCountryCode: string;
  destinationCity: string;
  countryCode: string;
  departureFrom: string;
  departureUntil: string;
  stayingNights: number;
  ticketOption: TicketOption;
  flightBudget: number;
  accommodationBudget: number;
}

export interface SavedTripPlan {
  id: string;
  title: string;
  createdAt: string;
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
      throw new Error('The request took too long. Please try again.');
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

export function getPlaceRecommendations(
  city: CitySuggestion, category: PlaceCategory, page = 0,
): Promise<PlaceRecommendationPage> {
  const name = encodeURIComponent(city.lookupName || city.name);
  const country = encodeURIComponent(city.countryCode);
  return send(`/api/trip-plans/cities/${name}/${category}?countryCode=${country}&page=${page}`);
}

export function getDestinationSafety(city: CitySuggestion): Promise<DestinationSafety> {
  const name = encodeURIComponent(city.lookupName || city.name);
  const country = encodeURIComponent(city.countryCode);
  return send(`/api/trip-plans/cities/${name}/safety?countryCode=${country}`);
}

export function getDestinationWeather(city: CitySuggestion): Promise<DestinationWeather> {
  const name = encodeURIComponent(city.lookupName || city.name);
  const country = encodeURIComponent(city.countryCode);
  return send(`/api/trip-plans/cities/${name}/weather?countryCode=${country}`);
}

export function getCountryHotlines(countryCode: string): Promise<CountryHotlines> {
  return send(`/api/trip-plans/countries/${encodeURIComponent(countryCode)}/hotlines`);
}

export function getCityNews(city: CitySuggestion): Promise<NewsArticle[]> {
  const name = encodeURIComponent(city.lookupName || city.name);
  const country = encodeURIComponent(city.countryCode);
  return send(`/api/trip-plans/cities/${name}/news?countryCode=${country}`);
}

export function searchFlights(input: FlightSearchInput): Promise<FlightItinerary[]> {
  return send('/api/trip-plans/flight-searches', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
}

export function searchAccommodations(input: AccommodationSearchInput): Promise<AccommodationOffer[]> {
  return send('/api/trip-plans/accommodation-searches', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
}

export function saveTripPlan(input: SaveTripPlanInput): Promise<SavedTripPlan> {
  return send('/api/trip-plans', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
}
