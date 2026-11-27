import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { CalendarDays, Check, RefreshCw, X } from 'lucide-react-native';
import * as Crypto from 'expo-crypto';
import CalendarPicker from '../tripplan/components/CalendarPicker';
import { searchCities, type CitySuggestion, type SavedTripPlan } from '../tripplan/api/tripPlanning';
import { journalApi } from './client';
import { journalError, type JournalTrip, type TripInput } from './api';
import { addNights, dateError, dateLabel, departureWindow } from './dates';
import { Command, Field, styles } from './ui';

export default function JournalTripForm({ today, source, trip, onSaved, onCancel }: {
  today: string; source?: SavedTripPlan; trip?: JournalTrip; onSaved(trip: JournalTrip): void; onCancel(): void;
}) {
  const initialCity = source?.destinationCity ?? trip?.destinationCity ?? '';
  const initialCountry = source?.countryCode ?? trip?.countryCode ?? '';
  const [title, setTitle] = useState(source?.title ?? trip?.title ?? '');
  const [description, setDescription] = useState(source?.description ?? trip?.description ?? '');
  const [city, setCity] = useState<CitySuggestion | null>(initialCity ? {
    name: initialCity, lookupName: initialCity, country: initialCountry, countryCode: initialCountry, latitude: null, longitude: null,
  } : null);
  const [query, setQuery] = useState(initialCity);
  const [suggestions, setSuggestions] = useState<CitySuggestion[]>([]);
  const [searching, setSearching] = useState(false);
  const [cityError, setCityError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [searchAttempt, setSearchAttempt] = useState(0);
  const [start, setStart] = useState(trip?.startDate ?? '');
  const [end, setEnd] = useState(trip?.endDate ?? '');
  const [startMode, setStartMode] = useState<'PLANNED' | 'CUSTOM' | null>(null);
  const [keepNights, setKeepNights] = useState<boolean | null>(null);
  const [calendar, setCalendar] = useState<'start' | 'end' | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const attempt = useRef<{ fingerprint: string; id: string } | null>(null);
  const savingRef = useRef(false);
  const alive = useRef(true);
  const window = source ? departureWindow(source.departureFrom, source.departureUntil, today) : null;
  const finalEnd = source && keepNights ? addNights(start, source.stayingNights) : end;

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    if (city || query.trim().length < 2) return;
    let active = true;
    const timer = setTimeout(() => {
      setSearching(true);
      searchCities(query.trim()).then(results => { if (active) { setSuggestions(results); setCityError(null); setSearched(true); } })
        .catch(failure => {
          if (active) setCityError(failure instanceof Error && failure.message.trim()
            ? failure.message : 'Destination search is unavailable. Try again.');
        })
        .finally(() => { if (active) setSearching(false); });
    }, 350);
    return () => { active = false; clearTimeout(timer); };
  }, [query, city, searchAttempt]);

  function chooseStartMode(mode: 'PLANNED' | 'CUSTOM') {
    setStartMode(mode); setStart(''); setEnd(''); setCalendar(null); setError(null);
  }

  async function save() {
    if (savingRef.current) return;
    let problem: string | null = null;
    if (!title.trim() || !description.trim()) problem = 'Trip name and description are required.';
    else if (!city) problem = 'Select a destination from the city suggestions.';
    else if (source && (startMode === null || keepNights === null)) problem = 'Choose a start-date option and confirm whether to keep the saved nights.';
    else if (source && startMode === 'PLANNED' && window && (start < window.minimum || start > window.maximum)) problem = 'Choose a date in the saved departure window.';
    else problem = dateError(today, start, finalEnd);
    if (problem || !city) { setError(problem); return; }
    const input: TripInput = {
      title: title.trim(), description: description.trim(), destinationCity: city.lookupName || city.name,
      countryCode: city.countryCode, startDate: start, endDate: source && keepNights ? null : end,
      sourcePlanId: source?.id ?? null, startMode: source ? startMode : null, keepNights: source ? keepNights : null,
    };
    const fingerprint = JSON.stringify(input);
    if (!attempt.current || attempt.current.fingerprint !== fingerprint) attempt.current = { fingerprint, id: Crypto.randomUUID() };
    savingRef.current = true; setSaving(true); setError(null);
    try {
      const saved = trip ? await journalApi.update(trip.id, input) : await journalApi.create(input, attempt.current.id);
      if (alive.current) onSaved(saved);
    } catch (failure) { if (alive.current) setError(journalError(failure)); }
    finally { savingRef.current = false; if (alive.current) setSaving(false); }
  }

  function choice(label: string, selected: boolean, onPress: () => void, disabled = false) {
    return <Pressable accessibilityRole="radio" accessibilityLabel={label} accessibilityState={{ checked: selected, disabled }} disabled={disabled || saving}
      onPress={onPress} style={[styles.choice, disabled && styles.dim]}>
      <View style={[styles.dot, selected && styles.selectedDot]} /><Text style={styles.choiceText}>{label}</Text>
    </Pressable>;
  }

  return <KeyboardAvoidingView style={styles.fill} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      {source && <View style={styles.section}>
        <Text style={styles.label}>Saved plan</Text><Text style={styles.text}>{source.title}</Text>
        <Text style={styles.muted}>{dateLabel(source.departureFrom.slice(0, 10))} - {dateLabel(source.departureUntil.slice(0, 10))} | {source.stayingNights} nights</Text>
      </View>}
      <Field label="Trip name *"><TextInput accessibilityLabel="Trip name" maxLength={120} value={title} onChangeText={setTitle} editable={!saving} style={styles.input} /></Field>
      <Field label="Description *"><TextInput accessibilityLabel="Trip description" maxLength={2000} multiline value={description} onChangeText={setDescription}
        editable={!saving} style={[styles.input, styles.description]} /></Field>
      <Field label="Destination *">
        <TextInput accessibilityLabel="Trip destination" placeholder="City name" value={query} editable={!saving} style={styles.input}
          onChangeText={value => { setQuery(value); setCity(null); setSuggestions([]); setSearching(false); setSearched(false); setCityError(null); }} />
        {city && <Text style={styles.muted}>{city.name}, {city.country}</Text>}
        {searching && <ActivityIndicator size="small" accessibilityLabel="Searching destinations" color="#0065EB" />}
        {!!cityError && <View style={styles.field}><Text accessibilityRole="alert" style={styles.error}>{cityError}</Text>
          <Command label="Retry destination search" icon={RefreshCw} disabled={searching} onPress={() => setSearchAttempt(value => value + 1)} /></View>}
        {!city && searched && !searching && !cityError && !suggestions.length && <Text style={styles.muted}>No matching cities.</Text>}
        {suggestions.length > 0 && !city && <View style={styles.suggestions}>{suggestions.map((suggestion, index) =>
          <Pressable key={`${suggestion.countryCode}-${suggestion.lookupName}-${index}`} accessibilityRole="button"
            accessibilityLabel={`Select ${suggestion.name}, ${suggestion.country}`} style={styles.suggestion}
            onPress={() => { setCity(suggestion); setQuery(suggestion.name); setSuggestions([]); setSearching(false); }}>
            <Text style={styles.text}>{suggestion.name}</Text><Text style={styles.muted}>{suggestion.country}</Text>
          </Pressable>)}</View>}
      </Field>
      {source && <>
        <View accessibilityRole="radiogroup" accessibilityLabel="Start date source">
          <Text style={styles.label}>Start date *</Text>
          {choice('Saved departure dates', startMode === 'PLANNED', () => chooseStartMode('PLANNED'), !window?.available)}
          {choice('Custom date', startMode === 'CUSTOM', () => chooseStartMode('CUSTOM'))}
        </View>
        <View accessibilityRole="radiogroup" accessibilityLabel="Keep saved nights">
          <Text style={styles.label}>Keep {source.stayingNights} nights? *</Text>
          {choice(`Yes, keep ${source.stayingNights} nights`, keepNights === true, () => { setKeepNights(true); setEnd(''); })}
          {choice('No, choose an end date', keepNights === false, () => { setKeepNights(false); setEnd(''); })}
        </View>
      </>}
      <View style={styles.dates}>
        <View style={styles.dateField}><Text style={styles.label}>Start date *</Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Choose trip start date" disabled={saving || Boolean(source && !startMode)}
            onPress={() => setCalendar('start')} style={[styles.input, styles.dateButton, source && !startMode && styles.dim]}>
            <CalendarDays size={18} color="#0065EB" /><Text style={styles.text}>{dateLabel(start)}</Text>
          </Pressable>
        </View>
        <View style={styles.dateField}><Text style={styles.label}>End date *</Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Choose trip end date" disabled={saving || Boolean(source && keepNights !== false)}
            onPress={() => setCalendar('end')} style={[styles.input, styles.dateButton]}>
            <CalendarDays size={18} color="#0065EB" /><Text style={styles.text}>{dateLabel(finalEnd)}</Text>
          </Pressable>
        </View>
      </View>
      {!!error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
      {saving && <ActivityIndicator color="#0065EB" accessibilityLabel="Saving journal trip" />}
      <View style={styles.row}>
        <Command label={trip ? 'Save changes' : 'Create trip'} icon={Check} primary disabled={saving} onPress={() => { void save(); }} />
        <Command label="Cancel" icon={X} disabled={saving} onPress={onCancel} />
      </View>
    </ScrollView>
    <CalendarPicker label={calendar === 'start' ? 'start' : 'end'} visible={calendar !== null} value={calendar === 'start' ? start : end}
      referenceDate={today} minimumDate={calendar === 'end' ? (start || today) : startMode === 'PLANNED' && window ? window.minimum : today}
      maximumDate={calendar === 'start' && startMode === 'PLANNED' ? window?.maximum : undefined}
      onClose={() => setCalendar(null)} onSelect={value => {
        if (calendar === 'start') { setStart(value); if (end && end < value) setEnd(''); } else setEnd(value);
      }} />
  </KeyboardAvoidingView>;
}