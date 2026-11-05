import { useImperativeHandle, useRef } from 'react';
import {Camera,GeoJSONSource,Layer,Map,type CameraRef,type MapRef,} from '@maplibre/maplibre-react-native';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps } from './MapCanvas.types';

export default function MapCanvas({ ref,style,userLocation = null,searchedLocation = null,zoomLevel = PLACES[0].zoom,dangerousZonesData,onReady,onError,onMove,onPress,onCoordinateSelect,}: MapCanvasProps) {
  const camera = useRef<CameraRef>(null);
  const nativeMap = useRef<MapRef>(null);
  const currentZoom = useRef(PLACES[0].zoom);
  const center = useRef(PLACES[0].center);
  const lastPress = useRef<{ time: number; point: [number, number] } | null>(null);
  useImperativeHandle(ref, () => ({
    fitCoverage: () => camera.current?.fitBounds([...ROMANIA_BOUNDS[0], ...ROMANIA_BOUNDS[1]], { padding: { top: 24, right: 24, bottom: 24, left: 24 }, duration: 900, bearing: 0, pitch: 0 }),
    goTo: position => camera.current?.flyTo({ ...position, bearing: 0, pitch: 0, duration: 900 }),
    zoomBy: delta => camera.current?.zoomTo(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, currentZoom.current + delta)), { duration: 250 }),
    resetNorth: () => camera.current?.easeTo({ center: center.current, bearing: 0, pitch: 0, duration: 250 }),
  }), []);
const accuracyRadius = userLocation
    ? Math.max(
        10,
        Math.min(
          80,
          userLocation.accuracy /
            (156543.03392 *
              Math.cos(userLocation.coordinates[1] * Math.PI / 180) /
              2 ** zoomLevel),
        ),
      )
    : 0;

  return (
    <Map
      ref={nativeMap}
      mapStyle={style}
      style={{ flex: 1 }}
      touchPitch={false}
      doubleTapZoom={false}
      attribution
      logo={false}
      tintColor="#0065EB"
      attributionPosition={{ bottom: 8, right: 8 }}
      onDidFinishLoadingMap={onReady}
      onDidFailLoadingMap={onError}
      onRegionDidChange={event => {
        currentZoom.current = event.nativeEvent.zoom;
        center.current = event.nativeEvent.center;
        onMove({
          center: event.nativeEvent.center,
          zoom: event.nativeEvent.zoom,
        });
      }}
      onPress={async event => {
        const { lngLat, point } = event.nativeEvent;
        const now = Date.now();
        const previousPress = lastPress.current;
        if (previousPress && now - previousPress.time < 350
          && Math.hypot(point[0] - previousPress.point[0], point[1] - previousPress.point[1]) < 30) {
          lastPress.current = null;
          onCoordinateSelect?.(lngLat);
        } else {
          lastPress.current = { time: now, point };
        }
        if (!onPress) return;

        try {
          const [x, y] = event.nativeEvent.point;
          const features = await nativeMap.current?.queryRenderedFeatures(
            [x, y],
            { layers: ['dangerous-zones-layer'] },
          );
          onPress(features?.[0]?.properties ?? null);
        } catch {
          onPress(null);
        }
      }}
    >
      <Camera
        ref={camera}
        initialViewState={{
          bounds: [...ROMANIA_BOUNDS[0], ...ROMANIA_BOUNDS[1]],
          padding: { top: 24, right: 24, bottom: 24, left: 24 },
        }}
        minZoom={MIN_ZOOM}
        maxZoom={MAX_ZOOM}
      />

      {dangerousZonesData && (
        <GeoJSONSource
          id="dangerous-zones-source"
          data={dangerousZonesData}
        >
          <Layer
            id="dangerous-zones-layer"
            type="fill"
            style={{
              fillColor: [
                'step',
                ['get', 'intensity'],
                '#FFF9C4',
                3, '#FFEB3B',
                5, '#FFC107',
                10, '#FF9800',
                15, '#D93838',
              ],
              fillOpacity: 0.45,
            }}
          />
          <Layer
            id="dangerous-zones-borders"
            type="line"
            style={{
              lineColor: '#d93838',
              lineWidth: 1,
              lineOpacity: 0.6,
            }}
          />
        </GeoJSONSource>
      )}

      {userLocation && (
        <GeoJSONSource
          id="user-location"
          data={{
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: userLocation.coordinates,
            },
            properties: { accuracy: userLocation.accuracy },
          }}
        >
          <Layer
            id="user-location-accuracy"
            type="circle"
            style={{
              circleRadius: accuracyRadius,
              circleColor: '#0065EB',
              circleOpacity: 0.18,
            }}
          />
          <Layer
            id="user-location-dot"
            type="circle"
            style={{
              circleRadius: 8,
              circleColor: '#0065EB',
              circleStrokeColor: '#FFFFFF',
              circleStrokeWidth: 3,
            }}
          />
        </GeoJSONSource>
      )}
      {searchedLocation && (
        <GeoJSONSource
          id="searched-location"
          data={{ type: 'Feature', geometry: { type: 'Point', coordinates: searchedLocation }, properties: {} }}
        >
          <Layer id="searched-location-marker" type="circle" style={{
            circleRadius: 9,
            circleColor: '#0065EB',
            circleStrokeColor: '#FFFFFF',
            circleStrokeWidth: 3,
          }} />
        </GeoJSONSource>
      )}
    </Map>
  );
}
