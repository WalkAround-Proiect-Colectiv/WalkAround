import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { Bell, CalendarDays, Heart, MapPin } from 'lucide-react-native';
import { formatEventDate, isSafeLink } from '../format';

export type EventCardItem = {
  key: string;
  title: string;
  startsAt: string;
  allDay: boolean;
  venueName: string | null;
  address: string | null;
  thumbnailUrl: string | null;
  interested: boolean;
  remindBeforeMinutes: number | null;
};

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

export default function EventCard({ event, busy, onOpen, onToggleInterest }: {
  event: EventCardItem;
  busy: boolean;
  onOpen(): void;
  onToggleInterest(): void;
}) {
  const place = event.venueName ?? event.address;
  // The open and heart buttons are siblings: on web, a button inside a button is invalid HTML.
  return (
    <View style={styles.card}>
      <Pressable accessibilityRole="button" accessibilityLabel={`Open ${event.title}`} onPress={onOpen}
                 style={({ pressed }) => [styles.open, pressed && styles.pressed]}>
        {isSafeLink(event.thumbnailUrl)
          ? <Image source={{ uri: event.thumbnailUrl }} style={styles.thumb} accessibilityIgnoresInvertColors />
          : <View style={[styles.thumb, styles.thumbEmpty]}><CalendarDays size={22} color={BLUE} /></View>}
        <View style={styles.body}>
          <Text style={styles.title} numberOfLines={2}>{event.title}</Text>
          <Text style={styles.meta}>{formatEventDate(event.startsAt, event.allDay)}</Text>
          {place && <View style={styles.row}><MapPin size={13} color={MUTED} /><Text style={styles.meta} numberOfLines={1}>{place}</Text></View>}
          {event.remindBeforeMinutes !== null && (
            <View style={styles.row}><Bell size={13} color={BLUE} /><Text style={styles.reminder}>Reminder on</Text></View>
          )}
        </View>
      </Pressable>
      <Pressable accessibilityRole="button" disabled={busy}
                 accessibilityLabel={event.interested ? `Remove ${event.title} from interested` : `Mark ${event.title} as interested`}
                 accessibilityState={{ selected: event.interested, busy }}
                 onPress={onToggleInterest} hitSlop={8} style={[styles.heart, busy && styles.pressed]}>
        <Heart size={22} color={event.interested ? '#e0245e' : '#9aa3ae'} fill={event.interested ? '#e0245e' : 'transparent'} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row', gap: 12, padding: 12, borderRadius: 16, backgroundColor: '#fff',
    borderWidth: 1, borderColor: '#e3e8ef', alignItems: 'center',
  },
  open: { flex: 1, flexDirection: 'row', gap: 12, alignItems: 'center' },
  pressed: { opacity: 0.6 },
  thumb: { width: 64, height: 64, borderRadius: 12, backgroundColor: '#eef4ff' },
  thumbEmpty: { alignItems: 'center', justifyContent: 'center' },
  body: { flex: 1, gap: 3 },
  title: { color: INK, fontSize: 15, fontWeight: '700' },
  meta: { color: MUTED, fontSize: 13, flexShrink: 1 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  reminder: { color: BLUE, fontSize: 12, fontWeight: '600' },
  heart: { padding: 6 },
});
