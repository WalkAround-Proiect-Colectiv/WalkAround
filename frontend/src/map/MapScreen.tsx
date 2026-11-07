import { useCallback, useEffect, useRef, useState, useMemo } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { useSession } from '../login/components/SessionProvider';
import { CloudSun, Compass, LocateFixed, Menu, Minus, Plus, RotateCcw, Route, UserRound, MapPin, X, Map as MapIcon, type LucideIcon } from 'lucide-react-native';
import type { StyleSpecification } from 'maplibre-gl';
import MapCanvas from './MapCanvas';
import type { MapHandle } from './MapCanvas.types';
import { isInCoverage, MAP_STYLE_URL, PLACES } from './config';
import { resolveStyleResources, styleMap } from './style';
import { useUserLocalization } from './userLocalization';
import DangerousZoneButton from '../dangerous-zone/components/DangerousZoneButton';
import DangerousZoneModal from '../dangerous-zone/components/DangerousZoneModal';
import { cellToBoundary } from 'h3-js';
import { fetchDangerousZonesHeatmap } from '../dangerous-zone/api/dangerousZoneApi';
import { environment } from '../config/environment';

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

export default function MapScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const map = useRef<MapHandle>(null);
  const { session } = useSession();
  const [style, setStyle] = useState<StyleSpecification | null>(null);
  const [camera, setCamera] = useState({ center: PLACES[0].center, zoom: PLACES[0].zoom });
  const [coordinateQuery, setCoordinateQuery] = useState('');
  const [searchedLocation, setSearchedLocation] = useState<[number, number] | null>(null);
  const [coordinateError, setCoordinateError] = useState<string | null>(null);
  const [poiResults, setPoiResults] = useState<PoiSearchResult[]>([]);
  const [searchingPoi, setSearchingPoi] = useState(false);
  const [poiSearchNonce, setPoiSearchNonce] = useState(0);
  const poiSearchRequest = useRef(0);
  const immediatePoiQuery = useRef('');
  const suppressPoiQuery = useRef('');
  const currentCamera = useRef(camera);
  currentCamera.current = camera;
  const [loading, setLoading] = useState(true);
  const [mapReady, setMapReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);

  const [routeMode, setRouteMode] = useState(false);
  const [activeRouteField, setActiveRouteField] = useState<'start' | 'end' | null>(null);
  const [routeStart, setRouteStart] = useState<{lat: number, lng: number} | null>(null);
  const [routeEnd, setRouteEnd] = useState<{lat: number, lng: number} | null>(null);
  const [routeGeoJSON, setRouteGeoJSON] = useState<any>(null);

  const { userLocation, locating, error: locationError, locate } = useUserLocalization(map);
  const locateRef = useRef(locate);
  locateRef.current = locate;

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
      setPoiResults([]);
      setSearchingPoi(false);
      return;
    }

    const controller = new AbortController();
    const delay = immediatePoiQuery.current === query ? 0 : 300;
    if (delay === 0) immediatePoiQuery.current = '';
    setCoordinateError(null);
    setPoiResults([]);
    setSearchingPoi(true);
    const timeout = setTimeout(async () => {
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
    const features = [];
    if (routeStart) features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [routeStart.lng, routeStart.lat] }, properties: { type: 'start' } });
    if (routeEnd) features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [routeEnd.lng, routeEnd.lat] }, properties: { type: 'end' } });
    return features.length > 0 ? { type: 'FeatureCollection', features } : null;
  }, [routeStart, routeEnd]);

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
        console.error("Could not load dangerous zones", e);
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
    setLoading(true);
    setMapReady(false);
    setError(null);
    setStyle(null);
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
    if (routeStart && routeEnd) {
      const baseUrl = process.env.EXPO_PUBLIC_API_BASE_URL;
      const fetchUrl = `${baseUrl}/api/v1/routes/pedestrian`;
      fetch(fetchUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          startLat: routeStart.lat,
          startLng: routeStart.lng,
          endLat: routeEnd.lat,
          endLng: routeEnd.lng
        })
      })
          .then(res => res.json())
          .then(data => {
            if (data && data.trip && data.trip.legs) {
              const encodedShape = data.trip.legs[0].shape;
              const decodedCoords = decodePolyline(encodedShape, 6);
              setRouteGeoJSON({ type: 'Feature', geometry: { type: 'LineString', coordinates: decodedCoords }});
            } else {
              console.log("No route found in response");
              setRouteGeoJSON(null);
            }
          })
          .catch(err => {
            console.error("Failed to calculate route:", err);
            setRouteGeoJSON(null);
          });
    }
  }, [routeStart, routeEnd]);

  function handleMapClick(properties: any, coords?: [number, number]) {
    if (routeMode && activeRouteField && coords) {
      const point = { lng: coords[0], lat: coords[1] };
      if (activeRouteField === 'start') {
        setRouteStart(point);
        if (!routeEnd) setActiveRouteField('end');
        else setActiveRouteField(null);
      } else {
        setRouteEnd(point);
        setActiveRouteField(null);
      }
      return;
    }
    if (properties) {
      setSelectedZoneInfo({ intensity: properties.intensity, categories: typeof properties.topCategories === 'string' ? JSON.parse(properties.topCategories) : properties.topCategories });
    } else {
      setSelectedZoneInfo(null);
    }
  }

  function handleReportPress() {
    if (!session) {
      router.push('/login');
      return;
    }
    setIsSelectingLocation(true);
  }

  function handleConfirmLocation() {
    setReportLocation({ lat: camera.center[1], lng: camera.center[0] });
    setIsSelectingLocation(false);
    setZoneModalVisible(true);
  }

  return <View style={styles.screen}>
    <View style={styles.map}>
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
    onMove={setCamera}
    onCoordinateSelect={handleMapCoordinateSelect}
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
      {loading && <View pointerEvents="none" style={styles.loading}><ActivityIndicator color="#0065EB" accessibilityLabel="Loading map" /></View>}

      {isSelectingLocation && (
          <View style={styles.centerPin} pointerEvents="none">
            <MapPin size={42} color="#d93838" />
          </View>
      )}

      {routeMode && (
          <View style={[styles.routeOverlay, { top: Math.max(insets.top, 12) + 64 }]}>
            <Text style={styles.routeOverlayTitle}>Plan your route</Text>

            <Pressable onPress={() => setActiveRouteField('start')} style={[styles.routeInput, activeRouteField === 'start' && styles.routeInputActive]}>
              <View style={[styles.routeDot, { backgroundColor: '#0065EB' }]} />
              <Text style={styles.routeInputText}>{routeStart ? `Start: ${routeStart.lat.toFixed(4)}, ${routeStart.lng.toFixed(4)}` : 'Tap map to select start point'}</Text>
            </Pressable>

            <Pressable onPress={() => setActiveRouteField('end')} style={[styles.routeInput, activeRouteField === 'end' && styles.routeInputActive]}>
              <View style={[styles.routeDot, { backgroundColor: '#d93838' }]} />
              <Text style={styles.routeInputText}>{routeEnd ? `Dest: ${routeEnd.lat.toFixed(4)}, ${routeEnd.lng.toFixed(4)}` : 'Tap map to select destination'}</Text>
            </Pressable>

            <View style={styles.routeActions}>
              <Pressable style={styles.routeCloseBtn} onPress={() => { setRouteMode(false); setActiveRouteField(null); setRouteStart(null); setRouteEnd(null); setRouteGeoJSON(null); }}>
                <Text style={styles.routeCloseText}>Cancel</Text>
              </Pressable>
              {routeGeoJSON && (
                  <Pressable style={styles.routeStartBtn} onPress={() => console.log('Start navigation task coming next')}>
                    <Text style={styles.routeStartText}>Start</Text>
                  </Pressable>
              )}
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
        {error && <Tool label="Retry map" icon={RotateCcw} onPress={() => setAttempt(value => value + 1)} />}
      </View>}
    </View>

    <View style={styles.searchArea}>
      <View style={styles.searchRow}>
        <TextInput
          accessibilityLabel="Search coordinates or places"
          accessibilityHint="Enter latitude and longitude, or a place name, then press Enter"
          value={coordinateQuery}
          onChangeText={value => { setCoordinateQuery(value); setCoordinateError(null); setPoiResults([]); }}
          onSubmitEditing={searchCoordinates}
          placeholder="Coordinates or place / POI name"
          placeholderTextColor="#788493"
          returnKeyType="search"
          editable
          autoCapitalize="none"
          autoCorrect={false}
          style={styles.searchInput}
        />
        {searchingPoi ? <ActivityIndicator size="small" color="#0065EB" style={styles.searchSpinner} /> : searchedLocation && <Pressable
          accessibilityRole="button"
          accessibilityLabel="Remove map marker"
          onPress={() => { setSearchedLocation(null); setCoordinateQuery(''); setPoiResults([]); setCoordinateError(null); }}
          style={styles.clearMarkerButton}
        ><X size={20} color="#0065EB" /></Pressable>}
      </View>
      {poiResults.length > 0 && <View style={styles.poiResults}>
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
        <Text style={styles.poiAttribution}>Place search by Photon · © OpenStreetMap contributors</Text>
      </View>}
      {coordinateError && <Text accessibilityRole="alert" style={styles.coordinateError}>{coordinateError}</Text>}
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

    <View style={[styles.bottomOverlayArea, { bottom: Math.max(insets.bottom, 12) + 64 }]}>
      {isSelectingLocation ? (
          <View style={styles.actionButtonsRow}>
            <Pressable style={styles.cancelButton} onPress={() => setIsSelectingLocation(false)}><Text style={styles.cancelText}>Cancel</Text></Pressable>
            <Pressable style={styles.confirmButton} onPress={() => { setReportLocation({ lat: camera.center[1], lng: camera.center[0] }); setIsSelectingLocation(false); setZoneModalVisible(true); }}><Text style={styles.confirmText}>Confirm Location</Text></Pressable>
          </View>
      ) : (!routeMode && <DangerousZoneButton onPress={() => session ? setIsSelectingLocation(true) : router.push('/login')} />)}
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
  routeInputActive: { borderColor: '#9b59b6', backgroundColor: '#fdfaee' },
  routeDot: { width: 10, height: 10, borderRadius: 5 },
  routeInputText: { fontSize: 14, color: '#363d47', flex: 1 },
  routeActions: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 },
  routeCloseBtn: { paddingVertical: 10, paddingHorizontal: 12 },
  routeCloseText: { color: '#758091', fontWeight: '600', fontSize: 15 },
  routeStartBtn: { backgroundColor: '#9b59b6', paddingVertical: 10, paddingHorizontal: 24, borderRadius: 20, elevation: 2 },
  routeStartText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
