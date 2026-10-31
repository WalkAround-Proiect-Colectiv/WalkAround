import type { Ref } from 'react';
import type { StyleSpecification } from 'maplibre-gl';

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
  onReady(): void;
  onError(): void;
  onMove(position: CameraPosition): void;
};