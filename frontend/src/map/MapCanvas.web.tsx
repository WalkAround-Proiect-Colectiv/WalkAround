import { useEffect, useEffectEvent, useImperativeHandle, useRef } from 'react';
import { Map, ScaleControl, setWorkerUrl } from 'maplibre-gl';
import type { GeoJSONSource } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps } from './MapCanvas.types';

export default function MapCanvas({ ref, style, dangerousZonesData, onReady, onError, onMove, onPress }: MapCanvasProps) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<Map | null>(null);
  const ready = useEffectEvent(onReady);
  const failed = useEffectEvent(onError);
  const moved = useEffectEvent(onMove);
  const pressed = useEffectEvent((props: any) => onPress && onPress(props));
  useImperativeHandle(ref, () => ({
    fitCoverage: () => { map.current?.fitBounds(ROMANIA_BOUNDS, { padding: 24, bearing: 0, pitch: 0, duration: 900 }); },
    goTo: position => { map.current?.flyTo({ ...position, bearing: 0, pitch: 0, duration: 900 }); },
    zoomBy: delta => { if (map.current) map.current.zoomTo(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, map.current.getZoom() + delta))); },
    resetNorth: () => { map.current?.easeTo({ bearing: 0, pitch: 0 }); },
  }), []);
  useEffect(() => {
    if (!container.current) return;
    let instance: Map;
    try {
      setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');
      instance = new Map({ container: container.current, style, center: PLACES[0].center,
        bounds: ROMANIA_BOUNDS, fitBoundsOptions: { padding: 24 },
        minZoom: MIN_ZOOM, maxZoom: MAX_ZOOM,
        maxPitch: 0, attributionControl: { compact: true } });
    } catch { failed(); return; }
    map.current = instance;
    instance.addControl(new ScaleControl({ unit: 'metric' }), 'bottom-left');
    instance.on('load', () => {
      instance.addSource('dangerous-zones-source', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });
      instance.addLayer({
        id: 'dangerous-zones-layer',
        type: 'fill',
        source: 'dangerous-zones-source',
        paint: {
          'fill-color': [
            'step',
            ['get', 'intensity'],
            '#FFF9C4', 3, '#FFEB3B', 5, '#FFC107', 10, '#FF9800', 15, '#D93838'
          ],
          'fill-opacity': 0.45
        }
      });
      instance.addLayer({
        id: 'dangerous-zones-borders',
        type: 'line',
        source: 'dangerous-zones-source',
        paint: {
          'line-color': '#d93838',
          'line-width': 1,
          'line-opacity': 0.6
        }
      });
      ready();
    });
    instance.on('error', () => failed());
    instance.on('idle', () => { if (instance.areTilesLoaded()) ready(); });
    instance.on('moveend', () => {
      const center = instance.getCenter();
      moved({ center: [center.lng, center.lat], zoom: instance.getZoom() });
    });
    instance.on('click', (e) => {
      const features = instance.queryRenderedFeatures(e.point, {
        layers: ['dangerous-zones-layer']
      });
      if (features.length > 0) {
        pressed(features[0].properties);
      } else {
        pressed(null);
      }
    });
    const observer = new ResizeObserver(() => instance.resize());
    observer.observe(container.current);
    return () => { observer.disconnect(); instance.remove(); map.current = null; };
  }, [style]);
  useEffect(() => {
    if (map.current && dangerousZonesData) {
      const source = map.current.getSource('dangerous-zones-source') as GeoJSONSource | undefined;
      if (source) {
        source.setData(dangerousZonesData);
      }
    }
  }, [dangerousZonesData]);
  return <div ref={container} aria-label="Map of Romania" style={{ position: 'absolute', inset: 0 }} />;
}