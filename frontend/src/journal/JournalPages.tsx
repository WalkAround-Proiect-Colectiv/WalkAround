import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { ArrowLeft, CalendarDays, ChevronRight, Download, MapPin, Pencil, Plus, RefreshCw, Trash2, X } from 'lucide-react-native';
import type { SavedTripPlan } from '../tripplan/api/tripPlanning';
import { journalApi } from './client';
import { journalError, type JournalTrip } from './api';
import { dateLabel } from './dates';
import JournalTripForm from './JournalTripForm';
import { Command, JournalPage, styles } from './ui';

export function JournalScreen() {
  return <JournalPage title="Private journal" root><TripList /></JournalPage>;
}

function TripList() {
  const router = useRouter();
  const [items, setItems] = useState<JournalTrip[]>([]);
  const [next, setNext] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [more, setMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const refresh = useCallback(() => {
    const active = ++generation.current;
    return journalApi.list().then(page => { if (generation.current === active) { setItems(page.items); setNext(page.nextPage); setError(null); } })
      .catch(failure => { if (generation.current === active) setError(journalError(failure)); })
      .finally(() => { if (generation.current === active) { setLoading(false); setMore(false); } });
  }, []);
  useFocusEffect(useCallback(() => {
    void refresh();
    return () => { generation.current += 1; };
  }, [refresh]));

  async function loadMore() {
    if (next === null || more) return;
    const active = generation.current;
    setMore(true);
    try {
      const page = await journalApi.list(next);
      if (active !== generation.current) return;
      setItems(current => [...current, ...page.items.filter(item => !current.some(existing => existing.id === item.id))]);
      setNext(page.nextPage); setError(null);
    } catch (failure) { if (active === generation.current) setError(journalError(failure)); }
    finally { if (active === generation.current) setMore(false); }
  }

  return <ScrollView contentContainerStyle={styles.content}>
    <View style={styles.between}><Text style={styles.heading}>Your trips</Text><Command label="Add trip" icon={Plus} primary onPress={() => router.push('/journal/new')} /></View>
    {loading && <ActivityIndicator color="#0065EB" accessibilityLabel="Loading journal" />}
    {!!error && <View style={styles.field}><Text accessibilityRole="alert" style={styles.error}>{error}</Text>
      <Command label="Retry journal" icon={RefreshCw} onPress={() => { setLoading(true); void refresh(); }} /></View>}
    {!loading && !error && !items.length && <Text style={styles.muted}>No trips yet.</Text>}
    {items.map(trip => <Pressable key={trip.id} accessibilityRole="button" accessibilityLabel={`Open ${trip.title}`}
      style={styles.tripRow} onPress={() => router.push({ pathname: '/journal/[id]', params: { id: trip.id } })}>
      <View style={styles.between}><Text style={styles.heading}>{trip.title}</Text><ChevronRight size={20} color="#0065EB" /></View>
      <View style={styles.row}><MapPin size={16} color="#637084" /><Text style={styles.text}>{trip.destinationCity}, {trip.countryName}</Text></View>
      <Text style={styles.muted}>{dateLabel(trip.startDate)} - {dateLabel(trip.endDate)}</Text>
    </Pressable>)}
    {next !== null && <Command label={more ? 'Loading trips' : 'Load more trips'} icon={RefreshCw} disabled={more} onPress={() => { void loadMore(); }} />}
  </ScrollView>;
}

export function JournalCreateScreen() {
  return <JournalPage title="Add journal trip"><CreateTrip /></JournalPage>;
}

function CreateTrip() {
  const router = useRouter();
  const [mode, setMode] = useState<'manual' | 'import'>('manual');
  const [source, setSource] = useState<SavedTripPlan | null>(null);
  const [today, setToday] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    journalApi.settings().then(settings => { if (active) setToday(settings.today); })
      .catch(failure => { if (active) setError(journalError(failure)); });
    return () => { active = false; };
  }, [attempt]);
  return <View style={styles.fill}>
    <View style={[styles.content, { paddingBottom: 12, paddingTop: 16 }]}>
      <View accessibilityRole="tablist" style={styles.row}>
        <Pressable accessibilityRole="tab" accessibilityLabel="Create manually" accessibilityState={{ selected: mode === 'manual' }}
          onPress={() => { setMode('manual'); setSource(null); }} style={[styles.command, mode === 'manual' && styles.primary]}>
          <Plus size={18} color={mode === 'manual' ? '#fff' : '#0065EB'} /><Text style={[styles.commandText, mode === 'manual' && styles.white]}>Create manually</Text>
        </Pressable>
        <Pressable accessibilityRole="tab" accessibilityLabel="Import saved plan" accessibilityState={{ selected: mode === 'import' }}
          onPress={() => { setMode('import'); setSource(null); }} style={[styles.command, mode === 'import' && styles.primary]}>
          <Download size={18} color={mode === 'import' ? '#fff' : '#0065EB'} /><Text style={[styles.commandText, mode === 'import' && styles.white]}>Import saved plan</Text>
        </Pressable>
      </View>
      {!!source && <Command label="Choose another plan" icon={ArrowLeft} onPress={() => setSource(null)} />}
      {!!error && <View style={styles.field}><Text accessibilityRole="alert" style={styles.error}>{error}</Text>
        <Command label="Retry" icon={RefreshCw} onPress={() => { setError(null); setAttempt(value => value + 1); }} /></View>}
    </View>
    {!today ? !error && <ActivityIndicator accessibilityLabel="Loading trip calendar" color="#0065EB" />
      : mode === 'import' && !source ? <PlanPicker onSelect={setSource} />
        : <JournalTripForm key={source?.id ?? 'manual'} today={today} source={source ?? undefined}
          onSaved={trip => router.replace({ pathname: '/journal/[id]', params: { id: trip.id } })} onCancel={() => router.replace('/journal')} />}
  </View>;
}

