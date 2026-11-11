import { useCallback, useEffect, useRef, useState, useMemo } from 'react';
import { ActivityIndicator, Animated, PanResponder, Pressable, ScrollView, StyleSheet, Text, TextInput, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { useSession } from '../login/components/SessionProvider';
import { CloudSun, CalendarDays, Compass, LocateFixed, Menu, Minus, Plus, RotateCcw, Route, UserRound, MapPin, Search, X, Map as MapIcon, type LucideIcon } from 'lucide-react-native';
import type { StyleSpecification } from 'maplibre-gl';
import MapCanvas from './MapCanvas';
import type { MapHandle, ScreenPoint } from './MapCanvas.types';
import { isInCoverage, MAP_STYLE_URL, PLACES } from './config';
import { resolveStyleResources, styleMap } from './style';
import type { MapPoi } from './poi';
import { useUserLocalization } from './userLocalization';
import EventMapPopup from '../events/components/EventMapPopup';
import { useMapEvents } from '../hooks/useMapEvents';
import DangerousZoneButton from '../dangerous-zone/components/DangerousZoneButton';
import DangerousZoneModal from '../dangerous-zone/components/DangerousZoneModal';
import { cellToBoundary } from 'h3-js';
import { fetchDangerousZonesHeatmap } from '../dangerous-zone/api/dangerousZoneApi';
import { environment } from '../config/environment';
import { authenticatedRequest } from '../login/api/session';
import { jsonPost } from '../login/api/http';
import SavedRoutesModal from '../saved-routes/components/SavedRoutesModal';

type LatLng = { lat: number; lng: number };
type RouteStop = { id: string; point: LatLng };

type PoiSearchResult = {
  name: string;
  detail: string;
  coordinates: [longitude: number, latitude: number];
};

async function lookupPlaces(query: string, camera: { center: [number, number]; zoom: number }, signal: AbortSignal) {
  const params = new URLSearchParams({
    q: query,
    limit: '5',
    lat: String(camera.center[1]),
    lon: String(camera.center[0]),
    zoom: String(Math.round(camera.zoom)),
  });
  const response = await fetch(`${environment.apiBaseUrl}/api/places/search?${params.toString()}`, { signal });
  if (!response.ok) throw new Error('Place search unavailable');
  const result = await response.json();
  return (result as { name: string; detail: string; latitude: number; longitude: number }[])
    .filter(place => Number.isFinite(place.latitude) && Number.isFinite(place.longitude))
    .filter(place => isInCoverage(place.longitude, place.latitude))
    .map(place => ({
      name: place.name || query,
      detail: place.detail || '',
      coordinates: [place.longitude, place.latitude] as [number, number],
    }));
}


function Tool({ label, icon: Icon, onPress, disabled = false }: { label: string; icon: LucideIcon; onPress(): void; disabled?: boolean }) {
  const [hovered, setHovered] = useState(false);
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled}
                    onHoverIn={() => setHovered(true)} onHoverOut={() => setHovered(false)}
                    onPress={onPress} style={({ pressed }) => [styles.tool, (disabled || pressed) && styles.dim]}>
    <Icon size={21} color={disabled ? '#9aa3ae' : '#0065EB'} />
    {hovered && <Text style={styles.tooltip}>{label}</Text>}
  </Pressable>;
}

function decodePolyline(str: string, precision = 6) {
  let index = 0, lat = 0, lng = 0, coordinates = [];
  let shift = 0, result = 0, byte = null;
  let latitude_change, longitude_change;
  const factor = Math.pow(10, precision);
  while (index < str.length) {
    byte = null; shift = 0; result = 0;
    do { byte = str.charCodeAt(index++) - 63; result |= (byte & 0x1f) << shift; shift += 5; } while (byte >= 0x20);
    latitude_change = ((result & 1) ? ~(result >> 1) : (result >> 1));
    shift = result = 0;
    do { byte = str.charCodeAt(index++) - 63; result |= (byte & 0x1f) << shift; shift += 5; } while (byte >= 0x20);
    longitude_change = ((result & 1) ? ~(result >> 1) : (result >> 1));
    lat += latitude_change; lng += longitude_change;
    coordinates.push([lng / factor, lat / factor]);
  }
  return coordinates;
}

function calculateBearing(startLat: number, startLng: number, destLat: number, destLng: number) {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const toDeg = (rad: number) => (rad * 180) / Math.PI;
  const startLatRad = toRad(startLat);
  const startLngRad = toRad(startLng);
  const destLatRad = toRad(destLat);
  const destLngRad = toRad(destLng);

  const y = Math.sin(destLngRad - startLngRad) * Math.cos(destLatRad);
  const x = Math.cos(startLatRad) * Math.sin(destLatRad) - Math.sin(startLatRad) * Math.cos(destLatRad) * Math.cos(destLngRad - startLngRad);

  const bearing = toDeg(Math.atan2(y, x));
  return (bearing + 360) % 360;
}

function calculateDistanceInMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3;
  const phi1 = lat1 * Math.PI / 180;
  const phi2 = lat2 * Math.PI / 180;
  const deltaphi = (lat2 - lat1) * Math.PI / 180;
  const deltalambda = (lon2 - lon1) * Math.PI / 180;

  const a = Math.sin(deltaphi/2) * Math.sin(deltaphi/2) +
      Math.cos(phi1) * Math.cos(phi2) *
      Math.sin(deltalambda/2) * Math.sin(deltalambda/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

  return R * c;
}

