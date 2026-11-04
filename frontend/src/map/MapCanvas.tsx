import { useImperativeHandle, useRef } from 'react';
import { Camera, Map, type CameraRef } from '@maplibre/maplibre-react-native';
// @ts-ignore
import { ShapeSource, FillLayer, LineLayer, CircleLayer } from '@maplibre/maplibre-react-native';
import { MAX_ZOOM, MIN_ZOOM, PLACES, ROMANIA_BOUNDS } from './config';
import type { MapCanvasProps } from './MapCanvas.types';

export default function MapCanvas({ ref, style, dangerousZonesData, routeData, routePointsData, onReady, onError, onMove, onPress }: MapCanvasProps) {
  const camera = useRef<CameraRef>(null);
  const mapRef = useRef<any>(null);
  const zoom = useRef(PLACES[0].zoom);
  const center = useRef(PLACES[0].center);
  useImperativeHandle(ref, () => ({
    fitCoverage: () => camera.current?.fitBounds([...ROMANIA_BOUNDS[0], ...ROMANIA_BOUNDS[1]], { padding: { top: 24, right: 24, bottom: 24, left: 24 }, duration: 900, bearing: 0, pitch: 0 }),
    goTo: position => camera.current?.flyTo({ ...position, bearing: 0, pitch: 0, duration: 900 }),
    zoomBy: delta => camera.current?.zoomTo(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom.current + delta)), { duration: 250 }),
    resetNorth: () => camera.current?.easeTo({ center: center.current, bearing: 0, pitch: 0, duration: 250 }),
  }), []);

  return (
      <Map ref={mapRef} mapStyle={style} style={{ flex: 1 }} touchPitch={false} attribution logo={false}
           tintColor="#0065EB" attributionPosition={{ bottom: 8, right: 8 }}
           onDidFinishLoadingMap={onReady} onDidFailLoadingMap={onError}
           onRegionDidChange={event => {
             zoom.current = event.nativeEvent.zoom;
             center.current = event.nativeEvent.center;
             onMove({ center: event.nativeEvent.center, zoom: event.nativeEvent.zoom });
           }}
           onPress={async (event) => {
               // @ts-ignore
               const coords: [number, number] | undefined = event?.geometry?.coordinates;
             if (!onPress || !mapRef.current) {
               if (onPress) onPress(null, coords);
               return;
             }
             try {
               const screenPoint: [number, number] = [event.nativeEvent.point[0], event.nativeEvent.point[1]];
               const featureCollection = await mapRef.current.queryRenderedFeaturesAtPoint(
                   screenPoint,
                   undefined,
                   ['dangerous-zones-layer']
               );

               if (featureCollection && featureCollection.features.length > 0) {
                 onPress(featureCollection.features[0].properties, coords);
               } else {
                 onPress(null, coords);
               }
             }
             catch (err) {
               console.log("Error on click:", err);
               if (onPress) onPress(null);
             }
           }}
      >
          <Camera ref={camera} initialViewState={{ bounds: [...ROMANIA_BOUNDS[0], ...ROMANIA_BOUNDS[1]], padding: { top: 24, right: 24, bottom: 24, left: 24 } }} minZoom={MIN_ZOOM} maxZoom={MAX_ZOOM} />
        {dangerousZonesData && (
            <ShapeSource id="dangerous-zones-source" shape={dangerousZonesData}>
              <FillLayer
                  id="dangerous-zones-layer"
                  style={{
                    fillColor: [
                      'step',
                      ['get', 'intensity'],
                      '#FFF9C4', 3, '#FFEB3B', 5, '#FFC107', 10, '#FF9800', 15, '#D93838'
                    ],
                    fillOpacity: 0.45
                  }}
              />
              <LineLayer
                  id="dangerous-zones-borders"
                  style={{
                    lineColor: '#d93838',
                    lineWidth: 1,
                    lineOpacity: 0.6
                  }}
              />
            </ShapeSource>
        )}
          {routeData && (
              <ShapeSource id="route-source" shape={routeData}>
                  <LineLayer id="route-layer" style={{ lineColor: '#9b59b6', lineWidth: 5, lineCap: 'round', lineJoin: 'round' }} />
              </ShapeSource>
          )}
          {routePointsData && (
              <ShapeSource id="route-points-source" shape={routePointsData}>
                  <CircleLayer id="route-points-layer" style={{
                      circleColor: ['match', ['get', 'type'], 'start', '#0065EB', 'end', '#d93838', '#000'],
                      circleRadius: 7,
                      circleStrokeWidth: 2,
                      circleStrokeColor: '#ffffff'
                  }} />
              </ShapeSource>
          )}
      </Map>
  );
}