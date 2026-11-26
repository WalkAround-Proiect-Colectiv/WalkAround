import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Bell, X } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import { refreshSession } from '../../login/api/session';
import { getUnseenReminders, markReminderSeen } from '../api/events';
import { formatEventDate } from '../format';
import { publishGroupPlanSaved } from '../../tripplan/api/groupPlanNotifications';
import { publishInboxChanged } from '../../notifications/liveInbox';
import { environment } from '../../config/environment';

type Reminder = {
  type: 'EVENT_REMINDER';
  eventKey: string;
  localityKey: string;
  title: string;
  startsAt: string;
  allDay: boolean;
  venueName: string | null;
};

type GroupPlanNotification = {
  type: 'GROUP_PLAN_SAVED';
  groupId: string;
  planId: string;
  groupName: string;
  title: string;
};

type Notification = Reminder | GroupPlanNotification;

function notificationKey(notification: Notification): string {
  return notification.type === 'EVENT_REMINDER'
    ? `event:${notification.eventKey}`
    : `group-plan:${notification.planId}`;
}

const RECONNECT_DELAY_MS = 5000;

function notificationsUrl(token: string): string | null {
  const base = process.env.EXPO_PUBLIC_WS_URL?.trim();
  if (!base) return null;
  return `${base.replace(/\/+$/, '')}/ws/notifications?token=${encodeURIComponent(token)}`;
}

function parseNotification(data: unknown): Notification | null {
  if (typeof data !== 'string') return null;
  try {
    const message = JSON.parse(data);
    if (message?.type === 'GROUP_PLAN_SAVED') {
      const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      if (!uuid.test(message.groupId) || !uuid.test(message.planId)
          || typeof message.groupName !== 'string' || typeof message.title !== 'string') return null;
      return {
        type: 'GROUP_PLAN_SAVED', groupId: message.groupId, planId: message.planId,
        groupName: message.groupName, title: message.title,
      };
    }
    if (message?.type !== 'EVENT_REMINDER' || typeof message.eventKey !== 'string') return null;
    return {
      type: 'EVENT_REMINDER',
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
 * In-app notifications shown over any screen. Event reminders are fetched after sign-in if missed;
 * group plan notifications arrive through the existing authenticated WebSocket while connected.
 */
export default function ReminderBanner() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { session } = useSession();
  const accessToken = session?.accessToken ?? null;
  const expiresAt = session?.expiresAt ?? null;
  const userId = session?.user.id ?? null;
  const [queue, setQueue] = useState<Notification[]>([]);

  const add = (notification: Notification) => setQueue(current =>
    current.some(item => notificationKey(item) === notificationKey(notification))
      ? current : [...current, notification]);

  // Reminders that were sent while the app was closed.
  useEffect(() => {
    if (!userId) return;
    let active = true;
    getUnseenReminders()
      .then(list => { if (active) list.forEach(reminder => add({ ...reminder, type: 'EVENT_REMINDER' })); })
      .catch(() => { /* Live reminders still work; missed ones are retried next start. */ });
    return () => { active = false; setQueue([]); };
  }, [userId]);

  // Live notifications. Reconnects when the access token is refreshed or the connection drops. The server
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
      socket.onopen = () => publishInboxChanged();
      socket.onmessage = event => {
        try {
          if (JSON.parse(event.data)?.type === 'INBOX_CHANGED') {
            publishInboxChanged();
            return;
          }
        } catch {
          // Existing notification parsing below handles malformed payloads.
        }
        const notification = parseNotification(event.data);
        if (!notification) return;
        add(notification);
        if (notification.type === 'GROUP_PLAN_SAVED') {
          publishGroupPlanSaved({ groupId: notification.groupId, planId: notification.planId });
        }
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

  useEffect(() => {
    if (!current) return;

    const timeout = setTimeout(() => {
      setQueue((items) => items[0] === current ? items.slice(1) : items);
    }, environment.notificationDisplayMs);

    return () => clearTimeout(timeout);
  }, [current]);

  if (!current) return null;

  function dismiss(open: boolean) {
    setQueue(items => items.slice(1));
    if (current.type === 'EVENT_REMINDER') {
      markReminderSeen(current.eventKey).catch(() => {});
      if (open) {
        router.push({ pathname: '/events/details', params: {
          localityKey: current.localityKey, eventKey: current.eventKey,
        } });
      }
    } else if (open) {
      router.push({ pathname: '/trip-groups', params: {
        groupId: current.groupId, tab: 'plan', planId: current.planId,
      } });
    }
  }

  return (
    <View pointerEvents="box-none" style={[styles.wrapper, { top: insets.top + 8 }]}>
      <View accessibilityRole="alert" style={styles.banner}>
        <Bell size={20} color="#0065EB" />
        <View style={styles.body}>
          <Text style={styles.title} numberOfLines={2}>
            {current.type === 'GROUP_PLAN_SAVED' ? `New plan saved: ${current.title}` : current.title}
          </Text>
          <Text style={styles.meta} numberOfLines={1}>
            {current.type === 'GROUP_PLAN_SAVED' ? current.groupName : <>
            {formatEventDate(current.startsAt, current.allDay)}{current.venueName ? ` · ${current.venueName}` : ''}
            </>}
          </Text>
          <Pressable accessibilityRole="button" onPress={() => dismiss(true)}>
            <Text style={styles.action}>{current.type === 'GROUP_PLAN_SAVED' ? 'View plan' : 'View event'}</Text>
          </Pressable>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Dismiss notification" onPress={() => dismiss(false)} hitSlop={8}>
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
