import type { Ref } from 'react';
import type { StyleSpecification } from 'maplibre-gl';
import type { MapPoi, MapZone } from './poi';
import type { Feature, FeatureCollection } from 'geojson';

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
  searchedLocation?: [longitude: number, latitude: number] | null;
  zoomLevel?: number;
  dangerousZonesData?: FeatureCollection;
  routeData?: Feature | FeatureCollection;
  routePointsData?: FeatureCollection;
  onReady(): void;
  onError(): void;
  onMove(position: CameraPosition): void;
  onPoiSelect(poi: MapPoi | null): void;
  onPress?(zone: MapZone | null, coordinates?: [number, number]): void;
  onCoordinateSelect?(coordinates: [longitude: number, latitude: number]): void;
};
