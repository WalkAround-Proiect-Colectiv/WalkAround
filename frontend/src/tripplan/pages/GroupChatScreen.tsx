import { Fragment, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, MessageCircle, Send, Smile } from 'lucide-react-native';
import * as Crypto from 'expo-crypto';
import { useSession } from '../../login/components/SessionProvider';
import GroupPicture from '../components/GroupPicture';
import EmojiPicker from '../components/EmojiPicker';
import { getGroupChatMessages, getGroupChatTicket, sendGroupChatMessage,
  type GroupChatMessage } from '../api/groupChat';
import type { CreatedTripGroup } from '../api/tripGroups';

const BLUE = '#0065EB';
const PAGE_SIZE = 50;
const RECONNECT_DELAY_MS = 3000;
const CATCH_UP_INTERVAL_MS = 3000;

type PendingMessage = { id: string; body: string };
type SocketEvent = { type: 'SUBSCRIBED'; conversationId: string }
  | ({ type: 'GROUP_CHAT_MESSAGE' } & GroupChatMessage);

function mergeMessages(current: GroupChatMessage[], incoming: GroupChatMessage[]): GroupChatMessage[] {
  const byId = new Map(current.map((message) => [message.id, message]));
  incoming.forEach((message) => byId.set(message.id, message));
  return [...byId.values()].sort((left, right) => left.sequence - right.sequence);
}

function parseSocketEvent(data: unknown): SocketEvent | null {
  if (typeof data !== 'string') return null;
  try {
    const event = JSON.parse(data);
    if (event?.type === 'SUBSCRIBED' && typeof event.conversationId === 'string') {
      return event;
    }
    if (event?.type !== 'GROUP_CHAT_MESSAGE'
        || typeof event.messageId !== 'string'
        || typeof event.conversationId !== 'string'
        || !Number.isSafeInteger(event.sequence) || event.sequence < 1
        || typeof event.body !== 'string' || typeof event.createdAt !== 'string'
        || typeof event.senderName !== 'string') return null;
    return {
      type: 'GROUP_CHAT_MESSAGE',
      id: event.messageId,
      conversationId: event.conversationId,
      sequence: event.sequence,
      senderId: typeof event.senderId === 'string' ? event.senderId : null,
      senderName: event.senderName,
      body: event.body,
      createdAt: event.createdAt,
    };
  } catch {
    return null;
  }
}

function socketUrl(token: string): string | null {
  const base = process.env.EXPO_PUBLIC_WS_URL?.trim();
  return base ? `${base.replace(/\/+$/, '')}/ws/group-chats?token=${encodeURIComponent(token)}` : null;
}

