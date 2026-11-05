import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Link, useRouter } from 'expo-router';
import { useSession } from '../login/components/SessionProvider';
import { Compass, Heart, LocateFixed, Menu, Minus, Plus, RotateCcw, Route, UserRound, MapPin, X, type LucideIcon } from 'lucide-react-native';
import type { StyleSpecification } from 'maplibre-gl';
import MapCanvas from './MapCanvas';
import type { MapHandle } from './MapCanvas.types';
import { MAP_STYLE_URL, PLACES } from './config';
import { resolveStyleResources, styleMap } from './style';
import { useUserLocalization } from './userLocalization';
import DangerousZoneButton from '../dangerous-zone/components/DangerousZoneButton';
import DangerousZoneModal from '../dangerous-zone/components/DangerousZoneModal';
import { cellToBoundary } from 'h3-js';
import { fetchDangerousZonesHeatmap } from '../dangerous-zone/api/dangerousZoneApi';

function Tool({ label, icon: Icon, onPress, disabled = false }: { label: string; icon: LucideIcon; onPress(): void; disabled?: boolean }) {
  const [hovered, setHovered] = useState(false);
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled}
                    onHoverIn={() => setHovered(true)} onHoverOut={() => setHovered(false)}
                    onPress={onPress} style={({ pressed }) => [styles.tool, (disabled || pressed) && styles.dim]}>
    <Icon size={21} color={disabled ? '#9aa3ae' : '#0065EB'} />
    {hovered && <Text style={styles.tooltip}>{label}</Text>}
  </Pressable>;
}

