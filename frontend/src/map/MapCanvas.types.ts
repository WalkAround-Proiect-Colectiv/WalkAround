import type { Ref } from 'react';
import type { StyleSpecification } from 'maplibre-gl';
import type { EventPinsFeatureCollection } from '../events/mapPins';

export type CameraPosition = { center: [number, number]; zoom: number };
export type UserLocation = { coordinates: [longitude: number, latitude: number]; accuracy: number } | null;
export type ScreenPoint = { x: number; y: number };
export type MapHandle = {
  fitCoverage(): void;
  goTo(position: CameraPosition): void;
  zoomBy(delta: number): void;
  resetNorth(): void;
};
export type MapCanvasProps = {
  ref?: Ref<MapHandle>;
  style: StyleSpecification;
  userLocation?: UserLocation;
  searchedLocation?: [longitude: number, latitude: number] | null;
  zoomLevel?: number;
  dangerousZonesData?: any;
  routeData?: any;
  routePointsData?: any;
  eventPinsData?: EventPinsFeatureCollection | null;
  selectedEventPinId?: string | null;
  selectedEventPinCoordinates?: [longitude: number, latitude: number] | null;
  onEventPinPress?(pinId: string): void;
  onSelectedPinScreenPoint?(point: ScreenPoint | null): void;
  onReady(): void;
  onError(): void;
  onMove(position: CameraPosition): void;
  onPress?(featureProperties: any | null, coordinates?: [number, number]): void;
  onCoordinateSelect?(coordinates: [longitude: number, latitude: number]): void;
};