export default function GroupChatScreen({ group }: { group: CreatedTripGroup }) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { session } = useSession();
  const [messages, setMessages] = useState<GroupChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [showEmojis, setShowEmojis] = useState(false);
  const [selection, setSelection] = useState({ start: 0, end: 0 });
  const [pending, setPending] = useState<PendingMessage | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [connection, setConnection] = useState('Connecting…');
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasOlder, setHasOlder] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const syncedSequence = useRef(0);
  const historyLoaded = useRef(false);
  const pendingRef = useRef<PendingMessage | null>(null);
  const listRef = useRef<ScrollView>(null);
  const loadingOlderRef = useRef(false);

  function acceptMessages(incoming: GroupChatMessage[]) {
    setMessages((current) => mergeMessages(current, incoming));
    if (pendingRef.current && incoming.some((message) => message.id === pendingRef.current?.id)) {
      pendingRef.current = null;
      setPending(null);
      setSendError(null);
    }
  }

  useEffect(() => {
    if (!session) return;
    let active = true;
    let generation = 0;
    let socket: WebSocket | null = null;
    let retry: ReturnType<typeof setTimeout> | null = null;
    let expiry: ReturnType<typeof setTimeout> | null = null;
    let catchUpTimer: ReturnType<typeof setInterval> | null = null;
    let catchingUp = false;

    const catchUp = async (connectionId: number) => {
      if (catchingUp) return;
      catchingUp = true;
      try {
        if (!historyLoaded.current) {
          const page = await getGroupChatMessages(group.id, { limit: PAGE_SIZE });
          if (!active || generation !== connectionId) return;
          acceptMessages(page);
          historyLoaded.current = true;
          setHasOlder(page.length === PAGE_SIZE);
          syncedSequence.current = page.at(-1)?.sequence ?? 0;
        } else {
          let cursor = syncedSequence.current;
          while (active && generation === connectionId) {
            const page = await getGroupChatMessages(group.id, { afterSequence: cursor, limit: PAGE_SIZE });
            if (!active || generation !== connectionId) return;
            if (page.length === 0) break;
            acceptMessages(page);
            cursor = page[page.length - 1].sequence;
            syncedSequence.current = cursor;
            if (page.length < PAGE_SIZE) break;
          }
        }
        if (active && generation === connectionId) {
          setLoading(false);
          setError(null);
        }
      } catch (reason) {
        if (active && generation === connectionId) {
          setLoading(false);
          setError(reason instanceof Error ? reason.message : 'Could not load chat messages.');
          socket?.close();
        }
      } finally {
        catchingUp = false;
      }
    };

    const connect = async () => {
      const connectionId = ++generation;
      setConnection('Connecting…');
      try {
        const ticket = await getGroupChatTicket(group.id);
        if (!active || generation !== connectionId) return;
        const url = socketUrl(ticket.token);
        if (!url) throw new Error('Live chat is not configured.');
        socket = new WebSocket(url);
        socket.onmessage = (received) => {
          const event = parseSocketEvent(received.data);
          if (!event || event.conversationId !== group.id) return;
          if (event.type === 'SUBSCRIBED') {
            setConnection('Live');
            void catchUp(connectionId);
            catchUpTimer = setInterval(() => { void catchUp(connectionId); }, CATCH_UP_INTERVAL_MS);
            const remaining = Date.parse(ticket.expiresAt) - Date.now() - 10_000;
            expiry = setTimeout(() => socket?.close(), Math.max(1000, remaining));
            return;
          }
          const { type: ignored, ...message } = event;
          acceptMessages([message]);
        };
        socket.onclose = () => {
          if (expiry) clearTimeout(expiry);
          if (catchUpTimer) clearInterval(catchUpTimer);
          if (!active || generation !== connectionId) return;
          setConnection('Reconnecting…');
          retry = setTimeout(() => { void connect(); }, RECONNECT_DELAY_MS);
        };
        socket.onerror = () => socket?.close();
      } catch (reason) {
        if (!active || generation !== connectionId) return;
        setConnection('Reconnecting…');
        setError(reason instanceof Error ? reason.message : 'Could not connect to group chat.');
        retry = setTimeout(() => { void connect(); }, RECONNECT_DELAY_MS);
      }
    };

    void connect();
    return () => {
      active = false;
      generation++;
      if (retry) clearTimeout(retry);
      if (expiry) clearTimeout(expiry);
      if (catchUpTimer) clearInterval(catchUpTimer);
      socket?.close();
    };
  }, [group.id, session?.user.id]);

  async function send(item: PendingMessage) {
    setSending(true);
    setSendError(null);
    try {
      const saved = await sendGroupChatMessage(group.id, item.id, item.body);
      acceptMessages([saved]);
      if (pendingRef.current?.id === item.id) {
        pendingRef.current = null;
        setPending(null);
      }
    } catch (reason) {
      if (pendingRef.current?.id === item.id) {
        setSendError(reason instanceof Error ? reason.message : 'Could not send the message.');
      }
    } finally {
      setSending(false);
    }
  }

  function submit() {
    if (sending || pendingRef.current) return;
    const body = draft.trim();
    if (!body || body.length > 2000) {
      setSendError('Enter a message of up to 2,000 characters.');
      return;
    }
    const item = { id: Crypto.randomUUID(), body };
    pendingRef.current = item;
    setPending(item);
    setDraft('');
    setSelection({ start: 0, end: 0 });
    setShowEmojis(false);
    void send(item);
  }

  function insertEmoji(emoji: string) {
    const start = Math.min(selection.start, draft.length);
    const end = Math.min(selection.end, draft.length);
    const next = draft.slice(0, start) + emoji + draft.slice(end);
    if (next.length > 2000) {
      setSendError('Message limit is 2,000 characters.');
      return;
    }
    const cursor = start + emoji.length;
    setDraft(next);
    setSelection({ start: cursor, end: cursor });
    setSendError(null);
  }

  async function loadOlder() {
    const first = messages[0];
    if (!first || loadingOlder) return;
    loadingOlderRef.current = true;
    setLoadingOlder(true);
    try {
      const page = await getGroupChatMessages(group.id, { beforeSequence: first.sequence, limit: PAGE_SIZE });
      acceptMessages(page);
      setHasOlder(page.length === PAGE_SIZE);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not load earlier messages.');
    } finally {
      setLoadingOlder(false);
      loadingOlderRef.current = false;
    }
  }

  return <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    style={[styles.screen, { paddingBottom: Math.max(insets.bottom, 12) }]}>
    <View style={[styles.header, { paddingTop: Math.max(insets.top, 12) }]}>
      <View style={styles.headerTop}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back to group"
          onPress={() => router.replace({ pathname: '/trip-groups', params: { groupId: group.id } })}
          style={styles.back}><ArrowLeft size={20} color="#202632" /></Pressable>
        <Text style={styles.pageTitle}>Messages</Text>
        <View style={styles.headerBadge}><MessageCircle size={14} color="#23012C" />
          <Text style={styles.headerBadgeText}>GROUP</Text></View>
      </View>
      <View style={styles.groupBanner}>
        {group.pictureUrl ? <GroupPicture groupId={group.id} size={46} />
          : <View style={styles.icon}><MessageCircle size={22} color={BLUE} /></View>}
        <View style={styles.heading}>
          <Text style={styles.title} numberOfLines={1}>{group.name}</Text>
          <Text style={styles.subtitle}>Shared trip space</Text>
        </View>
        <View style={[styles.connectionPill, connection === 'Live' && styles.connectionPillLive]}>
          <View style={[styles.connectionDot, connection === 'Live' && styles.connectionDotLive]} />
          <Text style={[styles.status, connection === 'Live' && styles.statusLive]}>
            {connection === 'Live' ? 'Live' : connection.startsWith('Reconnecting') ? 'Retrying' : 'Connecting'}
          </Text>
        </View>
      </View>
    </View>

    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
    <ScrollView ref={listRef} style={styles.messages} contentContainerStyle={styles.messageContent}
      onContentSizeChange={() => { if (!loadingOlderRef.current) listRef.current?.scrollToEnd({ animated: true }); }}>
      {hasOlder && <Pressable accessibilityRole="button" disabled={loadingOlder} onPress={() => { void loadOlder(); }}
        style={styles.older}><Text style={styles.olderText}>{loadingOlder ? 'Loading…' : 'Load earlier messages'}</Text></Pressable>}
      {loading && <ActivityIndicator color={BLUE} />}
      {!loading && messages.length === 0 && <View style={styles.emptyCard}>
        <View style={styles.emptyIcon}><MessageCircle size={26} color={BLUE} /></View>
        <Text style={styles.emptyTitle}>Start the conversation</Text>
        <Text style={styles.empty}>Share ideas, dates, and little discoveries with your group.</Text>
      </View>}
      {messages.map((message, index) => {
        const own = message.senderId === session?.user.id;
        const day = new Date(message.createdAt).toDateString();
        const previousDay = index > 0 ? new Date(messages[index - 1].createdAt).toDateString() : null;
        return <Fragment key={message.id}>
          {day !== previousDay && <View style={styles.dayRow}>
            <View style={styles.dayLine} />
            <Text style={styles.dayText}>
              {new Date(message.createdAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })}
            </Text>
            <View style={styles.dayLine} />
          </View>}
          <View style={[styles.bubble, own ? styles.ownBubble : styles.otherBubble]}>
            {!own && <Text style={styles.sender}>{message.senderName}</Text>}
            <Text style={own ? styles.ownText : styles.messageText}>{message.body}</Text>
            <Text style={own ? styles.ownTime : styles.time}>
              {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </Text>
          </View>
        </Fragment>;
      })}
      {pending && <View style={[styles.bubble, styles.ownBubble]}>
        <Text style={styles.ownText}>{pending.body}</Text>
        <Text style={styles.ownTime}>{sending ? 'Sending…' : 'Not confirmed'}</Text>
      </View>}
    </ScrollView>

    {showEmojis && <EmojiPicker onClose={() => setShowEmojis(false)} onSelect={insertEmoji} />}
    {sendError && <Text accessibilityRole="alert" style={styles.error}>{sendError}</Text>}
    {pending && !sending && <View style={styles.retryRow}>
      <Pressable accessibilityRole="button" onPress={() => { void send(pending); }}>
        <Text style={styles.retryText}>Retry this message</Text>
      </Pressable>
      <Pressable accessibilityRole="button" onPress={() => {
        pendingRef.current = null;
        setPending(null);
        setSendError(null);
      }}><Text style={styles.discardText}>Discard</Text></Pressable>
    </View>}
    <View style={styles.composer}>
      <Pressable accessibilityRole="button" accessibilityLabel={showEmojis ? 'Close emoji palette' : 'Open emoji palette'}
        accessibilityState={{ expanded: showEmojis }} onPress={() => {
          Keyboard.dismiss();
          setShowEmojis((open) => !open);
        }} style={[styles.emojiToggle, showEmojis && styles.emojiToggleActive]}>
        <Smile size={21} color={showEmojis ? BLUE : '#7D8B9E'} />
      </Pressable>
      <TextInput accessibilityLabel="Group message" multiline maxLength={2000}
        placeholder="Message your group" placeholderTextColor="#8A96A6"
        style={styles.input} value={draft} onChangeText={setDraft} selection={selection}
        onSelectionChange={({ nativeEvent }) => setSelection(nativeEvent.selection)}
        onFocus={() => setShowEmojis(false)} />
      <Pressable accessibilityRole="button" accessibilityLabel="Send message"
        disabled={sending || Boolean(pending) || !draft.trim()} onPress={submit}
        style={[styles.sendButton, (sending || Boolean(pending) || !draft.trim()) && styles.disabled]}>
        <Send size={19} color="#fff" />
      </Pressable>
    </View>
  </KeyboardAvoidingView>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F6F8FC' },
  header: { paddingHorizontal: 16, paddingBottom: 15, backgroundColor: '#fff', gap: 15,
    borderBottomWidth: 1, borderBottomColor: '#E5EAF2' },
  headerTop: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  back: { width: 42, height: 42, borderRadius: 15, alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#F3F6FA' },
  pageTitle: { flex: 1, color: '#202632', fontSize: 19, fontWeight: '800' },
  headerBadge: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: 10,
    paddingVertical: 7, borderRadius: 20, backgroundColor: '#F4EDF6' },
  headerBadgeText: { color: '#23012C', fontSize: 10, fontWeight: '800', letterSpacing: 0.7 },
  groupBanner: { flexDirection: 'row', alignItems: 'center', gap: 11, padding: 11,
    borderRadius: 19, backgroundColor: '#F5F8FF', borderWidth: 1, borderColor: '#E4EDFB' },
  icon: { width: 46, height: 46, borderRadius: 15, alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#E3EFFF' },
  heading: { flex: 1 },
  title: { color: '#202632', fontSize: 16, fontWeight: '800' },
  subtitle: { color: '#78869A', fontSize: 11, marginTop: 2 },
  connectionPill: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingHorizontal: 9,
    paddingVertical: 6, borderRadius: 14, backgroundColor: '#EDF1F6' },
  connectionPillLive: { backgroundColor: '#E7F7EF' },
  connectionDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#9AA6B5' },
  connectionDotLive: { backgroundColor: '#22A76B' },
  status: { color: '#687587', fontSize: 10, fontWeight: '700' },
  statusLive: { color: '#168457' },
  messages: { flex: 1 },
  messageContent: { paddingHorizontal: 16, paddingVertical: 20, gap: 10,
    flexGrow: 1, justifyContent: 'flex-end' },
  dayRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginVertical: 10 },
  dayLine: { flex: 1, height: 1, backgroundColor: '#E4EAF2' },
  dayText: { color: '#8895A6', fontSize: 11, fontWeight: '700' },
  bubble: { maxWidth: '84%', borderRadius: 20, paddingHorizontal: 15, paddingVertical: 10, gap: 5 },
  ownBubble: { alignSelf: 'flex-end', backgroundColor: BLUE, borderBottomRightRadius: 6,
    shadowColor: BLUE, shadowOpacity: 0.12, shadowRadius: 8, shadowOffset: { width: 0, height: 3 } },
  otherBubble: { alignSelf: 'flex-start', backgroundColor: '#fff', borderWidth: 1,
    borderColor: '#E7ECF4', borderBottomLeftRadius: 6,
    shadowColor: '#33415C', shadowOpacity: 0.05, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  sender: { color: '#23012C', fontWeight: '800', fontSize: 11 },
  ownText: { color: '#fff', fontSize: 14, lineHeight: 21 },
  messageText: { color: '#253043', fontSize: 14, lineHeight: 21 },
  ownTime: { color: '#D9E9FF', fontSize: 10, alignSelf: 'flex-end' },
  time: { color: '#8793A3', fontSize: 10, alignSelf: 'flex-end' },
  emptyCard: { alignSelf: 'center', alignItems: 'center', gap: 8, maxWidth: 270, marginTop: 36 },
  emptyIcon: { width: 54, height: 54, borderRadius: 20, backgroundColor: '#E5F0FF',
    alignItems: 'center', justifyContent: 'center', marginBottom: 4 },
  emptyTitle: { color: '#253043', fontSize: 16, fontWeight: '800', textAlign: 'center' },
  empty: { color: '#7D8B9E', fontSize: 13, lineHeight: 19, textAlign: 'center' },
  older: { alignSelf: 'center', padding: 8 },
  olderText: { color: BLUE, fontWeight: '700', fontSize: 13 },
  composer: { flexDirection: 'row', alignItems: 'flex-end', gap: 10, marginHorizontal: 12,
    padding: 7, paddingLeft: 8, backgroundColor: '#fff', borderRadius: 22,
    borderWidth: 1, borderColor: '#E3EAF3',
    shadowColor: '#253043', shadowOpacity: 0.08, shadowRadius: 12,
    shadowOffset: { width: 0, height: 3 }, elevation: 3 },
  emojiToggle: { width: 43, height: 43, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  emojiToggleActive: { backgroundColor: '#E5F0FF' },
  input: { flex: 1, maxHeight: 110, minHeight: 43, borderRadius: 15, backgroundColor: '#F5F7FB',
    color: '#202632', paddingHorizontal: 14, paddingVertical: 11, fontSize: 14, outlineWidth: 0 },
  sendButton: { width: 43, height: 43, borderRadius: 15, backgroundColor: BLUE,
    alignItems: 'center', justifyContent: 'center' },
  disabled: { opacity: 0.5 },
  error: { color: '#C51B2D', fontSize: 12, paddingHorizontal: 16, paddingTop: 8 },
  retryRow: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 8 },
  retryText: { color: BLUE, fontWeight: '700' },
  discardText: { color: '#C51B2D', fontWeight: '700' },
});
