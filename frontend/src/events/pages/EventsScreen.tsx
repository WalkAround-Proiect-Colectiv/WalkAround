import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Redirect, useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Location from 'expo-location';
import { ArrowLeft, CalendarX, MapPin, RotateCcw } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import EventCard, { type EventCardItem } from '../components/EventCard';
import {
  getInterests, getNearbyEvents, markInterest, unmarkInterest,
  type EventInterest, type NearbyEvents, type UpcomingEvent,
} from '../api/events';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

type Tab = 'nearby' | 'interested';
type Position = { latitude: number; longitude: number; fromMap: boolean };

function message(error: unknown) {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

function firstParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

/** The device position, or the map centre the user came from when location is unavailable or denied. */
async function currentPosition(mapLat?: number, mapLng?: number): Promise<Position> {
  const fallback = Number.isFinite(mapLat) && Number.isFinite(mapLng)
    ? { latitude: mapLat as number, longitude: mapLng as number, fromMap: true }
    : null;
  try {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (permission.status === 'granted') {
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      return { latitude: position.coords.latitude, longitude: position.coords.longitude, fromMap: false };
    }
  } catch {
    // Fall back to the map position below.
  }
  if (fallback) return fallback;
  throw new Error('Allow location access to see events near you.');
}

function fromEvent(event: UpcomingEvent): EventCardItem {
  return event;
}

function fromInterest(interest: EventInterest): EventCardItem {
  return { ...interest, key: interest.eventKey, interested: true };
}

export default function EventsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { session, ready } = useSession();
  const params = useLocalSearchParams<{ lat?: string; lng?: string }>();
  const mapLat = Number(firstParam(params.lat));
  const mapLng = Number(firstParam(params.lng));

  const [tab, setTab] = useState<Tab>('nearby');
  const [position, setPosition] = useState<Position | null>(null);
  const [nearby, setNearby] = useState<NearbyEvents | null>(null);
  const [nearbyError, setNearbyError] = useState<string | null>(null);
  const [loadingNearby, setLoadingNearby] = useState(true);
  const [interests, setInterests] = useState<EventInterest[] | null>(null);
  const [interestsError, setInterestsError] = useState<string | null>(null);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Bumping the attempt (retry button) reloads the nearby list.
  const [attempt, setAttempt] = useState(0);

  function retryNearby() {
    setLoadingNearby(true);
    setNearbyError(null);
    setAttempt(value => value + 1);
  }

  const loadInterests = useCallback(async () => {
    try {
      const list = await getInterests();
      setInterests(list);
      setInterestsError(null);
    } catch (error) {
      setInterestsError(message(error));
    }
  }, []);

  const signedIn = Boolean(session);

  // State changes only in promise callbacks, and a newer load ignores older responses.
  useEffect(() => {
    if (!signedIn) return;
    let active = true;
    currentPosition(mapLat, mapLng)
      .then(async where => {
        const result = await getNearbyEvents(where.latitude, where.longitude);
        if (!active) return;
        setPosition(where);
        setNearby(result);
        setNearbyError(null);
      })
      .catch(error => { if (active) setNearbyError(message(error)); })
      .finally(() => { if (active) setLoadingNearby(false); });
    return () => { active = false; };
  }, [signedIn, mapLat, mapLng, attempt]);

  // Coming back from the details screen may have changed which events are marked.
  useFocusEffect(useCallback(() => {
    if (signedIn) loadInterests();
  }, [signedIn, loadInterests]));

  const interestedKeys = new Map((interests ?? []).map(interest => [interest.eventKey, interest]));

  async function toggleInterest(item: EventCardItem, localityKey: string) {
    setBusyKey(item.key);
    setActionError(null);
    try {
      if (item.interested) {
        await unmarkInterest(item.key);
      } else {
        await markInterest(localityKey, item.key, null);
      }
      await loadInterests();
    } catch (error) {
      setActionError(message(error));
    } finally {
      setBusyKey(null);
    }
  }

  function open(localityKey: string, eventKey: string) {
    router.push({ pathname: '/events/details', params: { localityKey, eventKey } });
  }

  if (!ready) return <View style={styles.center}><ActivityIndicator color={BLUE} /></View>;
  if (!session) return <Redirect href="/login" />;

  const nearbyItems = (nearby?.events ?? []).map(event => {
    const interest = interestedKeys.get(event.key);
    return {
      item: { ...fromEvent(event), interested: Boolean(interest), remindBeforeMinutes: interest?.remindBeforeMinutes ?? null },
      localityKey: event.localityKey,
    };
  });
  const interestedItems = (interests ?? []).map(interest => ({ item: fromInterest(interest), localityKey: interest.localityKey }));

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back to map"
                   onPress={() => (router.canGoBack() ? router.back() : router.replace('/map'))} style={styles.back}>
          <ArrowLeft size={22} color={BLUE} />
        </Pressable>
        <Text accessibilityRole="header" style={styles.heading}>Upcoming events</Text>
      </View>

      <View style={styles.tabs} accessibilityRole="tablist">
        {(['nearby', 'interested'] as Tab[]).map(value => (
          <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: tab === value }}
                     onPress={() => setTab(value)} style={[styles.tab, tab === value && styles.tabActive]}>
            <Text style={[styles.tabText, tab === value && styles.tabTextActive]}>
              {value === 'nearby' ? 'Nearby' : `Interested${interests?.length ? ` (${interests.length})` : ''}`}
            </Text>
          </Pressable>
        ))}
      </View>

      {actionError && <Text accessibilityRole="alert" style={styles.inlineError}>{actionError}</Text>}

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        {tab === 'nearby' && (
          <>
            {nearby?.locality && (
              <View style={styles.locality}>
                <MapPin size={15} color={MUTED} />
                <Text style={styles.localityText}>
                  {nearby.locality.name}{position?.fromMap ? ' (from the map position)' : ''}
                </Text>
              </View>
            )}
            {nearby?.stale && <Text style={styles.note}>Showing the last saved list. It could not be refreshed right now.</Text>}
            {loadingNearby && (
              <View style={styles.state}>
                <ActivityIndicator color={BLUE} accessibilityLabel="Loading events" />
                <Text style={styles.stateText}>Looking for events near you. The first search for a town can take a few seconds.</Text>
              </View>
            )}
            {!loadingNearby && nearbyError && (
              <View style={styles.state}>
                <Text accessibilityRole="alert" style={styles.stateText}>{nearbyError}</Text>
                <Pressable accessibilityRole="button" onPress={retryNearby} style={styles.retry}>
                  <RotateCcw size={16} color={BLUE} /><Text style={styles.retryText}>Try again</Text>
                </Pressable>
              </View>
            )}
            {!loadingNearby && !nearbyError && nearby && nearbyItems.length === 0 && (
              <View style={styles.state}>
                <CalendarX size={32} color={MUTED} />
                <Text style={styles.stateTitle}>No upcoming events near you</Text>
                <Text style={styles.stateText}>Events are listed for the town you are in. Check again later.</Text>
              </View>
            )}
            {!loadingNearby && nearbyItems.map(({ item, localityKey }) => (
              <EventCard key={item.key} event={item} busy={busyKey === item.key}
                         onOpen={() => open(localityKey, item.key)}
                         onToggleInterest={() => toggleInterest(item, localityKey)} />
            ))}
            {nearby && !loadingNearby && <Text style={styles.attribution}>{nearby.attribution}</Text>}
          </>
        )}

        {tab === 'interested' && (
          <>
            {interestsError && <Text accessibilityRole="alert" style={styles.stateText}>{interestsError}</Text>}
            {interests === null && !interestsError && <ActivityIndicator color={BLUE} />}
            {interests !== null && interestedItems.length === 0 && (
              <View style={styles.state}>
                <CalendarX size={32} color={MUTED} />
                <Text style={styles.stateTitle}>No events marked yet</Text>
                <Text style={styles.stateText}>Tap the heart on an event to keep it here and set a reminder.</Text>
              </View>
            )}
            {interestedItems.map(({ item, localityKey }) => (
              <EventCard key={item.key} event={item} busy={busyKey === item.key}
                         onOpen={() => open(localityKey, item.key)}
                         onToggleInterest={() => toggleInterest(item, localityKey)} />
            ))}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f7fb' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 16, paddingVertical: 12, backgroundColor: '#fff' },
  back: { padding: 6, borderRadius: 12 },
  heading: { fontSize: 20, fontWeight: '800', color: INK },
  tabs: { flexDirection: 'row', gap: 8, paddingHorizontal: 16, paddingVertical: 10, backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#e3e8ef' },
  tab: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: '#d5dce6' },
  tabActive: { backgroundColor: '#eef4ff', borderColor: BLUE },
  tabText: { color: MUTED, fontWeight: '600' },
  tabTextActive: { color: BLUE },
  content: { padding: 16, gap: 10, maxWidth: 720, width: '100%', alignSelf: 'center' },
  locality: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  localityText: { color: MUTED, fontSize: 14 },
  note: { color: '#8a5a00', backgroundColor: '#fff6e0', padding: 10, borderRadius: 10, fontSize: 13 },
  state: { alignItems: 'center', gap: 10, paddingVertical: 36, paddingHorizontal: 16 },
  stateTitle: { color: INK, fontSize: 16, fontWeight: '700', textAlign: 'center' },
  stateText: { color: MUTED, textAlign: 'center' },
  retry: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: BLUE },
  retryText: { color: BLUE, fontWeight: '600' },
  inlineError: { color: '#b42318', backgroundColor: '#fdecea', paddingHorizontal: 16, paddingVertical: 8 },
  attribution: { color: '#8c96a4', fontSize: 11, textAlign: 'center', marginTop: 12 },
});