export default function MapScreen() {
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  const compactSearchLayout = windowWidth < 600;
  const [mobileSearchExpanded, setMobileSearchExpanded] = useState(false);
  const [mobileSearchHeight, setMobileSearchHeight] = useState(0);
  const [searchReveal] = useState(() => new Animated.Value(0));
  useEffect(() => {
    if (!compactSearchLayout || !mobileSearchExpanded) {
      searchReveal.setValue(0);
      return;
    }
    Animated.timing(searchReveal, {
      toValue: 1,
      duration: 220,
      useNativeDriver: true,
    }).start();
    return () => searchReveal.stopAnimation();
  }, [compactSearchLayout, mobileSearchExpanded, searchReveal]);
  const mobileSearchPanResponder = useMemo(() => PanResponder.create({
    onStartShouldSetPanResponder: () => false,
    onMoveShouldSetPanResponder: (_, gesture) => Math.abs(gesture.dy) > 6 && Math.abs(gesture.dy) > Math.abs(gesture.dx),
    onMoveShouldSetPanResponderCapture: (_, gesture) => Math.abs(gesture.dy) > 6 && Math.abs(gesture.dy) > Math.abs(gesture.dx),
    onPanResponderRelease: (_, gesture) => {
      if (gesture.dy < -12) setMobileSearchExpanded(true);
      else if (gesture.dy > 12) setMobileSearchExpanded(false);
    },
  }), []);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const map = useRef<MapHandle>(null);
  const { session } = useSession();
  const [style, setStyle] = useState<StyleSpecification | null>(null);
  const [camera, setCamera] = useState({ center: PLACES[0].center, zoom: PLACES[0].zoom });
  const [coordinateQuery, setCoordinateQuery] = useState('');
  const showPlanTripLink = coordinateQuery.trim().length > 0;
  const [searchedLocation, setSearchedLocation] = useState<[number, number] | null>(null);
  const [coordinateError, setCoordinateError] = useState<string | null>(null);
  const [poiResults, setPoiResults] = useState<PoiSearchResult[]>([]);
  const [searchingPoi, setSearchingPoi] = useState(false);
  const [poiSearchNonce, setPoiSearchNonce] = useState(0);
  const poiSearchRequest = useRef(0);
  const immediatePoiQuery = useRef('');
  const suppressPoiQuery = useRef('');
  const currentCamera = useRef(camera);
  useEffect(() => {
    currentCamera.current = camera;
  }, [camera]);
  const [loading, setLoading] = useState(true);
  const [mapReady, setMapReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [selectedPoi, setSelectedPoi] = useState<MapPoi | null>(null);
  const [selectedPinScreenPoint, setSelectedPinScreenPoint] = useState<ScreenPoint | null>(null);
  const [mapLayout, setMapLayout] = useState<{ width: number; height: number } | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  const mapEvents = useMapEvents({
    center: camera.center,
    signedIn: Boolean(session),
    mapReady,
  });

  const [routeMode, setRouteMode] = useState(false);
  const [routeStart, setRouteStart] = useState<{lat: number, lng: number} | null>(null);
  const [routeEnd, setRouteEnd] = useState<{lat: number, lng: number} | null>(null);
  const [activeRouteField, setActiveRouteField] = useState<string | null>(null);
  const [routeStops, setRouteStops] = useState<RouteStop[]>([]);
  const stopIdCounter = useRef(0);
  const [routeGeoJSON, setRouteGeoJSON] = useState<any>(null);
  const [routeInstructions, setRouteInstructions] = useState<any[]>([]);
  const [isNavigating, setIsNavigating] = useState(false);
  const [showSavedRoutes, setShowSavedRoutes] = useState(false);
  const [activeSavedRouteId, setActiveSavedRouteId] = useState<number | null>(null);
  const [showArrivalAlert, setShowArrivalAlert] = useState(false);

  const { userLocation, locating, error: locationError, locate } = useUserLocalization(map);
  const locateRef = useRef(locate);
  useEffect(() => {
    locateRef.current = locate;
  }, [locate]);

  useFocusEffect(useCallback(() => {
    if (mapReady) void locateRef.current();
  }, [mapReady]));

  const displayedError = error ?? locationError;

  function searchCoordinates() {
    const query = coordinateQuery.trim();
    const parts = query.split(',').map(value => value.trim());
    const values = parts.map(value => Number(value));
    const isCoordinatePair = parts.length === 2 && parts.every(value => value !== '')
      && values.every(value => Number.isFinite(value));
    setCoordinateError(null);
    setPoiResults([]);

    if (isCoordinatePair) {
      const [latitude, longitude] = values;
      if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
        setCoordinateError('Latitude must be between -90 and 90, and longitude between -180 and 180.');
        return;
      }
      if (!isInCoverage(longitude, latitude)) {
        setCoordinateError('That location is outside the downloaded map coverage (Romania).');
        return;
      }
      setSearchedLocation([longitude, latitude]);
      setSelectedZoneInfo(null);
      map.current?.goTo({ center: [longitude, latitude], zoom: 14 });
      return;
    }

    if (!query) {
      setCoordinateError('Enter coordinates or a place name to search.');
      return;
    }

    if (poiResults.length > 0) {
      selectPoiResult(poiResults[0]);
      return;
    }
    immediatePoiQuery.current = query;
    setPoiSearchNonce(value => value + 1);
  }

  async function handlePauseAndSaveRoute() {
    setIsNavigating(false);
    map.current?.resetNorth();
    if (!session || !routeStart || !routeEnd) return;
    try {
      const payload = {
        startLat: routeStart.lat,
        startLng: routeStart.lng,
        endLat: routeEnd.lat,
        endLng: routeEnd.lng
      };
      if (activeSavedRouteId)
      {
        await authenticatedRequest(`/api/v1/saved-routes/${activeSavedRouteId}`, {
          ...jsonPost(payload),
          method: 'PUT'
        });
        console.log('Route successfully updated!');
      }
      else
      {
        await authenticatedRequest('/api/v1/saved-routes', jsonPost(payload));
        console.log('New route successfully saved!');
      }

    } catch (err) {
      console.error('Error saving/updating route:', err);
    }
  }

  function handleRecenter() {
    if (!userLocation || !routeEnd) return;

    const userLat = userLocation.coordinates[1];
    const userLng = userLocation.coordinates[0];
    const bearing = calculateBearing(userLat, userLng, routeEnd.lat, routeEnd.lng);

    map.current?.startNavigation([userLng, userLat], bearing);
  }

  function handleResumeSavedRoute(destination: { lat: number; lng: number }, routeId: number)
  {
    setShowSavedRoutes(false);
    setRouteMode(true);
    setActiveSavedRouteId(routeId);
    if (userLocation)
    {
      setRouteStart({ lat: userLocation.coordinates[1], lng: userLocation.coordinates[0] });
      map.current?.goTo({ center: [userLocation.coordinates[0], userLocation.coordinates[1]], zoom: 15 });
    }
    else
    {
      setRouteStart({ lat: camera.center[1], lng: camera.center[0] });
    }
    setRouteEnd(destination);
    setActiveRouteField(null);
  }

  function handleMapCoordinateSelect([longitude, latitude]: [number, number]) {
    poiSearchRequest.current += 1;
    setSearchingPoi(false);
    setPoiResults([]);
    setSearchedLocation([longitude, latitude]);
    setCoordinateQuery(`${latitude.toFixed(6)}, ${longitude.toFixed(6)}`);
    setCoordinateError(null);
    setSelectedZoneInfo(null);
  }

  function selectPoiResult(result: PoiSearchResult) {
    suppressPoiQuery.current = result.name;
    handleMapCoordinateSelect(result.coordinates);
    setCoordinateQuery(result.name);
    map.current?.goTo({ center: result.coordinates, zoom: 15 });
  }

  useEffect(() => {
    const query = coordinateQuery.trim();
    const isPartialCoordinatePair = /^[-+]?\d*\.?\d*\s*,\s*[-+]?\d*\.?\d*$/.test(query);
    const requestId = ++poiSearchRequest.current;
    if (query.length < 2 || isPartialCoordinatePair || suppressPoiQuery.current === query) {
      if (suppressPoiQuery.current === query) suppressPoiQuery.current = '';
      const resetTimeout = setTimeout(() => {
        if (requestId !== poiSearchRequest.current) return;
        setPoiResults([]);
        setSearchingPoi(false);
      }, 0);
      return () => clearTimeout(resetTimeout);
    }

    const controller = new AbortController();
    const delay = immediatePoiQuery.current === query ? 0 : 300;
    if (delay === 0) immediatePoiQuery.current = '';
    const timeout = setTimeout(async () => {
      if (requestId !== poiSearchRequest.current) return;
      setCoordinateError(null);
      setPoiResults([]);
      setSearchingPoi(true);
      try {
        const matches = await lookupPlaces(query, currentCamera.current, controller.signal);
        if (requestId !== poiSearchRequest.current) return;
        setPoiResults(matches);
        if (matches.length === 0) setCoordinateError(`No places found for “${query}”.`);
      } catch {
        if (requestId === poiSearchRequest.current && !controller.signal.aborted) {
          setCoordinateError('Place search failed. Check your connection and try again.');
        }
      } finally {
        if (requestId === poiSearchRequest.current) setSearchingPoi(false);
      }
    }, delay);

    return () => { clearTimeout(timeout); controller.abort(); };
  }, [coordinateQuery, poiSearchNonce]);
  const [isSelectingLocation, setIsSelectingLocation] = useState(false);
  const [zoneModalVisible, setZoneModalVisible] = useState(false);
  const [reportLocation, setReportLocation] = useState<{lat: number, lng: number} | null>(null);
  const [selectedZoneInfo, setSelectedZoneInfo] = useState<{ categories: string[], intensity: number } | null>(null);
  const [dangerousZonesData, setDangerousZonesData] = useState<any>({
    type: 'FeatureCollection',
    features: []
  });

  const routePointsData = useMemo(() => {
    const features: any[] = [];
    if (routeStart) features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [routeStart.lng, routeStart.lat] }, properties: { type: 'start' } });
    routeStops.forEach(stop => features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [stop.point.lng, stop.point.lat] }, properties: { type: 'stop' } }));
    if (routeEnd) features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [routeEnd.lng, routeEnd.lat] }, properties: { type: 'end' } });
    return features.length > 0 ? { type: 'FeatureCollection' as const, features } : null;
  }, [routeStart, routeStops, routeEnd]);
  useEffect(() => {
    if (!style) return;
    async function loadZones() {
      try {
        const heatmapData = await fetchDangerousZonesHeatmap();
        const features = heatmapData.map(zone => {
          const boundary = cellToBoundary(zone.h3Index, true);
          return {
            type: 'Feature',
            geometry: { type: 'Polygon', coordinates: [boundary] },
            properties: { h3Index: zone.h3Index, intensity: zone.intensity, topCategories: zone.topCategories }
          };
        });
        setDangerousZonesData({
          type: 'FeatureCollection',
          features: features
        });
      }
      catch (e) {
        console.warn('Danger-zone overlays are unavailable; the map can still be used.', e);
      }
    }
    loadZones();
  }, [session, style]);

  useEffect(() => {
    const wsBaseUrl = process.env.EXPO_PUBLIC_WS_URL?.trim();
    if (!wsBaseUrl) {
      console.warn("EXPO_PUBLIC_WS_URL is not defined in .env");
      return;
    }
    const wsUrl = `${wsBaseUrl.replace(/\/+$/, '')}/ws/dangerous-zones`;
    const ws = new WebSocket(wsUrl);
    ws.onopen = () => {
      console.log('Connected to real-time service');
    };
    ws.onmessage = (event) => {
      try {
        const newZoneEvent = JSON.parse(event.data);
        setDangerousZonesData((prevData: any) => {
          const currentFeatures = prevData?.features ? [...prevData.features] : [];
          const existingFeatureIndex = currentFeatures.findIndex(
              (f: any) => f.properties.h3Index === newZoneEvent.h3Index
          );

          if (existingFeatureIndex !== -1) {
            const existingFeature = { ...currentFeatures[existingFeatureIndex] };
            existingFeature.properties = { ...existingFeature.properties };
            existingFeature.properties.intensity += 1;

            if (newZoneEvent.topCategories && newZoneEvent.topCategories.length > 0) {
              const newCategories = newZoneEvent.topCategories[0];
              if (!existingFeature.properties.topCategories.includes(newCategories)) {
                existingFeature.properties.topCategories.push(newCategories);
              }
            }
            currentFeatures[existingFeatureIndex] = existingFeature;
          } else {
            const boundary = cellToBoundary(newZoneEvent.h3Index, true);
            const newFeature = {
              type: 'Feature',
              geometry: { type: 'Polygon', coordinates: [boundary] },
              properties: {
                h3Index: newZoneEvent.h3Index,
                intensity: newZoneEvent.intensity || 1,
                topCategories: newZoneEvent.topCategories
              }
            };
            currentFeatures.push(newFeature);
          }
          return {
            type: 'FeatureCollection',
            features: currentFeatures
          };
        });
      } catch (e) {
        console.error("Error when processing realtime event", e);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket Error: ', error);
    };

    return () => {
      ws.close();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(new DOMException('Map request timed out', 'TimeoutError')), 15000);
    fetch(MAP_STYLE_URL, { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error('Style unavailable');
      const result = await response.json();
      if (result.version !== 8 || !result.sources || !Array.isArray(result.layers)) throw new Error('Invalid map style');
      if (!controller.signal.aborted) setStyle(styleMap(resolveStyleResources(result, response.url || MAP_STYLE_URL)));
    }).catch(() => {
      if (!controller.signal.aborted) { setError('Could not load the map. Check your connection and retry.'); setLoading(false); }
      else if (controller.signal.reason?.name === 'TimeoutError') { setError('Map request timed out. Please retry.'); setLoading(false); }
    }).finally(() => clearTimeout(timeout));
    return () => { clearTimeout(timeout); controller.abort(); };
  }, [attempt]);

  useEffect(() => {
    if (!style || !loading) return;
    const timeout = setTimeout(() => {
      setLoading(false);
      setError('The map is taking too long to load. Check your connection or retry.');
    }, 20000);
    return () => clearTimeout(timeout);
  }, [style, loading]);

  useEffect(() => {
    if (!routeStart || !routeEnd) return;
    const controller = new AbortController();
    const baseUrl = process.env.EXPO_PUBLIC_API_BASE_URL;

    fetch(`${baseUrl}/api/v1/routes/pedestrian`, {
      method: 'POST',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        startLat: routeStart.lat,
        startLng: routeStart.lng,
        stops: routeStops.map(s => ({ lat: s.point.lat, lng: s.point.lng })),
        endLat: routeEnd.lat,
        endLng: routeEnd.lng
      })
    })
      .then(res => res.json())
      .then(data => {
        if (data?.trip?.legs?.length) {
          let allCoords: number[][] = [];
          let allInstructions: any[] = [];

          data.trip.legs.forEach((leg: any) => {
            const legCoords = decodePolyline(leg.shape, 6);
            allCoords = allCoords.concat(legCoords);
            if (leg.maneuvers) {
              const legInstructions = leg.maneuvers.map((m: any) => ({
                instruction: m.instruction,
                distanceMeters: m.length * 1000,
                coordinate: legCoords[m.begin_shape_index],
                type: m.type
              }));
              allInstructions = allInstructions.concat(legInstructions);
            }
          });

          setRouteGeoJSON({ type: 'Feature', geometry: { type: 'LineString', coordinates: allCoords } });
          setRouteInstructions(allInstructions);
          console.log("Extracted Instructions for TTS:", allInstructions);
        } else {
          console.log('No route found in response');
          setRouteGeoJSON(null);
          setRouteInstructions([]);
        }
      })
      .catch(err => {
        if (err.name === 'AbortError') return;
        console.error('Failed to calculate route:', err);
        setRouteGeoJSON(null);
      });

    return () => controller.abort();
  }, [routeStart, routeEnd, routeStops]);

  useEffect(() => {
    if (isNavigating && userLocation && routeEnd) {
      const userLat = userLocation.coordinates[1];
      const userLng = userLocation.coordinates[0];

      const distanceToDestination = calculateDistanceInMeters(userLat, userLng, routeEnd.lat, routeEnd.lng);
      if (distanceToDestination <= 10)
      {
        /* eslint-disable react-hooks/set-state-in-effect */
        setIsNavigating(false);
        map.current?.resetNorth();
        setShowArrivalAlert(true);
        setTimeout(() => setShowArrivalAlert(false), 5000);
        /* eslint-enable react-hooks/set-state-in-effect */
        return;
      }
      let currentBearing = userLocation.heading;
      if (currentBearing === null || currentBearing === undefined) {
        const destLat = routeGeoJSON?.geometry?.coordinates?.[1]?.[1] || routeEnd.lat;
        const destLng = routeGeoJSON?.geometry?.coordinates?.[1]?.[0] || routeEnd.lng;
        currentBearing = calculateBearing(userLat, userLng, destLat, destLng);
      }
      map.current?.startNavigation([userLng, userLat], currentBearing);
    }
  }, [userLocation, isNavigating, routeEnd, routeGeoJSON]);

  function addStop() {
    const id = `stop-${++stopIdCounter.current}`;
    setRouteStops(stops => {
      const previous = stops.length > 0
        ? stops[stops.length - 1].point
        : (routeStart ?? { lat: camera.center[1], lng: camera.center[0] });
      return [...stops, { id, point: previous }];
    });
    setActiveRouteField(id); // următorul tap pe hartă mută noua oprire
  }

  function removeStop(id: string) {
    setRouteStops(stops => stops.filter(s => s.id !== id));
    setActiveRouteField(field => (field === id ? null : field));
  }

  function handleEventPinPress(pinId: string) {
    setSelectedPoi(null);
    setSelectedZoneInfo(null);
    mapEvents.selectPin(pinId);
  }

  function handlePoiSelect(poi: MapPoi | null) {
    setSelectedPoi(poi);
    if (poi) {
      mapEvents.clearSelection();
    }
  }

  function handleMapClick(zone: { intensity: number; categories: string[] } | null, coords?: [number, number]) {
    if (routeMode && activeRouteField && coords) {
      const point = { lng: coords[0], lat: coords[1] };
      if (activeRouteField === 'start') {
        setRouteStart(point);
        if (!routeEnd) setActiveRouteField('end');
        else setActiveRouteField(null);
      } else if (activeRouteField === 'end') {
        setRouteEnd(point);
        setActiveRouteField(null);
      } else {
        setRouteStops(stops => stops.map(s => (s.id === activeRouteField ? { ...s, point } : s)));
        setActiveRouteField(null);
      }
      return;
    }
    mapEvents.clearSelection();
    if (zone) {
      setSelectedZoneInfo({ intensity: zone.intensity, categories: zone.categories });
    } else {
      setSelectedZoneInfo(null);
    }
  }

  function retryMap() {
    setLoading(true);
    setMapReady(false);
    setError(null);
    setStyle(null);
    setSelectedPoi(null);
    setSelectedZoneInfo(null);
    mapEvents.clearSelection();
    setAttempt(value => value + 1);
  }

  function handleReportPress() {
    if (!session) {
      router.push('/login');
      return;
    }
    setSelectedPoi(null);
    setSelectedZoneInfo(null);
    mapEvents.clearSelection();
    setIsSelectingLocation(true);
  }

  function handleConfirmLocation() {
    setReportLocation({ lat: camera.center[1], lng: camera.center[0] });
    setIsSelectingLocation(false);
    setZoneModalVisible(true);
  }

  return <View style={styles.screen}>
    <View
      style={styles.map}
      onLayout={event => {
        const { width, height } = event.nativeEvent.layout;
        setMapLayout(current =>
          current && current.width === width && current.height === height
            ? current
            : { width, height },
        );
      }}
    >
{style && (
  <MapCanvas
    key={attempt}
    ref={map}
    style={style}
    userLocation={userLocation}
    searchedLocation={searchedLocation}
    zoomLevel={camera.zoom}
    dangerousZonesData={dangerousZonesData}
    routeData={routeGeoJSON}
    routePointsData={routePointsData}
    eventPinsData={mapEvents.geoJSON}
    selectedEventPinId={mapEvents.selectedPinId}
    selectedEventPinCoordinates={mapEvents.selectedPin?.coordinates ?? null}
    onEventPinPress={routeMode && activeRouteField ? undefined : handleEventPinPress}
    onSelectedPinScreenPoint={setSelectedPinScreenPoint}
    onMove={setCamera}
    onCoordinateSelect={handleMapCoordinateSelect}
    onPoiSelect={handlePoiSelect}
    onReady={() => {
      setLoading(false);
      setMapReady(true);
    }}
    onError={() => {
      setLoading(false);
      setMapReady(false);
      setError('Some map resources could not load. Check your connection or retry.');
    }}
    onPress={handleMapClick}
  />
)}
      {mapEvents.selectedEvent && selectedPinScreenPoint && !routeMode && !isSelectingLocation && (
        <EventMapPopup
          event={mapEvents.selectedEvent}
          currentIndex={mapEvents.selectedEventIndex}
          totalCount={mapEvents.selectedPinCount}
          screenPoint={selectedPinScreenPoint}
          containerWidth={mapLayout?.width ?? windowWidth}
          containerHeight={mapLayout?.height ?? windowHeight}
          topInset={Math.max(insets.top, 12)}
          onClose={mapEvents.clearSelection}
          onPrevious={mapEvents.previousEvent}
          onNext={mapEvents.nextEvent}
          onSeeMore={selected => {
            mapEvents.clearSelection();
            router.push({
              pathname: '/events/details',
              params: { localityKey: selected.localityKey, eventKey: selected.key },
            });
          }}
        />
      )}
      {loading && <View pointerEvents="none" style={styles.loading}><ActivityIndicator color="#0065EB" accessibilityLabel="Loading map" /></View>}

      {isSelectingLocation && (
          <View style={styles.centerPin} pointerEvents="none">
            <MapPin size={42} color="#d93838" />
          </View>
      )}

      {routeMode && !isNavigating && (
          <View style={[styles.routeOverlay, { top: Math.max(insets.top, 12) + 64 }]}>
            <Text style={styles.routeOverlayTitle}>Plan your route</Text>

            <Pressable onPress={() => setActiveRouteField('start')} style={[styles.routeInput, activeRouteField === 'start' && styles.routeInputActive]}>
              <View style={[styles.routeDot, { backgroundColor: '#0065EB' }]} />
              <Text style={styles.routeInputText}>{routeStart ? `Start: ${routeStart.lat.toFixed(4)}, ${routeStart.lng.toFixed(4)}` : 'Tap map to select start point'}</Text>
            </Pressable>

            {routeStops.map((stop, index) => (
              <View key={stop.id} style={styles.routeStopRow}>
                <Pressable
                  onPress={() => setActiveRouteField(stop.id)}
                  style={[styles.routeInput, styles.routeStopInput, activeRouteField === stop.id && styles.routeInputActive]}>
                  <View style={[styles.routeDot, { backgroundColor: '#9157a2' }]} />
                  <Text style={styles.routeInputText}>
                    {`Stop ${index + 1}: ${stop.point.lat.toFixed(4)}, ${stop.point.lng.toFixed(4)}`}
                  </Text>
                </Pressable>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`Remove stop ${index + 1}`}
                  onPress={() => removeStop(stop.id)}
                  style={styles.removeStopBtn}>
                  <X size={18} color="#758091" />
                </Pressable>
              </View>
            ))}

            <Pressable accessibilityRole="button" accessibilityLabel="Add stop" onPress={addStop} style={styles.addStopBtn}>
              <Plus size={16} color="#0065EB" />
              <Text style={styles.addStopText}>Add stop</Text>
            </Pressable>

            <Pressable onPress={() => setActiveRouteField('end')} style={[styles.routeInput, activeRouteField === 'end' && styles.routeInputActive]}>
              <View style={[styles.routeDot, { backgroundColor: '#d93838' }]} />
              <Text style={styles.routeInputText}>{routeEnd ? `Dest: ${routeEnd.lat.toFixed(4)}, ${routeEnd.lng.toFixed(4)}` : 'Tap map to select destination'}</Text>
            </Pressable>

            <View style={styles.routeActions}>
              <Pressable style={styles.routeCloseBtn} onPress={() => { setRouteMode(false); setActiveRouteField(null); setRouteStart(null); setRouteEnd(null); setRouteGeoJSON(null); setRouteInstructions([]); setIsNavigating(false); setActiveSavedRouteId(null);}}>
                <Text style={styles.routeCloseText}>Cancel</Text>
              </Pressable>

              {routeGeoJSON && (
                  <Pressable style={styles.routeStartBtn} onPress={() => {
                    if (!routeEnd) return;
                    let startPoint = routeStart;
                    if (userLocation) {
                      const userLat = userLocation.coordinates[1];
                      const userLng = userLocation.coordinates[0];
                      const isDifferent = !routeStart ||
                          Math.abs(userLat - routeStart.lat) > 0.0001 ||
                          Math.abs(userLng - routeStart.lng) > 0.0001;
                      if (isDifferent) {
                        startPoint = { lat: userLat, lng: userLng };
                        setRouteStart(startPoint);
                      }
                    }

                    if (startPoint)
                    {
                      const destLat = routeGeoJSON?.geometry?.coordinates?.[1]?.[1] || routeEnd.lat;
                      const destLng = routeGeoJSON?.geometry?.coordinates?.[1]?.[0] || routeEnd.lng;
                      const initialBearing = calculateBearing(startPoint.lat, startPoint.lng, destLat, destLng);

                      setIsNavigating(true);
                      map.current?.startNavigation([startPoint.lng, startPoint.lat], initialBearing);
                    }
                  }}>
                    <Text style={styles.routeStartText}>Start</Text>
                  </Pressable>
              )}
            </View>
          </View>
      )}

      {showArrivalAlert && (
          <View style={styles.arrivalAlertContainer}>
            <Text style={styles.arrivalAlertText}>You have arrived at your destination!</Text>
          </View>
      )}

      {isNavigating && (
          <View style={styles.navigatingOverlay} pointerEvents="box-none">
            <View style={styles.navigatingActionsRow}>
              <Pressable
                  accessibilityRole="button"
                  style={styles.recenterBtn}
                  onPress={handleRecenter}>
                <LocateFixed size={22} color="#ffffff" />
              </Pressable>

              <Pressable
                  accessibilityRole="button"
                  style={styles.pauseRouteBtn}
                  onPress={handlePauseAndSaveRoute}>
                <Text style={styles.pauseRouteText}>Pause Route</Text>
              </Pressable>
            </View>
          </View>
      )}

      <View style={[styles.mapMenu, { top: Math.max(insets.top, 12) + 4 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel={menuOpen ? 'Close features menu' : 'Open features menu'}
                   accessibilityState={{ expanded: menuOpen }} onPress={() => setMenuOpen(open => !open)} style={styles.menuButton}>
          {menuOpen ? <X size={22} color="#0065EB" /> : <Menu size={22} color="#0065EB" />}
        </Pressable>
        {menuOpen && <View style={styles.menuPanel}>
          <Pressable accessibilityRole="button" onPress={() => setMenuOpen(false)} style={styles.menuItem}>
            <Compass size={20} color="#0065EB" /><Text style={styles.menuItemText}>Explore map</Text>
          </Pressable>
          <Pressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.push('/trip-plan'); }} style={styles.menuItem}>
            <Route size={20} color="#0065EB" /><Text style={styles.menuItemText}>Plan a trip</Text>
          </Pressable>
          <Pressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.push('/recommendations'); }} style={styles.menuItem}>
            <CloudSun size={20} color="#0065EB" /><Text style={styles.menuItemText}>Weather activities</Text>
          </Pressable>
          <Pressable accessibilityRole="button" onPress={() => {
            setMenuOpen(false);
            mapEvents.clearSelection();
            setRouteMode(true);
            if (userLocation)
            {
              setRouteStart({ lat: userLocation.coordinates[1], lng: userLocation.coordinates[0] });
              map.current?.goTo({ center: [userLocation.coordinates[0], userLocation.coordinates[1]], zoom: 15 });
            }
            else
            {
              setRouteStart({ lat: camera.center[1], lng: camera.center[0] });
            }

            setActiveRouteField('end');
          }} style={styles.menuItem}>
            <MapIcon size={20} color="#0065EB" /><Text style={styles.menuItemText}>Route Search</Text>
          </Pressable>
          {session && (
              <Pressable accessibilityRole="button" onPress={() => {
                setMenuOpen(false);
                setShowSavedRoutes(true);
              }} style={styles.menuItem}>
                <Route size={20} color="#0065EB" /><Text style={styles.menuItemText}>My Routes</Text>
              </Pressable>
          )}
          <Pressable accessibilityRole="button" onPress={() => {
            setMenuOpen(false);
            // The map centre is the fallback position when location access is unavailable.
            router.push({ pathname: '/events', params: { lat: String(camera.center[1]), lng: String(camera.center[0]) } });
          }} style={styles.menuItem}>
            <CalendarDays size={20} color="#0065EB" /><Text style={styles.menuItemText}>Upcoming events</Text>
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel={session ? 'Account' : 'Sign in'}
            onPress={() => { setMenuOpen(false); router.push(session ? '/home' : '/login'); }} style={styles.menuItem}>
            <UserRound size={20} color="#0065EB" />
            <Text style={styles.menuItemText}>{session ? 'Account' : 'Sign in'}</Text>
          </Pressable>
        </View>}
      </View>

      <View style={[styles.tools, { top: Math.max(insets.top, 12) + 4 }]}>
        <Tool label="Zoom in" icon={Plus} disabled={!style || loading} onPress={() => map.current?.zoomBy(1)} />
        <Tool label="Zoom out" icon={Minus} disabled={!style || loading} onPress={() => map.current?.zoomBy(-1)} />
        <Tool label="Reset north" icon={Compass} disabled={!style || loading} onPress={() => map.current?.resetNorth()} />
        <Tool label="My location" icon={LocateFixed} disabled={!style || loading || locating} onPress={locate} />
      </View>
      {displayedError && <View style={styles.error}><Text accessibilityRole="alert" style={styles.errorText}>{displayedError}</Text>
  {error && <Tool label="Retry map" icon={RotateCcw} onPress={retryMap} />}
      </View>}
    </View>
    {selectedPoi && <View style={styles.poiDetails}>
      <View style={styles.poiHeading}>
        <Text accessibilityRole="header" style={styles.poiName}>{selectedPoi.name}</Text>
        <Tool label="Close place details" icon={X} onPress={() => setSelectedPoi(null)} />
      </View>
      <ScrollView style={styles.poiDescription}>
        <Text style={styles.poiCategory}>{selectedPoi.category}</Text>
        {!!selectedPoi.sources && <Text style={styles.poiMetadata}>{selectedPoi.sources}</Text>}
        {!!selectedPoi.attribution && <Text style={styles.poiMetadata}>{selectedPoi.attribution}</Text>}
        {!!selectedPoi.licenses && <Text style={styles.poiMetadata}>{selectedPoi.licenses}</Text>}
      </ScrollView>
    </View>}

    <View style={[styles.searchArea, compactSearchLayout ? styles.searchAreaCompact : null,
      compactSearchLayout && mobileSearchExpanded ? styles.searchAreaExpanded : null, compactSearchLayout ? {
      bottom: mobileSearchExpanded ? 0 : Math.max(insets.bottom, 4),
      maxHeight: Math.max(180, Math.min(440, windowHeight - Math.max(insets.top, 12) - 30)),
      paddingBottom: mobileSearchExpanded ? Math.max(insets.bottom, 8) : undefined,
    } : { top: Math.max(insets.top, 12) + 1 }]}
      onLayout={event => setMobileSearchHeight(height => height === event.nativeEvent.layout.height ? height : event.nativeEvent.layout.height)}>
      {compactSearchLayout && <View style={[styles.mobileSearchHandle, mobileSearchExpanded && styles.mobileSearchHandleExpanded]}>
        <Pressable
          {...mobileSearchPanResponder.panHandlers}
          accessibilityRole="button"
          accessibilityLabel={mobileSearchExpanded ? 'Hide location search' : 'Show location search'}
          accessibilityState={{ expanded: mobileSearchExpanded }}
          onPress={event => {
            event.stopPropagation();
            setMobileSearchExpanded(expanded => !expanded);
          }}
          style={[styles.mobileSearchHandleTarget, mobileSearchExpanded ? styles.mobileSearchHandleTargetExpanded : styles.mobileSearchHandleCollapsed]}
        >
          <View style={styles.mobileSearchGrip} />
        </Pressable>
      </View>}
      {(!compactSearchLayout || mobileSearchExpanded) && <Animated.View style={compactSearchLayout ? {
        opacity: searchReveal,
        transform: [{ translateY: searchReveal.interpolate({ inputRange: [0, 1], outputRange: [12, 0] }) }],
      } : undefined}>
      <View style={!compactSearchLayout && showPlanTripLink ? styles.desktopSearchGroup : undefined}>
      <View style={[styles.searchRow, !compactSearchLayout && showPlanTripLink && styles.searchRowJoinedTop]}>
        <Search size={19} color="#0065EB" style={styles.searchIcon} />
        <TextInput
          accessibilityLabel="Search coordinates or places"
          accessibilityHint="Enter latitude and longitude, or a place name, then press Enter"
          value={coordinateQuery}
          onChangeText={value => { setCoordinateQuery(value); setCoordinateError(null); setPoiResults([]); }}
          onSubmitEditing={searchCoordinates}
          placeholder="Search places or coordinates"
          placeholderTextColor="#8491a2"
          returnKeyType="search"
          editable
          autoCapitalize="none"
          autoCorrect={false}
          style={styles.searchInput}
        />
        {searchingPoi ? <ActivityIndicator size="small" color="#0065EB" style={styles.searchSpinner} /> : (searchedLocation || coordinateQuery.length > 0) && <Pressable
          accessibilityRole="button"
          accessibilityLabel={searchedLocation ? 'Clear search and remove map marker' : 'Clear search'}
          onPress={() => { setSearchedLocation(null); setCoordinateQuery(''); setPoiResults([]); setCoordinateError(null); }}
          style={styles.clearMarkerButton}
        ><X size={20} color="#0065EB" /></Pressable>}
      </View>
      {showPlanTripLink && <Pressable
        accessibilityRole="button"
        accessibilityLabel="Plan a trip"
        onPress={() => { if (compactSearchLayout) setMobileSearchExpanded(false); router.push('/trip-plan'); }}
        style={[styles.planTripSearchLink, !compactSearchLayout && styles.planTripSearchLinkJoined]}
      >
        <View style={styles.planTripSearchIcon}><Route size={20} color="#0065EB" /></View>
        <Text style={styles.planTripSearchText}>Plan a trip</Text>
        <Plus size={23} color="#0065EB" />
      </Pressable>}
      {poiResults.length > 0 && <View style={[styles.poiResults, !compactSearchLayout && showPlanTripLink && styles.poiResultsJoined]}>
        <ScrollView
          style={styles.poiResultsList}
          keyboardShouldPersistTaps="always"
          nestedScrollEnabled
          showsVerticalScrollIndicator={false}
        >
        {poiResults.map((result, index) => <Pressable
          key={`${result.name}-${result.coordinates[0]}-${result.coordinates[1]}-${index}`}
          accessibilityRole="button"
          accessibilityLabel={`Select ${result.name}${result.detail ? `, ${result.detail}` : ''}`}
          onPress={() => {
            selectPoiResult(result);
          }}
          style={[styles.poiResult, index > 0 && styles.poiResultBorder]}
        >
          <MapPin size={17} color="#0065EB" />
          <View style={styles.poiResultText}>
            <Text numberOfLines={1} style={styles.poiResultName}>{result.name}</Text>
            {!!result.detail && <Text numberOfLines={1} style={styles.poiResultDetail}>{result.detail}</Text>}
          </View>
        </Pressable>)}
        </ScrollView>
        <Text style={styles.poiAttribution}>Place search by Photon · © OpenStreetMap contributors</Text>
      </View>}
      {coordinateError && <Text accessibilityRole="alert" style={styles.coordinateError}>{coordinateError}</Text>}
      </View>
      </Animated.View>}
    </View>

    {selectedZoneInfo && !routeMode && (
        <View style={styles.infoCard}>
          <Text style={styles.infoCardTitle}>
            Dangerous Zone (Intensity: {selectedZoneInfo.intensity})
          </Text>
          <Text style={styles.infoCardText}>
            Reported for: {selectedZoneInfo.categories.join(', ')}
          </Text>
          <Pressable onPress={() => setSelectedZoneInfo(null)} style={styles.closeInfoButton}>
            <Text style={styles.closeInfoText}>Close</Text>
          </Pressable>
        </View>
    )}

    <View style={[styles.bottomOverlayArea, { bottom: compactSearchLayout && mobileSearchExpanded
      ? mobileSearchHeight + 12
      : Math.max(insets.bottom, 12) + 64 }]}>
      {isSelectingLocation ? (
          <View style={styles.actionButtonsRow}>
            <Pressable style={styles.cancelButton} onPress={() => setIsSelectingLocation(false)}><Text style={styles.cancelText}>Cancel</Text></Pressable>
            <Pressable style={styles.confirmButton} onPress={handleConfirmLocation}><Text style={styles.confirmText}>Confirm Location</Text></Pressable>
          </View>
      ) : (!routeMode && <DangerousZoneButton onPress={handleReportPress} />)}
    </View>
    {reportLocation && (
        <DangerousZoneModal
            visible={zoneModalVisible}
            onClose={() => {
              setZoneModalVisible(false);
              setReportLocation(null);
            }}
            currentLat={reportLocation.lat}
            currentLng={reportLocation.lng}
            onSuccess={() => {
              console.log('Dangerous zone reported successfully!');
            }}
        />
    )}
    <View pointerEvents="none" style={[styles.coordinateBadge, { bottom: Math.max(insets.bottom, 12) + 10 }]}>
      <MapPin size={13} color="#0065EB" />
      <Text style={styles.coordinates}>{camera.center[1].toFixed(4)}, {camera.center[0].toFixed(4)} · z{camera.zoom.toFixed(1)}</Text>
      {locating && <ActivityIndicator size="small" color="#0065EB" accessibilityLabel="Finding your location" />}
    </View>
    {session && (
        <SavedRoutesModal
            visible={showSavedRoutes}
            onClose={() => setShowSavedRoutes(false)}
            onSelectRoute={handleResumeSavedRoute}
        />
    )}
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ffffff' },
  mapMenu: { position: 'absolute', left: 12, top: 16, zIndex: 5, alignItems: 'flex-start' },
  menuButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 16, backgroundColor: '#fff', borderWidth: 1, borderColor: '#dce2e9', elevation: 4, shadowColor: '#14243f', shadowOpacity: 0.14, shadowRadius: 10, shadowOffset: { width: 0, height: 3 } },
  menuPanel: { width: 242, marginTop: 8, padding: 7, borderRadius: 18, backgroundColor: '#fff', borderWidth: 1, borderColor: '#dce2e9', gap: 2, elevation: 5, shadowColor: '#14243f', shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 4 } },
  menuItem: { minHeight: 46, borderRadius: 13, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 13 },
  menuItemText: { color: '#263241', fontSize: 15, fontWeight: '700' },
  map: { flex: 1, position: 'relative', backgroundColor: '#edf0f3', overflow: 'hidden' },
  tools: { position: 'absolute', right: 12, top: 16, gap: 8 },
  tool: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 6, borderWidth: 1, borderColor: '#dce2e9', backgroundColor: '#ffffff' },
  dim: { opacity: 0.55 },
  tooltip: { position: 'absolute', right: 52, backgroundColor: '#ffffff', color: '#363d47', padding: 8, borderRadius: 4, width: 110, fontSize: 12, borderWidth: 1, borderColor: '#dce2e9' },
  loading: { position: 'absolute', top: 18, left: 76, backgroundColor: '#ffffff', padding: 12, borderRadius: 6 },
  error: { position: 'absolute', left: 12, right: 12, bottom: 44, flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12, backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#ff0000', borderRadius: 6 },
  errorText: { flex: 1, color: '#a60000', fontSize: 14 },
  poiDetails: { paddingHorizontal: 16, paddingVertical: 12, borderTopWidth: 1, borderColor: '#dce2e9', backgroundColor: '#ffffff' },
  poiHeading: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  poiName: { flex: 1, fontSize: 18, fontWeight: '600', color: '#363d47' },
  poiDescription: { maxHeight: 110 },
  poiCategory: { color: '#0065EB', fontSize: 14, marginBottom: 6, textTransform: 'capitalize' },
  poiMetadata: { color: '#626b78', fontSize: 12, lineHeight: 18 },
  coordinateBadge: { position: 'absolute', alignSelf: 'center', flexDirection: 'row', alignItems: 'center',
    gap: 6, paddingHorizontal: 11, paddingVertical: 8, borderRadius: 18, backgroundColor: '#fff',
    borderWidth: 1, borderColor: '#dce2e9', elevation: 4, shadowColor: '#14243f', shadowOpacity: 0.14,
    shadowRadius: 10, shadowOffset: { width: 0, height: 3 } },
  coordinates: { color: '#263241', fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  centerPin: { position: 'absolute', top: '50%', left: '50%', marginLeft: -21, marginTop: -42, zIndex: 10, alignItems: 'center', justifyContent: 'center' },
  bottomOverlayArea: { position: 'absolute', right: 20, zIndex: 100, alignItems: 'flex-end' },
  actionButtonsRow: { flexDirection: 'row', gap: 12 },
  confirmButton: { backgroundColor: '#d93838', paddingHorizontal: 20, paddingVertical: 14, borderRadius: 30, elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4 },
  confirmText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  cancelButton: { backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 14, borderRadius: 30, elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4 },
  cancelText: { color: '#363d47', fontWeight: '700', fontSize: 15 },
  infoCard: { position: 'absolute', bottom: 150, left: 20, right: 20, backgroundColor: '#ffffff', padding: 16, borderRadius: 12, elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4, zIndex: 110 },
  infoCardTitle: { fontWeight: '700', fontSize: 16, color: '#d93838', marginBottom: 8 },
  infoCardText: { color: '#363d47', fontSize: 14 },
  closeInfoButton: { marginTop: 12, paddingVertical: 8 },
  closeInfoText: { color: '#0065EB', fontWeight: '600', fontSize: 14 },

  searchArea: { position: 'absolute', left: 70, right: 70, zIndex: 20 },
  searchAreaCompact: { left: 12, right: 12 },
  searchAreaExpanded: { left: 0, right: 0, paddingHorizontal: 16, paddingBottom: 8, borderTopLeftRadius: 20, borderTopRightRadius: 20, backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#d8e0ea', elevation: 8, shadowColor: '#14243f', shadowOpacity: 0.18, shadowRadius: 14, shadowOffset: { width: 0, height: 5 } },
  desktopSearchGroup: { borderRadius: 16, borderWidth: 1, borderColor: '#d8e0ea', backgroundColor: '#ffffff', overflow: 'hidden' },
  mobileSearchHandle: { height: 34, alignItems: 'center', justifyContent: 'center' },
  mobileSearchHandleExpanded: { height: 20 },
  mobileSearchHandleTarget: { width: 90, height: 34, alignItems: 'center', justifyContent: 'center' },
  mobileSearchHandleTargetExpanded: { height: 20 },
  mobileSearchHandleCollapsed: { justifyContent: 'flex-end', paddingBottom: 1 },
  mobileSearchGrip: { width: 78, height: 5, borderRadius: 3, backgroundColor: '#aab5c2' },
  searchRow: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#fff', borderRadius: 16, borderWidth: 1, borderColor: '#d8e0ea', paddingHorizontal: 15, minHeight: 54, elevation: 5, shadowColor: '#14243f', shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 4 } },
  searchRowJoinedTop: { borderWidth: 0, borderBottomWidth: 1, borderBottomColor: '#d8e0ea', borderRadius: 0, elevation: 0, shadowOpacity: 0 },
  planTripSearchLink: { minHeight: 50, flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 10, paddingHorizontal: 10, borderRadius: 16, borderWidth: 1, borderColor: '#d8e0ea', backgroundColor: '#ffffff' },
  planTripSearchLinkJoined: { marginTop: 0, borderWidth: 0, borderBottomWidth: 1, borderBottomColor: '#d8e0ea', borderRadius: 0 },
  planTripSearchIcon: { width: 34, height: 34, alignItems: 'center', justifyContent: 'center', borderRadius: 17, backgroundColor: '#eaf2ff' },
  planTripSearchText: { flex: 1, color: '#0065EB', fontSize: 15, fontWeight: '500' },
  searchIcon: { marginRight: 11, flexShrink: 0 },
  searchInput: { flex: 1, flexShrink: 1, minWidth: 0, height: 52, color: '#263241', fontSize: 15 },
  searchSpinner: { marginLeft: 8 },
  clearMarkerButton: { width: 32, height: 32, flexShrink: 0, marginLeft: 8, alignItems: 'center', justifyContent: 'center', borderRadius: 16, backgroundColor: '#f1f5f9' },
  poiResults: { backgroundColor: '#fff', borderRadius: 16, borderWidth: 1, borderColor: '#d8e0ea', marginTop: 8, paddingTop: 5, maxHeight: 280, overflow: 'hidden', elevation: 8, shadowColor: '#14243f', shadowOpacity: 0.18, shadowRadius: 14, shadowOffset: { width: 0, height: 5 } },
  poiResultsJoined: { marginTop: 0, borderWidth: 0, borderRadius: 0, elevation: 0, shadowOpacity: 0 },
  poiResultsList: { flexShrink: 1, maxHeight: 230 },
  poiResult: { minHeight: 58, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 10, gap: 11 },
  poiResultBorder: { borderTopWidth: 1, borderTopColor: '#edf0f3' },
  poiResultText: { flex: 1, minWidth: 0 },
  poiResultName: { fontSize: 15, fontWeight: '600', color: '#263241' },
  poiResultDetail: { fontSize: 13, color: '#758091', marginTop: 2 },
  poiAttribution: { fontSize: 10, color: '#8793a2', textAlign: 'center', paddingHorizontal: 8, paddingVertical: 8, borderTopWidth: 1, borderTopColor: '#edf0f3' },
  coordinateError: { backgroundColor: '#ffe5e5', color: '#a60000', padding: 10, borderRadius: 8, marginTop: 6, fontSize: 13, borderWidth: 1, borderColor: '#ff0000' },
  routeOverlay: {
    position: 'absolute',
    left: 12,
    width: 300,
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    elevation: 6,
    shadowColor: '#14243f',
    shadowOpacity: 0.16,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    zIndex: 100
  },
  routeOverlayTitle: { fontSize: 16, fontWeight: '700', color: '#263241', marginBottom: 12 },
  routeInput: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12, borderWidth: 1, borderColor: '#dce2e9', borderRadius: 10, marginBottom: 10, backgroundColor: '#f9f9fb' },
  routeStopRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 },
  routeStopInput: { flex: 1, marginBottom: 0 },
  removeStopBtn: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: 18 },
  addStopBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, alignSelf: 'flex-start', paddingVertical: 6, paddingHorizontal: 4, marginBottom: 10 },
  addStopText: { color: '#0065EB', fontWeight: '600', fontSize: 14 },
  routeInputActive: { borderColor: '#9b59b6', backgroundColor: '#fdfaee' },
  routeDot: { width: 10, height: 10, borderRadius: 5 },
  routeInputText: { fontSize: 14, color: '#363d47', flex: 1 },
  routeActions: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 },
  routeCloseBtn: { paddingVertical: 10, paddingHorizontal: 12 },
  routeCloseText: { color: '#758091', fontWeight: '600', fontSize: 15 },
  routeStartBtn: { backgroundColor: '#9b59b6', paddingVertical: 10, paddingHorizontal: 24, borderRadius: 20, elevation: 2 },
  routeStartText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
  navigatingOverlay: {position: 'absolute', top: 90, left: 0, right: 0, alignItems: 'center', zIndex: 100},
  navigatingActionsRow: {flexDirection: 'row', alignItems: 'center', gap: 12},
  recenterBtn: {backgroundColor: '#0065EB', width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', elevation: 6, shadowColor: '#14243f', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.25, shadowRadius: 8},
  pauseRouteBtn: {backgroundColor: '#9b59b6', paddingHorizontal: 24, paddingVertical: 12, borderRadius: 24, elevation: 6, shadowColor: '#14243f', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.25, shadowRadius: 8},
  pauseRouteText: {color: '#ffffff', fontWeight: '700', fontSize: 15},
  arrivalAlertContainer: { position: 'absolute', top: 120, alignSelf: 'center', backgroundColor: '#4C9F54', paddingHorizontal: 24, paddingVertical: 12, borderRadius: 24, elevation: 6, shadowColor: '#14243f', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.25, shadowRadius: 8, zIndex: 101 },
  arrivalAlertText: { color: '#ffffff', fontWeight: '700', fontSize: 16 },
});