export default function MapScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const map = useRef<MapHandle>(null);
  const { session } = useSession();
  const [style, setStyle] = useState<StyleSpecification | null>(null);
  const [camera, setCamera] = useState({ center: PLACES[0].center, zoom: PLACES[0].zoom });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const { userLocation, locating, error: locationError, locate } = useUserLocalization(map);
  const displayedError = error ?? locationError;
  const [isSelectingLocation, setIsSelectingLocation] = useState(false);
  const [zoneModalVisible, setZoneModalVisible] = useState(false);
  const [reportLocation, setReportLocation] = useState<{lat: number, lng: number} | null>(null);
  const [selectedZoneInfo, setSelectedZoneInfo] = useState<{ categories: string[], intensity: number } | null>(null);
  const [dangerousZonesData, setDangerousZonesData] = useState<any>({
    type: 'FeatureCollection',
    features: []
  });
  
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
    const wsUrl = process.env.EXPO_PUBLIC_WS_URL;
    if (!wsUrl) {
      console.warn("EXPO_PUBLIC_WS_URL is not defined in .env");
      return;
    }
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
    <View style={[styles.header, { paddingTop: Math.max(insets.top, 12) }]}>
      <View style={styles.heading}>
        <Compass size={28} color="#0065EB" />
        <Text style={styles.brand}>WalkAround</Text>
      </View>
      <Link href={session ? '/home' : '/login'} accessibilityLabel="Account" style={styles.account}><UserRound size={22} color="#0065EB" /></Link>
    </View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.presets} contentContainerStyle={styles.presetContent}>
      {PLACES.map(place => <Pressable key={place.name} accessibilityRole="button" accessibilityLabel={`View ${place.name}`}
                                      disabled={!style || loading} onPress={() => place.name === 'Romania' ? map.current?.fitCoverage() : map.current?.goTo(place)} style={styles.place}>
        <Text style={styles.placeText}>{place.name}</Text>
      </Pressable>)}
    </ScrollView>
    <View style={styles.map}>
{style && (
  <MapCanvas
    key={attempt}
    ref={map}
    style={style}
    userLocation={userLocation}
    zoomLevel={camera.zoom}
    dangerousZonesData={dangerousZonesData}
    onMove={setCamera}
    onReady={() => setLoading(false)}
    onError={() => {
      setLoading(false);
      setError('Some map resources could not load. Check your connection or retry.');
    }}
    onPress={properties => {
      if (properties) {
        setSelectedZoneInfo({
          intensity: properties.intensity,
          categories: typeof properties.topCategories === 'string'
            ? JSON.parse(properties.topCategories)
            : properties.topCategories,
        });
      } else {
        setSelectedZoneInfo(null);
      }
    }}
  />
)}
      {loading && <View pointerEvents="none" style={styles.loading}><ActivityIndicator color="#0065EB" accessibilityLabel="Loading map" /></View>}

      {isSelectingLocation && (
          <View style={styles.centerPin} pointerEvents="none">
            <MapPin size={42} color="#d93838" />
          </View>
      )}
      
      <View style={styles.mapMenu}>
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
          <Pressable accessibilityRole="button" accessibilityLabel="My future, coming soon"
                     accessibilityState={{ disabled: true }} disabled style={[styles.menuItem, styles.menuItemDisabled]}>
            <Heart size={20} color="#8c96a4" /><Text style={styles.menuItemTextDisabled}>My future</Text>
            <Text style={styles.menuComingSoon}>Coming soon</Text>
          </Pressable>
        </View>}
      </View>

      <View style={styles.tools}>
        <Tool label="Zoom in" icon={Plus} disabled={!style || loading} onPress={() => map.current?.zoomBy(1)} />
        <Tool label="Zoom out" icon={Minus} disabled={!style || loading} onPress={() => map.current?.zoomBy(-1)} />
        <Tool label="Reset north" icon={Compass} disabled={!style || loading} onPress={() => map.current?.resetNorth()} />
        <Tool label="My location" icon={LocateFixed} disabled={!style || loading || locating} onPress={locate} />
      </View>
      {displayedError && <View style={styles.error}><Text accessibilityRole="alert" style={styles.errorText}>{displayedError}</Text>
        {error && <Tool label="Retry map" icon={RotateCcw} onPress={() => setAttempt(value => value + 1)} />}
      </View>}
    </View>

    {selectedZoneInfo && (
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

    <View style={styles.bottomOverlayArea}>
      {isSelectingLocation ? (
          <View style={styles.actionButtonsRow}>
            <Pressable style={styles.cancelButton} onPress={() => setIsSelectingLocation(false)}>
              <Text style={styles.cancelText}>Cancel</Text>
            </Pressable>
            <Pressable style={styles.confirmButton} onPress={handleConfirmLocation}>
              <Text style={styles.confirmText}>Confirm Location</Text>
            </Pressable>
          </View>
      ) : (
          <DangerousZoneButton onPress={handleReportPress} />
      )}
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
    <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 10) }]}>
      <Text style={styles.footerText}>Romania</Text>
      <Text style={styles.coordinates}>{camera.center[1].toFixed(4)}, {camera.center[0].toFixed(4)} · z{camera.zoom.toFixed(1)}</Text>
      {locating && <ActivityIndicator size="small" color="#0065EB" accessibilityLabel="Finding your location" />}
    </View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ffffff' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingBottom: 12, borderBottomWidth: 1, borderColor: '#e5e8ed' },
  heading: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  mapMenu: { position: 'absolute', left: 12, top: 16, zIndex: 5, alignItems: 'flex-start' },
  menuButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 16, backgroundColor: '#fff', borderWidth: 1, borderColor: '#dce2e9', elevation: 4, shadowColor: '#14243f', shadowOpacity: 0.14, shadowRadius: 10, shadowOffset: { width: 0, height: 3 } },
  menuPanel: { width: 242, marginTop: 8, padding: 7, borderRadius: 18, backgroundColor: '#fff', borderWidth: 1, borderColor: '#dce2e9', gap: 2, elevation: 5, shadowColor: '#14243f', shadowOpacity: 0.16, shadowRadius: 12, shadowOffset: { width: 0, height: 4 } },
  menuItem: { minHeight: 46, borderRadius: 13, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 13 },
  menuItemText: { color: '#263241', fontSize: 15, fontWeight: '700' },
  menuItemDisabled: { opacity: 0.72 },
  menuItemTextDisabled: { color: '#758091', fontSize: 15, fontWeight: '700' },
  menuComingSoon: { marginLeft: 'auto', color: '#8c96a4', fontSize: 11, fontWeight: '600' },
  brand: { fontSize: 22, fontWeight: '700', color: '#0065EB' },
  account: { padding: 10 },
  presets: { flexGrow: 0, flexShrink: 0, height: 54, borderBottomWidth: 1, borderColor: '#e5e8ed' },
  presetContent: { alignItems: 'center', paddingHorizontal: 12, gap: 6 },
  place: { paddingHorizontal: 12, paddingVertical: 9, borderRadius: 6, backgroundColor: '#eef5ff' },
  placeText: { color: '#0065EB', fontWeight: '600', fontSize: 13 },
  map: { flex: 1, position: 'relative', backgroundColor: '#edf0f3', overflow: 'hidden' },
  tools: { position: 'absolute', right: 12, top: 16, gap: 8 },
  tool: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 6, borderWidth: 1, borderColor: '#dce2e9', backgroundColor: '#ffffff' },
  dim: { opacity: 0.55 },
  tooltip: { position: 'absolute', right: 52, backgroundColor: '#ffffff', color: '#363d47', padding: 8, borderRadius: 4, width: 110, fontSize: 12, borderWidth: 1, borderColor: '#dce2e9' },
  loading: { position: 'absolute', top: 18, left: 76, backgroundColor: '#ffffff', padding: 12, borderRadius: 6 },
  error: { position: 'absolute', left: 12, right: 12, bottom: 44, flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12, backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#ff0000', borderRadius: 6 },
  errorText: { flex: 1, color: '#a60000', fontSize: 14 },
  footer: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8, paddingHorizontal: 16, paddingTop: 10, borderTopWidth: 1, borderColor: '#e5e8ed' },
  footerText: { color: '#363d47', fontSize: 13, fontWeight: '600' },
  coordinates: { color: '#626b78', fontSize: 12, fontVariant: ['tabular-nums'] },
  centerPin: { position: 'absolute', top: '50%', left: '50%', marginLeft: -21, marginTop: -42, zIndex: 10, alignItems: 'center', justifyContent: 'center' },
  bottomOverlayArea: { position: 'absolute', right: 20, bottom: 80, zIndex: 100, alignItems: 'flex-end' },
  actionButtonsRow: { flexDirection: 'row', gap: 12 },
  confirmButton: { backgroundColor: '#d93838', paddingHorizontal: 20, paddingVertical: 14, borderRadius: 30, elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4 },
  confirmText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  cancelButton: { backgroundColor: '#fff', paddingHorizontal: 20, paddingVertical: 14, borderRadius: 30, elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4 },
  cancelText: { color: '#363d47', fontWeight: '700', fontSize: 15 },
  infoCard: { position: 'absolute', bottom: 150, left: 20, right: 20, backgroundColor: '#ffffff', padding: 16, borderRadius: 12, elevation: 5, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4, zIndex: 110 },
  infoCardTitle: { fontWeight: '700', fontSize: 16, color: '#d93838', marginBottom: 8 },
  infoCardText: { color: '#363d47', fontSize: 14 },
  closeInfoButton: { marginTop: 12, paddingVertical: 8 },
  closeInfoText: { color: '#0065EB', fontWeight: '600', fontSize: 14 }
});
