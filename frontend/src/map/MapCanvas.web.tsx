import { useEffect, useEffectEvent, useImperativeHandle, useRef, useState } from 'react';
import { Map, Marker, ScaleControl, setWorkerUrl } from 'maplibre-gl';
import type { GeoJSONSource } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import './mapCanvas.web.css';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps, ScreenPoint } from './MapCanvas.types';
import { selectMapHit } from './poi';

const EMPTY_FEATURE_COLLECTION = {
  type: 'FeatureCollection' as const,
  features: [],
};

function createEventPinImageData(): ImageData | { width: number; height: number; data: Uint8ClampedArray } {
  const width = 56;
  const height = 72;
  if (typeof document !== 'undefined') {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      const cx = 28;
      const cy = 26;
      const r = 20;
      const tipY = 67;
      const tangAngle = Math.asin(r / (tipY - cy));

      ctx.beginPath();
      ctx.arc(cx, cy, r, Math.PI - tangAngle, tangAngle, false);
      ctx.lineTo(cx, tipY);
      ctx.closePath();

      ctx.fillStyle = '#8FD694';
      ctx.fill();
      ctx.lineWidth = 3.5;
      ctx.strokeStyle = '#4C9F54';
      ctx.stroke();

      ctx.beginPath();
      ctx.arc(cx, cy, 8, 0, Math.PI * 2);
      ctx.fillStyle = '#FFFFFF';
      ctx.fill();

      return ctx.getImageData(0, 0, width, height);
    }
  }
  return {
    width,
    height,
    data: new Uint8ClampedArray(width * height * 4),
  };
}

