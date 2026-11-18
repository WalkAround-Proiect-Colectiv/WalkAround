import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, MessageCircle, Send } from 'lucide-react-native';
import * as Crypto from 'expo-crypto';
import { useSession } from '../../login/components/SessionProvider';
import GroupPicture from '../components/GroupPicture';
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
    void send(item);
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
    style={[styles.screen, { paddingTop: Math.max(insets.top, 12), paddingBottom: Math.max(insets.bottom, 12) }]}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel="Back to group"
        onPress={() => router.replace({ pathname: '/trip-groups', params: { groupId: group.id } })}
        style={styles.back}><ArrowLeft size={20} color="#202632" /></Pressable>
      {group.pictureUrl ? <GroupPicture groupId={group.id} size={38} />
        : <View style={styles.icon}><MessageCircle size={19} color={BLUE} /></View>}
      <View style={styles.heading}><Text style={styles.title} numberOfLines={1}>{group.name}</Text>
        <Text style={styles.status}>{connection} · Group chat</Text></View>
    </View>

    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
    <ScrollView ref={listRef} style={styles.messages} contentContainerStyle={styles.messageContent}
      onContentSizeChange={() => { if (!loadingOlderRef.current) listRef.current?.scrollToEnd({ animated: true }); }}>
      {hasOlder && <Pressable accessibilityRole="button" disabled={loadingOlder} onPress={() => { void loadOlder(); }}
        style={styles.older}><Text style={styles.olderText}>{loadingOlder ? 'Loading…' : 'Load earlier messages'}</Text></Pressable>}
      {loading && <ActivityIndicator color={BLUE} />}
      {!loading && messages.length === 0 && <Text style={styles.empty}>No messages yet. Start the conversation.</Text>}
      {messages.map((message) => {
        const own = message.senderId === session?.user.id;
        return <View key={message.id} style={[styles.bubble, own ? styles.ownBubble : styles.otherBubble]}>
          {!own && <Text style={styles.sender}>{message.senderName}</Text>}
          <Text style={own ? styles.ownText : styles.messageText}>{message.body}</Text>
          <Text style={own ? styles.ownTime : styles.time}>
            {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </Text>
        </View>;
      })}
      {pending && <View style={[styles.bubble, styles.ownBubble]}>
        <Text style={styles.ownText}>{pending.body}</Text>
        <Text style={styles.ownTime}>{sending ? 'Sending…' : 'Not confirmed'}</Text>
      </View>}
    </ScrollView>

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
      <TextInput accessibilityLabel="Group message" multiline maxLength={2000}
        placeholder="Message your group" placeholderTextColor="#8A96A6"
        style={styles.input} value={draft} onChangeText={setDraft} />
      <Pressable accessibilityRole="button" accessibilityLabel="Send message"
        disabled={sending || Boolean(pending) || !draft.trim()} onPress={submit}
        style={[styles.sendButton, (sending || Boolean(pending) || !draft.trim()) && styles.disabled]}>
        <Send size={19} color="#fff" />
      </Pressable>
    </View>
  </KeyboardAvoidingView>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4F7FB' },
  header: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14, paddingBottom: 12,
    borderBottomWidth: 1, borderBottomColor: '#E3EAF3', backgroundColor: '#fff' },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F2F6FB' },
  icon: { width: 38, height: 38, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: '#EAF3FF' },
  heading: { flex: 1 },
  title: { color: '#202632', fontSize: 16, fontWeight: '800' },
  status: { color: '#718096', fontSize: 12 },
  messages: { flex: 1 },
  messageContent: { padding: 16, gap: 10, flexGrow: 1, justifyContent: 'flex-end' },
  bubble: { maxWidth: '82%', borderRadius: 17, paddingHorizontal: 13, paddingVertical: 9, gap: 3 },
  ownBubble: { alignSelf: 'flex-end', backgroundColor: BLUE, borderBottomRightRadius: 5 },
  otherBubble: { alignSelf: 'flex-start', backgroundColor: '#fff', borderWidth: 1, borderColor: '#E4EAF1', borderBottomLeftRadius: 5 },
  sender: { color: '#23012C', fontWeight: '700', fontSize: 12 },
  ownText: { color: '#fff', fontSize: 14, lineHeight: 20 },
  messageText: { color: '#202632', fontSize: 14, lineHeight: 20 },
  ownTime: { color: '#DCEAFF', fontSize: 10, alignSelf: 'flex-end' },
  time: { color: '#8290A2', fontSize: 10, alignSelf: 'flex-end' },
  empty: { color: '#718096', textAlign: 'center', marginTop: 36 },
  older: { alignSelf: 'center', padding: 8 },
  olderText: { color: BLUE, fontWeight: '700', fontSize: 13 },
  composer: { flexDirection: 'row', alignItems: 'flex-end', gap: 9, padding: 12, backgroundColor: '#fff',
    borderTopWidth: 1, borderTopColor: '#E3EAF3' },
  input: { flex: 1, maxHeight: 110, minHeight: 44, borderRadius: 17, backgroundColor: '#F2F5F9',
    color: '#202632', paddingHorizontal: 14, paddingVertical: 11, fontSize: 14 },
  sendButton: { width: 44, height: 44, borderRadius: 15, backgroundColor: BLUE,
    alignItems: 'center', justifyContent: 'center' },
  disabled: { opacity: 0.5 },
  error: { color: '#C51B2D', fontSize: 12, paddingHorizontal: 16, paddingTop: 8 },
  retryRow: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 8 },
  retryText: { color: BLUE, fontWeight: '700' },
  discardText: { color: '#C51B2D', fontWeight: '700' },
});
