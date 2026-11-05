import { useEffect, useEffectEvent, useImperativeHandle, useRef, useState } from 'react';
import { Map, Marker, ScaleControl, setWorkerUrl } from 'maplibre-gl';
import type { GeoJSONSource } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps } from './MapCanvas.types';

export default function MapCanvas({ref,style,userLocation = null,searchedLocation = null,zoomLevel = PLACES[0].zoom,dangerousZonesData,onReady,onError,onMove,onPress,onCoordinateSelect,}: MapCanvasProps) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<Map | null>(null);
  const locationMarker = useRef<Marker | null>(null);
  const locationElement = useRef<HTMLDivElement | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);
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
      if (!instance.getSource('dangerous-zones-source')) {
        instance.addSource('dangerous-zones-source', {
          type: 'geojson',
          data: dangerousZonesData || { type: 'FeatureCollection', features: [] }
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
    instance.on('dblclick', event => {
      event.preventDefault();
      onCoordinateSelect?.([event.lngLat.lng, event.lngLat.lat]);
    });
    const observer = new ResizeObserver(() => instance.resize());
    observer.observe(container.current);
return () => {
      observer.disconnect();
      locationMarker.current?.remove();
      locationMarker.current = null;
      locationElement.current = null;
      instance.remove();
      map.current = null;
      setMapLoaded(false);
    };
  }, [style]);

  useEffect(() => {
    if (!mapLoaded || !map.current || !dangerousZonesData) return;

    const source = map.current.getSource('dangerous-zones-source') as
      | GeoJSONSource
      | undefined;

    source?.setData(dangerousZonesData);
  }, [dangerousZonesData, mapLoaded]);

  useEffect(() => {
    if (!userLocation) {
      locationMarker.current?.remove();
      locationMarker.current = null;
      locationElement.current = null;
      return;
    }

    if (!map.current) return;

    if (!locationMarker.current) {
      const element = document.createElement('div');
      const dot = document.createElement('div');

      Object.assign(element.style, {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: '50%',
        backgroundColor: 'rgba(0, 101, 235, 0.18)',
        pointerEvents: 'none',
      });
      Object.assign(dot.style, {
        width: '14px',
        height: '14px',
        borderRadius: '50%',
        backgroundColor: '#0065EB',
        border: '3px solid #FFFFFF',
        boxSizing: 'border-box',
        boxShadow: '0 1px 4px rgba(0, 0, 0, 0.45)',
      });

      element.appendChild(dot);
      locationMarker.current = new Marker({ element, anchor: 'center' })
        .setLngLat(userLocation.coordinates)
        .addTo(map.current);
      locationElement.current = element;
    }

    const marker = locationMarker.current;
    const element = locationElement.current;
    if (!marker || !element) return;

    const metersPerPixel =
      156543.03392 *
      Math.cos(userLocation.coordinates[1] * Math.PI / 180) /
      2 ** zoomLevel;
    const radius = Math.max(10, Math.min(80, userLocation.accuracy / metersPerPixel));

    Object.assign(element.style, {
      width: `${radius * 2}px`,
      height: `${radius * 2}px`,
    });
    marker.setLngLat(userLocation.coordinates);
  }, [userLocation, zoomLevel]);
  useEffect(() => {
    if (!map.current || !searchedLocation) return;
    const marker = new Marker({ color: '#0065EB' }).setLngLat(searchedLocation).addTo(map.current);
    return () => { marker.remove(); };
  }, [searchedLocation]);
  return <div ref={container} aria-label="Map of Romania" style={{ position: 'absolute', inset: 0 }} />;
}
