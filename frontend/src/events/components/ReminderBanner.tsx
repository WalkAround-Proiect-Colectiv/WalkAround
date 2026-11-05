import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Bell, X } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import { refreshSession } from '../../login/api/session';
import { getUnseenReminders, markReminderSeen } from '../api/events';
import { formatEventDate } from '../format';

type Reminder = {
  eventKey: string;
  localityKey: string;
  title: string;
  startsAt: string;
  allDay: boolean;
  venueName: string | null;
};

const RECONNECT_DELAY_MS = 5000;

function notificationsUrl(token: string): string | null {
  const base = process.env.EXPO_PUBLIC_WS_URL?.trim();
  if (!base) return null;
  return `${base.replace(/\/+$/, '')}/ws/notifications?token=${encodeURIComponent(token)}`;
}

function parseReminder(data: unknown): Reminder | null {
  if (typeof data !== 'string') return null;
  try {
    const message = JSON.parse(data);
    if (message?.type !== 'EVENT_REMINDER' || typeof message.eventKey !== 'string') return null;
    return {
      eventKey: message.eventKey,
      localityKey: message.localityKey,
      title: message.title,
      startsAt: message.startsAt,
      allDay: Boolean(message.allDay),
      venueName: message.venueName ?? null,
    };
  } catch {
    return null;
  }
}

/**
 * In-app event reminders, shown over any screen. Reminders arrive live from the realtime service; ones
 * that came due while the app was closed are fetched when the user signs in or the app starts.
 */
export default function ReminderBanner() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { session } = useSession();
  const accessToken = session?.accessToken ?? null;
  const expiresAt = session?.expiresAt ?? null;
  const userId = session?.user.id ?? null;
  const [queue, setQueue] = useState<Reminder[]>([]);

  const add = (reminder: Reminder) => setQueue(current =>
    current.some(item => item.eventKey === reminder.eventKey) ? current : [...current, reminder]);

  // Reminders that were sent while the app was closed.
  useEffect(() => {
    if (!userId) return;
    let active = true;
    getUnseenReminders()
      .then(list => { if (active) list.forEach(add); })
      .catch(() => { /* Live reminders still work; missed ones are retried next start. */ });
    return () => { active = false; setQueue([]); };
  }, [userId]);

  // Live reminders. Reconnects when the access token is refreshed or the connection drops. The server
  // rejects expired tokens, so an expiring session is refreshed first; the new token re-runs this effect.
  useEffect(() => {
    if (!accessToken || !expiresAt) return;
    const url = notificationsUrl(accessToken);
    if (!url) return;
    let socket: WebSocket | null = null;
    let retry: ReturnType<typeof setTimeout> | null = null;
    let closed = false;
    const connect = () => {
      if (Date.parse(expiresAt) <= Date.now() + 30_000) {
        refreshSession().catch(() => { if (!closed) retry = setTimeout(connect, RECONNECT_DELAY_MS); });
        return;
      }
      socket = new WebSocket(url);
      socket.onmessage = event => {
        const reminder = parseReminder(event.data);
        if (reminder) add(reminder);
      };
      socket.onclose = () => {
        if (!closed) retry = setTimeout(connect, RECONNECT_DELAY_MS);
      };
    };
    connect();
    return () => {
      closed = true;
      if (retry) clearTimeout(retry);
      socket?.close();
    };
  }, [accessToken, expiresAt]);

  const current = queue[0];
  if (!current) return null;

  function dismiss(open: boolean) {
    setQueue(items => items.slice(1));
    markReminderSeen(current.eventKey).catch(() => {});
    if (open) {
      router.push({ pathname: '/events/details', params: { localityKey: current.localityKey, eventKey: current.eventKey } });
    }
  }

  return (
    <View pointerEvents="box-none" style={[styles.wrapper, { top: insets.top + 8 }]}>
      <View accessibilityRole="alert" style={styles.banner}>
        <Bell size={20} color="#0065EB" />
        <View style={styles.body}>
          <Text style={styles.title} numberOfLines={2}>{current.title}</Text>
          <Text style={styles.meta} numberOfLines={1}>
            {formatEventDate(current.startsAt, current.allDay)}{current.venueName ? ` · ${current.venueName}` : ''}
          </Text>
          <Pressable accessibilityRole="button" onPress={() => dismiss(true)}>
            <Text style={styles.action}>View event</Text>
          </Pressable>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Dismiss reminder" onPress={() => dismiss(false)} hitSlop={8}>
          <X size={18} color="#5f6b7a" />
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { position: 'absolute', left: 12, right: 12, alignItems: 'center', zIndex: 1000 },
  banner: {
    width: '100%', maxWidth: 520, flexDirection: 'row', gap: 10, alignItems: 'flex-start', padding: 12,
    borderRadius: 14, backgroundColor: '#fff', borderWidth: 1, borderColor: '#0065EB',
    shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 6,
  },
  body: { flex: 1, gap: 2 },
  title: { color: '#202632', fontWeight: '700', fontSize: 15 },
  meta: { color: '#5f6b7a', fontSize: 13 },
  action: { color: '#0065EB', fontWeight: '700', marginTop: 4 },
});
