import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import * as Location from 'expo-location';
import { MapPin, RotateCcw, X } from 'lucide-react-native';
import { environment } from '../config/environment';
import { isInCoverage } from './config';
import { fetchNearbyPolice, policeDistance, type PoliceSearchResult, type PoliceStation } from './nearbyPolice';

type Props = {
  maxHeight: number;
  onResults(result: PoliceSearchResult | null): void;
  onSelect(station: PoliceStation): void;
  onClose(): void;
};

export default function NearbyPolicePanel({ maxHeight, onResults, onSelect, onClose }: Props) {
  const [radius, setRadius] = useState(3000);
  const [refresh, setRefresh] = useState(0);
  const [stations, setStations] = useState<PoliceStation[]>([]);
  const [loading, setLoading] = useState<string | null>('Finding your location...');
  const [error, setError] = useState<string | null>(null);
  const activeRequest = useRef<AbortController | null>(null);

  function restartSearch(nextRadius = radius) {
    activeRequest.current?.abort();
    setRadius(nextRadius);
    setRefresh(value => value + 1);
    setStations([]);
    setError(null);
    setLoading('Finding your location...');
    onResults(null);
  }

  useEffect(() => {
    const controller = new AbortController();
    activeRequest.current = controller;
    const timeout = setTimeout(() => {
      controller.abort();
      setLoading(null);
      setError('Location or station lookup timed out. Check location access and your connection, then retry.');
    }, 30000);

    void (async () => {
      try {
        const permission = await Location.requestForegroundPermissionsAsync();
        if (controller.signal.aborted) return;
        if (!permission.granted) {
          throw new Error('Allow foreground location in your device or browser settings, then press Refresh. The map centre is not used.');
        }
        const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
        if (controller.signal.aborted) return;
        const { longitude, latitude } = position.coords;
        if (!isInCoverage(longitude, latitude)) {
          throw new Error('Your position is outside the available Romania map coverage.');
        }
        if (!Number.isFinite(position.timestamp) || Date.now() - position.timestamp > 60000) {
          throw new Error('A current position could not be obtained. Please refresh to try again.');
        }
        setLoading('Finding police stations...');
        const origin: [number, number] = [longitude, latitude];
        const results = await fetchNearbyPolice(environment.apiBaseUrl, origin, radius, controller.signal);
        if (controller.signal.aborted) return;
        setStations(results);
        onResults({ stations: results, origin, radiusMeters: radius });
      } catch (reason) {
        if (!controller.signal.aborted) {
          setError(reason instanceof Error ? reason.message : 'Police stations are unavailable. Check location access and your connection.');
        }
      } finally {
        clearTimeout(timeout);
        if (!controller.signal.aborted) setLoading(null);
      }
    })();

    return () => {
      controller.abort();
      clearTimeout(timeout);
    };
  }, [radius, refresh, onResults]);

  return <View style={[styles.panel, { maxHeight }]}>
    <View style={styles.heading}>
      <Text accessibilityRole="header" style={styles.title}>Nearby police stations</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Close nearby police stations" onPress={onClose} style={styles.icon}>
        <X size={21} color="#0065EB" />
      </Pressable>
    </View>
    <View style={styles.controls}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.radiusScroll}
        contentContainerStyle={styles.radii} accessibilityRole="radiogroup" accessibilityLabel="Police search radius">
        {[1000, 3000, 5000, 10000].map(value => <Pressable key={value} accessibilityRole="radio"
          accessibilityState={{ checked: radius === value }} onPress={() => { if (radius !== value) restartSearch(value); }}
          style={[styles.radius, radius === value && styles.activeRadius]}>
          <Text style={{ color: radius === value ? '#fff' : '#0065EB' }}>{value / 1000} km</Text>
        </Pressable>)}
      </ScrollView>
      <Pressable accessibilityRole="button" accessibilityLabel="Refresh police stations using current location"
        onPress={() => restartSearch()} style={styles.icon}>
        <RotateCcw size={20} color="#0065EB" />
      </Pressable>
    </View>
    <ScrollView style={styles.results} keyboardShouldPersistTaps="handled">
      {loading && <View style={styles.loading}><ActivityIndicator color="#0065EB" /><Text>{loading}</Text></View>}
      {error && <View><Text accessibilityRole="alert" style={styles.error}>{error}</Text>
        <Pressable accessibilityRole="button" onPress={() => restartSearch()} style={styles.retry}>
          <Text style={styles.action}>Retry</Text>
        </Pressable>
      </View>}
      {!loading && !error && <>
        <Text style={styles.hint}>{stations.length ? `${stations.length} recorded stations - straight-line distance` : `No police stations recorded within ${radius / 1000} km of your position. Try a larger radius.`}</Text>
        {stations.map(station => <Pressable key={station.id} accessibilityRole="button"
          accessibilityLabel={`${station.name}, ${policeDistance(station.distanceMeters)} away`}
          onPress={() => onSelect(station)} style={styles.station}>
          <MapPin size={20} color="#0065EB" />
          <Text style={styles.name}>{station.name}</Text>
          <Text style={styles.action}>{policeDistance(station.distanceMeters)}</Text>
        </Pressable>)}
      </>}
    </ScrollView>
    <Text style={styles.attribution}>(c) OpenStreetMap contributors | ODbL 1.0. Recorded data may be incomplete.</Text>
  </View>;
}

const styles = StyleSheet.create({
  panel: { backgroundColor: '#fff', borderRadius: 18, padding: 14, gap: 8, elevation: 5, boxShadow: '0px 2px 8px #00000022' },
  heading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { flex: 1, fontSize: 17, fontWeight: '700', color: '#26313e' },
  controls: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 4 },
  radiusScroll: { flex: 1 },
  radii: { flexDirection: 'row', gap: 4 },
  radius: { minHeight: 44, paddingHorizontal: 10, justifyContent: 'center', borderWidth: 1, borderColor: '#dce0e5', borderRadius: 12 },
  activeRadius: { backgroundColor: '#0065EB', borderColor: '#0065EB' },
  icon: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  loading: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 8 },
  hint: { fontSize: 12, color: '#626d7a' },
  results: { flexShrink: 1 },
  station: { minHeight: 48, flexDirection: 'row', gap: 8, alignItems: 'center', paddingVertical: 8, borderBottomWidth: 1, borderColor: '#edf0f3' },
  name: { flex: 1, color: '#26313e' },
  action: { color: '#0065EB', fontWeight: '600' },
  error: { color: '#ac2525', fontSize: 13 },
  retry: { minHeight: 44, justifyContent: 'center' },
  attribution: { fontSize: 10, color: '#626d7a' },
});
