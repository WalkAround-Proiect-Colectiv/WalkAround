import type { Ref } from 'react';
import type { StyleSpecification } from 'maplibre-gl';
import type { MapPoi, MapZone } from './poi';
import type { FeatureCollection } from 'geojson';

export type CameraPosition = { center: [number, number]; zoom: number };
export type MapHandle = {
  fitCoverage(): void;
  goTo(position: CameraPosition): void;
  zoomBy(delta: number): void;
  resetNorth(): void;
};
export type MapCanvasProps = {
  ref?: Ref<MapHandle>;
  style: StyleSpecification;
  dangerousZonesData?: FeatureCollection;
  onReady(): void;
  onError(): void;
  onMove(position: CameraPosition): void;
  onPoiSelect(poi: MapPoi | null): void;
  onPress?(zone: MapZone | null): void;
};