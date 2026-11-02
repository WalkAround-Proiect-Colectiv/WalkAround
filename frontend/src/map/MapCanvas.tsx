import { useImperativeHandle, useRef } from 'react';
import { Camera, Map, type CameraRef } from '@maplibre/maplibre-react-native';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps } from './MapCanvas.types';

export default function MapCanvas({ ref, style, onReady, onError, onMove }: MapCanvasProps) {
  const camera = useRef<CameraRef>(null);
  const zoom = useRef(PLACES[0].zoom);
  const center = useRef(PLACES[0].center);
  useImperativeHandle(ref, () => ({
    fitCoverage: () => camera.current?.fitBounds([...ROMANIA_BOUNDS[0], ...ROMANIA_BOUNDS[1]], { padding: { top: 24, right: 24, bottom: 24, left: 24 }, duration: 900, bearing: 0, pitch: 0 }),
    goTo: position => camera.current?.flyTo({ ...position, bearing: 0, pitch: 0, duration: 900 }),
    zoomBy: delta => camera.current?.zoomTo(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom.current + delta)), { duration: 250 }),
    resetNorth: () => camera.current?.easeTo({ center: center.current, bearing: 0, pitch: 0, duration: 250 }),
  }), []);
  return <Map mapStyle={style} style={{ flex: 1 }} touchPitch={false} attribution logo={false}
    tintColor="#0065EB" attributionPosition={{ bottom: 8, right: 8 }}
    onDidFinishLoadingMap={onReady} onDidFailLoadingMap={onError}
    onRegionDidChange={event => {
      zoom.current = event.nativeEvent.zoom;
      center.current = event.nativeEvent.center;
      onMove({ center: event.nativeEvent.center, zoom: event.nativeEvent.zoom });
    }}>
    <Camera ref={camera} initialViewState={{ bounds: [...ROMANIA_BOUNDS[0], ...ROMANIA_BOUNDS[1]], padding: { top: 24, right: 24, bottom: 24, left: 24 } }} minZoom={MIN_ZOOM} maxZoom={MAX_ZOOM} />
  </Map>;
}