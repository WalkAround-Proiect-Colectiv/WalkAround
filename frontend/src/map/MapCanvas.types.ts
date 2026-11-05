import type { Ref } from 'react';
import type { StyleSpecification } from 'maplibre-gl';

export type CameraPosition = { center: [number, number]; zoom: number };
export type UserLocation = { coordinates: [longitude: number, latitude: number]; accuracy: number } | null;
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
  zoomLevel?: number;
  dangerousZonesData?: any;
  routeData?: any;
  routePointsData?: any;
  onReady(): void;
  onError(): void;
  onMove(position: CameraPosition): void;
  onPress?(featureProperties: any | null, coordinates?: [number, number]): void;
};