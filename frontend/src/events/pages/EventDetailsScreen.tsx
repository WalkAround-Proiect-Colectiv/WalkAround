import { useEffect, useState } from 'react';
import { ActivityIndicator, Image, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, Bell, CalendarDays, ExternalLink, Heart, MapPin } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import { getEventDetails, markInterest, unmarkInterest, type EventDetails } from '../api/events';
import { REMINDER_OPTIONS, formatEventDate, isSafeLink } from '../format';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

function message(error: unknown) {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

function firstParam(value: string | string[] | undefined) {
  return (Array.isArray(value) ? value[0] : value) ?? '';
}

/**
 * Details for one event: `/events/details?localityKey=...&eventKey=...`.
 * Also the screen to open from an event on the map.
 */
export default function EventDetailsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { session, ready } = useSession();
  const params = useLocalSearchParams<{ localityKey?: string; eventKey?: string }>();
  const localityKey = firstParam(params.localityKey);
  const eventKey = firstParam(params.eventKey);

  const [details, setDetails] = useState<EventDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const signedIn = Boolean(session);
  useEffect(() => {
    if (!signedIn || !localityKey || !eventKey) return;
    let active = true;
    getEventDetails(localityKey, eventKey)
      .then(result => { if (active) { setDetails(result); setError(null); } })
      .catch(failure => { if (active) setError(message(failure)); });
    return () => { active = false; };
  }, [signedIn, localityKey, eventKey]);

  async function update(action: () => Promise<unknown>) {
    setSaving(true);
    setActionError(null);
    try {
      await action();
      setDetails(await getEventDetails(localityKey, eventKey));
    } catch (failure) {
      setActionError(message(failure));
    } finally {
      setSaving(false);
    }
  }

  if (!ready) return <View style={styles.center}><ActivityIndicator color={BLUE} /></View>;
  if (!session) return <Redirect href="/login" />;

  const event = details?.event;
  const interest = details?.interest;
  const title = interest?.title ?? event?.title ?? '';
  const startsAt = interest?.startsAt ?? event?.startsAt;
  const allDay = interest?.allDay ?? event?.allDay ?? false;
  const venue = interest?.venueName ?? event?.venueName;
  const address = interest?.address ?? event?.address;
  const thumbnail = interest?.thumbnailUrl ?? event?.thumbnailUrl;
  const tickets = event?.tickets?.length
    ? event.tickets
    : interest?.ticketUrl ? [{ source: interest.ticketSource, url: interest.ticketUrl }] : [];
  const reminder = interest?.remindBeforeMinutes ?? null;

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back"
                   onPress={() => (router.canGoBack() ? router.back() : router.replace('/events'))} style={styles.back}>
          <ArrowLeft size={22} color={BLUE} />
        </Pressable>
        <Text accessibilityRole="header" style={styles.heading}>Event</Text>
      </View>

      {!localityKey || !eventKey ? (
        <Text style={styles.stateText}>This event link is incomplete.</Text>
      ) : error ? (
        <Text accessibilityRole="alert" style={styles.stateText}>{error}</Text>
      ) : !details ? (
        <View style={styles.center}><ActivityIndicator color={BLUE} accessibilityLabel="Loading event" /></View>
      ) : (
        <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
          {isSafeLink(thumbnail) && <Image source={{ uri: thumbnail }} style={styles.image} accessibilityIgnoresInvertColors />}
          <Text style={styles.title}>{title}</Text>
          {startsAt && (
            <View style={styles.row}><CalendarDays size={16} color={MUTED} />
              <Text style={styles.meta}>{formatEventDate(startsAt, allDay)}</Text></View>
          )}
          {(venue || address) && (
            <View style={styles.row}><MapPin size={16} color={MUTED} />
              <Text style={styles.meta}>{[venue, address && address !== venue ? address : null].filter(Boolean).join(' · ')}</Text></View>
          )}
          {interest?.description && <Text style={styles.description}>{interest.description}</Text>}

          {actionError && <Text accessibilityRole="alert" style={styles.inlineError}>{actionError}</Text>}

          <Pressable accessibilityRole="button" disabled={saving} accessibilityState={{ selected: Boolean(interest), busy: saving }}
                     onPress={() => update(() => interest ? unmarkInterest(eventKey) : markInterest(localityKey, eventKey, null))}
                     style={[styles.primary, interest && styles.primaryOn, saving && styles.dim]}>
            <Heart size={18} color={interest ? '#e0245e' : '#fff'} fill={interest ? '#e0245e' : 'transparent'} />
            <Text style={[styles.primaryText, interest && styles.primaryTextOn]}>
              {interest ? 'Interested · tap to remove' : 'Mark as interested'}
            </Text>
          </Pressable>

          {interest && (
            <View style={styles.reminderBox}>
              <View style={styles.row}><Bell size={16} color={BLUE} /><Text style={styles.reminderTitle}>Remind me</Text></View>
              <View style={styles.chips}>
                {REMINDER_OPTIONS.map(option => {
                  const selected = option.minutes === reminder;
                  return (
                    <Pressable key={option.label} accessibilityRole="radio" accessibilityState={{ selected, disabled: saving }}
                               disabled={saving || selected}
                               onPress={() => update(() => markInterest(interest.localityKey, eventKey, option.minutes))}
                               style={[styles.chip, selected && styles.chipOn]}>
                      <Text style={[styles.chipText, selected && styles.chipTextOn]}>{option.label}</Text>
                    </Pressable>
                  );
                })}
              </View>
              <Text style={styles.hint}>Reminders appear inside WalkAround while the app is open.</Text>
            </View>
          )}

          {tickets.filter(ticket => isSafeLink(ticket.url)).map(ticket => (
            <Pressable key={ticket.url} accessibilityRole="link" onPress={() => Linking.openURL(ticket.url)} style={styles.link}>
              <ExternalLink size={16} color={BLUE} />
              <Text style={styles.linkText}>Tickets and info{ticket.source ? ` · ${ticket.source}` : ''}</Text>
            </Pressable>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f7fb' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 16, paddingVertical: 12, backgroundColor: '#fff' },
  back: { padding: 6, borderRadius: 12 },
  heading: { fontSize: 20, fontWeight: '800', color: INK },
  content: { padding: 16, gap: 12, maxWidth: 720, width: '100%', alignSelf: 'center' },
  image: { width: '100%', height: 180, borderRadius: 16, backgroundColor: '#eef4ff' },
  title: { color: INK, fontSize: 22, fontWeight: '800' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  meta: { color: MUTED, fontSize: 14, flexShrink: 1 },
  description: { color: INK, fontSize: 15, lineHeight: 22 },
  primary: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: BLUE, borderRadius: 14, paddingVertical: 13 },
  primaryOn: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#f3b6c8' },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  primaryTextOn: { color: '#b0154a' },
  dim: { opacity: 0.6 },
  reminderBox: { backgroundColor: '#fff', borderRadius: 14, borderWidth: 1, borderColor: '#e3e8ef', padding: 12, gap: 10 },
  reminderTitle: { color: INK, fontWeight: '700' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingVertical: 7, paddingHorizontal: 12, borderRadius: 999, borderWidth: 1, borderColor: '#d5dce6' },
  chipOn: { backgroundColor: '#eef4ff', borderColor: BLUE },
  chipText: { color: MUTED, fontWeight: '600' },
  chipTextOn: { color: BLUE },
  hint: { color: '#8c96a4', fontSize: 12 },
  link: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 10 },
  linkText: { color: BLUE, fontWeight: '600' },
  stateText: { color: MUTED, textAlign: 'center', padding: 24 },
  inlineError: { color: '#b42318', backgroundColor: '#fdecea', padding: 10, borderRadius: 10 },
});
