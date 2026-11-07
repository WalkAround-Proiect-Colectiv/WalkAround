export type WeatherBucket = 'CLEAR' | 'CLOUDY' | 'RAIN' | 'STORM' | 'SNOW' | 'FOG';

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export type LocationStatus = 'loading' | 'granted' | 'denied' | 'unavailable';

export interface ActivityRecommendationItem {
  id: string;
  name: string;
  category: string;
  categoryDisplayName: string;
  indoor: boolean;
  latitude: number;
  longitude: number;
  distanceMeters: number;
  reason: string;
  openingHours: string | null;
  address: string | null;
}

export interface RecommendationResponse {
  weatherBucket: WeatherBucket;
  temperatureC: number | null;
  weatherSummary: string;
  degraded: boolean;
  recommendations: ActivityRecommendationItem[];
  generatedAt: string;
}