export default function MapCanvas({
  ref,
  style,
  userLocation = null,
  searchedLocation = null,
  zoomLevel = PLACES[0].zoom,
  dangerousZonesData,
  routeData,
  routePointsData,
  eventPinsData = null,
  selectedEventPinId = null,
  selectedEventPinCoordinates = null,
  onEventPinPress,
  onSelectedPinScreenPoint,
  onReady,
  onError,
  onMove,
  onPoiSelect,
  onPress,
  onCoordinateSelect,
}: MapCanvasProps) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<Map | null>(null);
  const locationMarker = useRef<Marker | null>(null);
  const locationElement = useRef<HTMLDivElement | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);

  const ready = useEffectEvent(onReady);
  const failed = useEffectEvent(onError);
  const moved = useEffectEvent(onMove);
  const selected = useEffectEvent(onPoiSelect);
  const coordinateSelected = useEffectEvent((coordinates: [number, number]) =>
    onCoordinateSelect?.(coordinates),
  );
  const pressed = useEffectEvent(
    (zone: Parameters<NonNullable<MapCanvasProps['onPress']>>[0], coords?: [number, number]) => {
      onPress?.(zone, coords);
    },
  );
  const currentZones = useEffectEvent(
    () => dangerousZonesData ?? EMPTY_FEATURE_COLLECTION,
  );
  const currentRoute = useEffectEvent(() => routeData ?? EMPTY_FEATURE_COLLECTION);
  const currentRoutePoints = useEffectEvent(
    () => routePointsData ?? EMPTY_FEATURE_COLLECTION,
  );
  const currentEventPins = useEffectEvent(
    () => eventPinsData ?? EMPTY_FEATURE_COLLECTION,
  );
  const pinPressed = useEffectEvent((pinId: string) => {
    onEventPinPress?.(pinId);
  });
  const hasPinPressHandler = useEffectEvent(() => Boolean(onEventPinPress));
  const reportPinScreenPoint = useEffectEvent((point: ScreenPoint | null) => {
    onSelectedPinScreenPoint?.(point);
  });
  const projectSelectedPin = useEffectEvent(() => {
    if (!onSelectedPinScreenPoint) {
      return;
    }
    const instance = map.current;
    if (!instance || !selectedEventPinCoordinates) {
      onSelectedPinScreenPoint(null);
      return;
    }
    const projected = instance.project(selectedEventPinCoordinates);
    if (Number.isFinite(projected.x) && Number.isFinite(projected.y)) {
      onSelectedPinScreenPoint({ x: projected.x, y: projected.y });
    } else {
      onSelectedPinScreenPoint(null);
    }
  });

  useImperativeHandle(
    ref,
    () => ({
      fitCoverage: () => {
        map.current?.fitBounds(ROMANIA_BOUNDS, {
          padding: 24,
          bearing: 0,
          pitch: 0,
          duration: 900,
        });
      },
      goTo: position => {
        map.current?.flyTo({ ...position, bearing: 0, pitch: 0, duration: 900 });
      },
      zoomBy: delta => {
        if (map.current) {
          map.current.zoomTo(
            Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, map.current.getZoom() + delta)),
          );
        }
      },
      resetNorth: () => {
        map.current?.easeTo({ bearing: 0, pitch: 0 });
      },
    }),
    [],
  );

  useEffect(() => {
    if (!container.current) return;
    let instance: Map;
    try {
      setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');
      instance = new Map({
        container: container.current,
        style,
        center: PLACES[0].center,
        bounds: ROMANIA_BOUNDS,
        fitBoundsOptions: { padding: 24 },
        minZoom: MIN_ZOOM,
        maxZoom: MAX_ZOOM,
        maxPitch: 0,
        attributionControl: { compact: true },
      });
    } catch {
      failed();
      return;
    }
    map.current = instance;
    instance.addControl(new ScaleControl({ unit: 'metric' }), 'bottom-left');
    instance.on('load', () => {
      if (!instance.hasImage('event-pin')) {
        instance.addImage('event-pin', createEventPinImageData(), { pixelRatio: 2 });
      }

      if (!instance.getSource('dangerous-zones-source')) {
        instance.addSource('dangerous-zones-source', {
          type: 'geojson',
          data: currentZones() as any,
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
              '#FFF9C4',
              3,
              '#FFEB3B',
              5,
              '#FFC107',
              10,
              '#FF9800',
              15,
              '#D93838',
            ],
            'fill-opacity': 0.45,
          },
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
            'line-opacity': 0.6,
          },
        });
      }
      if (!instance.getSource('route-source')) {
        instance.addSource('route-source', {
          type: 'geojson',
          data: currentRoute() as any,
        });
      }
      if (!instance.getLayer('route-layer')) {
        instance.addLayer({
          id: 'route-layer',
          type: 'line',
          source: 'route-source',
          paint: { 'line-color': '#9b59b6', 'line-width': 5 },
        });
      }
      if (!instance.getSource('route-points-source')) {
        instance.addSource('route-points-source', {
          type: 'geojson',
          data: currentRoutePoints() as any,
        });
      }
      if (!instance.getLayer('route-points-layer')) {
        instance.addLayer({
          id: 'route-points-layer',
          type: 'circle',
          source: 'route-points-source',
          layout: {
            'circle-sort-key': ['match', ['get', 'type'], 'stop', 2, 1],
          },
          paint: {
            'circle-color': [
              'match',
              ['get', 'type'],
              'start',
              '#0065EB',
              'stop',
              '#9157a2',
              'end',
              '#d93838',
              '#000',
            ],
            'circle-radius': ['match', ['get', 'type'], 'stop', 4, 6],
            'circle-stroke-width': 2,
            'circle-stroke-color': '#fff',
          },
        });
      }
      if (!instance.getSource('event-pins-source')) {
        instance.addSource('event-pins-source', {
          type: 'geojson',
          data: currentEventPins() as any,
        });
      }
      if (!instance.getLayer('event-pins-layer')) {
        instance.addLayer({
          id: 'event-pins-layer',
          type: 'symbol',
          source: 'event-pins-source',
          layout: {
            'icon-image': 'event-pin',
            'icon-anchor': 'bottom',
            'icon-size': 0.92,
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
          },
        });
      }

      instance.on('mouseenter', 'event-pins-layer', () => {
        if (hasPinPressHandler()) {
          instance.getCanvas().style.cursor = 'pointer';
        }
      });
      instance.on('mouseleave', 'event-pins-layer', () => {
        instance.getCanvas().style.cursor = '';
      });

      ready();
      setMapLoaded(true);
      projectSelectedPin();
    });
    instance.on('error', () => failed());
    instance.on('idle', () => {
      if (instance.areTilesLoaded()) ready();
    });
    instance.on('move', () => {
      projectSelectedPin();
    });
    instance.on('moveend', () => {
      const center = instance.getCenter();
      moved({ center: [center.lng, center.lat], zoom: instance.getZoom() });
      projectSelectedPin();
    });
    instance.on('click', event => {
      const coords: [number, number] = [event.lngLat.lng, event.lngLat.lat];
      if (hasPinPressHandler() && instance.getLayer('event-pins-layer')) {
        const pinFeatures = instance.queryRenderedFeatures(
          [[event.point.x - 8, event.point.y - 8], [event.point.x + 8, event.point.y + 8]],
          { layers: ['event-pins-layer'] },
        );
        const hitPinId = pinFeatures[0]?.properties?.pinId;
        if (typeof hitPinId === 'string' && hitPinId.length > 0) {
          selected(null);
          pinPressed(hitPinId);
          return;
        }
      }

      const layers = ['poi-dot', 'poi-label'].filter(id => instance.getLayer(id));
      const poiFeatures = layers.length
        ? instance.queryRenderedFeatures(
            [[event.point.x - 12, event.point.y - 12], [event.point.x + 12, event.point.y + 12]],
            { layers },
          )
        : [];
      const zoneFeatures = instance.getLayer('dangerous-zones-layer')
        ? instance.queryRenderedFeatures(event.point, { layers: ['dangerous-zones-layer'] })
        : [];
      const hit = selectMapHit(poiFeatures, zoneFeatures);
      selected(hit.poi);
      pressed(hit.zone, coords);
    });
    instance.on('dblclick', event => {
      event.preventDefault();
      coordinateSelected([event.lngLat.lng, event.lngLat.lat]);
    });
    const observer = new ResizeObserver(() => {
      instance.resize();
      projectSelectedPin();
    });
    observer.observe(container.current);
    return () => {
      observer.disconnect();
      locationMarker.current?.remove();
      locationMarker.current = null;
      locationElement.current = null;
      reportPinScreenPoint(null);
      instance.remove();
      map.current = null;
      setMapLoaded(false);
    };
  }, [style]);

  useEffect(() => {
    if (mapLoaded && map.current) {
      const updateSrc = (id: string, data: any) => {
        const src = map.current?.getSource(id) as GeoJSONSource | undefined;
        if (src && data) src.setData(data);
      };
      updateSrc('dangerous-zones-source', dangerousZonesData ?? EMPTY_FEATURE_COLLECTION);
      updateSrc('route-source', routeData || EMPTY_FEATURE_COLLECTION);
      updateSrc('route-points-source', routePointsData || EMPTY_FEATURE_COLLECTION);
      updateSrc('event-pins-source', eventPinsData || EMPTY_FEATURE_COLLECTION);
    }
  }, [dangerousZonesData, routeData, routePointsData, eventPinsData, mapLoaded]);

  useEffect(() => {
    if (!mapLoaded || !map.current || !map.current.getLayer('event-pins-layer')) {
      return;
    }
    map.current.setLayoutProperty(
      'event-pins-layer',
      'icon-size',
      selectedEventPinId
        ? ['case', ['==', ['get', 'pinId'], selectedEventPinId], 1.12, 0.92]
        : 0.92,
    );
  }, [selectedEventPinId, mapLoaded]);

  useEffect(() => {
    if (!mapLoaded || !map.current) {
      return;
    }
    if (!selectedEventPinCoordinates) {
      reportPinScreenPoint(null);
      return;
    }
    const projected = map.current.project(selectedEventPinCoordinates);
    if (Number.isFinite(projected.x) && Number.isFinite(projected.y)) {
      reportPinScreenPoint({ x: projected.x, y: projected.y });
    } else {
      reportPinScreenPoint(null);
    }
  }, [selectedEventPinCoordinates, mapLoaded]);

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
      (156543.03392 * Math.cos((userLocation.coordinates[1] * Math.PI) / 180)) /
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

    const marker = new Marker({ color: '#0065EB' })
      .setLngLat(searchedLocation)
      .addTo(map.current);

    return () => {
      marker.remove();
    };
  }, [searchedLocation]);

  return (
    <div
      ref={container}
      className="walkaround-map"
      aria-label="Map of Romania"
      style={{ position: 'absolute', inset: 0 }}
    />
  );
}