function PlanPicker({ onSelect }: { onSelect(plan: SavedTripPlan): void }) {
  const [items, setItems] = useState<SavedTripPlan[]>([]);
  const [next, setNext] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    journalApi.plans().then(page => { if (active.current) { setItems(page.items); setNext(page.nextPage); } })
      .catch(failure => { if (active.current) setError(journalError(failure)); })
      .finally(() => { if (active.current) setLoading(false); });
    return () => { active.current = false; };
  }, [attempt]);
  async function loadMore() {
    if (next === null || loading) return;
    setLoading(true);
    try { const page = await journalApi.plans(next); if (active.current) { setItems(current => [...current, ...page.items]); setNext(page.nextPage); } }
    catch (failure) { if (active.current) setError(journalError(failure)); }
    finally { if (active.current) setLoading(false); }
  }
  return <ScrollView contentContainerStyle={styles.content}>
    <Text style={styles.heading}>Personal saved plans</Text>
    {loading && <ActivityIndicator accessibilityLabel="Loading saved plans" color="#0065EB" />}
    {!!error && <View style={styles.field}><Text accessibilityRole="alert" style={styles.error}>{error}</Text>
      <Command label="Retry saved plans" icon={RefreshCw} onPress={() => { setError(null); setLoading(true); setAttempt(value => value + 1); }} /></View>}
    {!loading && !error && !items.length && <Text style={styles.muted}>No personal saved plans yet.</Text>}
    {items.map(plan => <Pressable key={plan.id} accessibilityRole="button" accessibilityLabel={`Import ${plan.title}`} style={styles.tripRow} onPress={() => onSelect(plan)}>
      <Text style={styles.heading}>{plan.title}</Text><Text style={styles.text}>{plan.destinationCity}, {plan.countryCode}</Text>
      <Text style={styles.muted}>{dateLabel(plan.departureFrom.slice(0, 10))} - {dateLabel(plan.departureUntil.slice(0, 10))} | {plan.stayingNights} nights</Text>
    </Pressable>)}
    {next !== null && <Command label="Load more saved plans" icon={RefreshCw} disabled={loading} onPress={() => { void loadMore(); }} />}
  </ScrollView>;
}

export function JournalTripScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <JournalPage title="Journal trip"><TripDetail key={id} id={id} /></JournalPage>;
}

function TripDetail({ id }: { id: string }) {
  const router = useRouter();
  const [trip, setTrip] = useState<JournalTrip | null>(null);
  const [today, setToday] = useState('');
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    Promise.all([journalApi.get(id), journalApi.settings()]).then(([found, settings]) => {
      if (alive.current) { setTrip(found); setToday(settings.today); }
    }).catch(failure => { if (alive.current) setError(journalError(failure)); })
      .finally(() => { if (alive.current) setLoading(false); });
    return () => { alive.current = false; };
  }, [id, attempt]);
  async function remove() {
    if (deleting) return;
    setDeleting(true); setError(null);
    try { await journalApi.remove(id); if (alive.current) router.replace('/journal'); }
    catch (failure) { if (alive.current) { setError(journalError(failure)); setDeleting(false); } }
  }
  if (editing && trip) return <JournalTripForm today={today} trip={trip} onSaved={saved => { setTrip(saved); setEditing(false); }} onCancel={() => setEditing(false)} />;
  return <ScrollView contentContainerStyle={styles.content}>
    {loading && <ActivityIndicator color="#0065EB" accessibilityLabel="Loading journal trip" />}
    {!!error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
    {!loading && !trip && <Command label="Retry trip" icon={RefreshCw} onPress={() => { setLoading(true); setError(null); setAttempt(value => value + 1); }} />}
    {trip && <>
      <Text accessibilityRole="header" style={styles.heading}>{trip.title}</Text>
      <View style={styles.row}><MapPin size={20} color="#0065EB" /><Text style={styles.text}>{trip.destinationCity}, {trip.countryName}</Text></View>
      <View style={styles.row}><CalendarDays size={20} color="#0065EB" /><Text style={styles.text}>{dateLabel(trip.startDate)} - {dateLabel(trip.endDate)}</Text></View>
      <View style={styles.section}><Text style={styles.label}>Description</Text><Text style={styles.text}>{trip.description}</Text></View>
      <View style={styles.row}>
        <Command label="Edit trip" icon={Pencil} disabled={deleting} onPress={() => { setError(null); setEditing(true); setConfirming(false); }} />
        <Command label="Delete trip" icon={Trash2} disabled={deleting} onPress={() => setConfirming(true)} />
      </View>
      {confirming && <View style={styles.warning}>
        <Text style={styles.text}>Delete {trip.title} from your private journal?</Text>
        <View style={styles.row}><Command label={deleting ? 'Deleting trip' : 'Confirm deletion'} icon={Trash2} danger disabled={deleting} onPress={() => { void remove(); }} />
          <Command label="Cancel deletion" icon={X} disabled={deleting} onPress={() => setConfirming(false)} /></View>
      </View>}
    </>}
  </ScrollView>;
}