import { useState, useEffect, useEffectEvent, useImperativeHandle, useRef } from 'react';
import { Map, ScaleControl, setWorkerUrl } from 'maplibre-gl';
import type { GeoJSONSource } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps } from './MapCanvas.types';
import { selectMapHit } from './poi';

export default function MapCanvas({ ref, style, dangerousZonesData, onReady, onError, onMove, onPoiSelect, onPress }: MapCanvasProps) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<Map | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);
  const ready = useEffectEvent(onReady);
  const failed = useEffectEvent(onError);
  const moved = useEffectEvent(onMove);
  const selected = useEffectEvent(onPoiSelect);
  const pressed = useEffectEvent((zone: Parameters<NonNullable<MapCanvasProps['onPress']>>[0]) => onPress?.(zone));
  const currentZones = useEffectEvent(() => dangerousZonesData ?? { type: 'FeatureCollection' as const, features: [] });
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
      if (!instance.getSource('dangerous-zones-source')) {
        instance.addSource('dangerous-zones-source', {
          type: 'geojson',
          data: currentZones()
        });
      }
      if (!instance.getLayer('dangerous-zones-layer')) {
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
      }
      if (!instance.getLayer('dangerous-zones-borders')) {
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
      }
      ready();
      setMapLoaded(true);
    });
    instance.on('error', () => failed());
    instance.on('idle', () => { if (instance.areTilesLoaded()) ready(); });
    instance.on('moveend', () => {
      const center = instance.getCenter();
      moved({ center: [center.lng, center.lat], zoom: instance.getZoom() });
    });
    instance.on('click', event => {
      const layers = ['poi-dot', 'poi-label'].filter(id => instance.getLayer(id));
      const poiFeatures = layers.length ? instance.queryRenderedFeatures([[event.point.x - 12, event.point.y - 12], [event.point.x + 12, event.point.y + 12]], { layers }) : [];
      const zoneFeatures = instance.getLayer('dangerous-zones-layer')
        ? instance.queryRenderedFeatures(event.point, { layers: ['dangerous-zones-layer'] }) : [];
      const hit = selectMapHit(poiFeatures, zoneFeatures);
      selected(hit.poi);
      pressed(hit.zone);
    });
    const observer = new ResizeObserver(() => instance.resize());
    observer.observe(container.current);
    return () => { observer.disconnect(); instance.remove(); map.current = null; setMapLoaded(false);};
  }, [style]);
  useEffect(() => {
    if (mapLoaded && map.current) {
      const source = map.current.getSource('dangerous-zones-source') as GeoJSONSource | undefined;
      if (source) {
        source.setData(dangerousZonesData ?? { type: 'FeatureCollection', features: [] });
      }
    }
  }, [dangerousZonesData, mapLoaded]);
  return <div ref={container} aria-label="Map of Romania" style={{ position: 'absolute', inset: 0 }} />;
}