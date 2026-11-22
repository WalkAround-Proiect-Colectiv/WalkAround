import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Flag, Pencil, Send, Trash2, X, type LucideIcon } from 'lucide-react-native';
import { authenticatedRequest } from '../login/api/session';
import { request } from '../login/api/http';
import { useSession } from '../login/components/SessionProvider';
import type { MapPoi } from '../map/poi';
import { commentError, createCommentsApi, mergeComments, type PoiComment } from './api';

const api = createCommentsApi(request, authenticatedRequest);

function Action({ label, icon: Icon, onPress, disabled = false }: { label: string; icon: LucideIcon; onPress(): void; disabled?: boolean }) {
  const [hovered, setHovered] = useState(false);
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled}
    onHoverIn={() => setHovered(true)} onHoverOut={() => setHovered(false)} onPress={onPress}
    style={[styles.iconButton, disabled && styles.disabled]}>
    <Icon size={18} color="#0065EB" />
    {hovered && <Text style={styles.tooltip}>{label}</Text>}
  </Pressable>;
}

export default function PoiCommentsSheet({ poi, onClose }: { poi: MapPoi; onClose(): void }) {
  return <Modal transparent animationType="slide" onRequestClose={onClose}>
    <SafeAreaProvider><CommentsBody poi={poi} onClose={onClose} /></SafeAreaProvider>
  </Modal>;
}

function CommentsBody({ poi, onClose }: { poi: MapPoi; onClose(): void }) {
  const { session } = useSession();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const generation = useRef(0);
  const [items, setItems] = useState<PoiComment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [pending, setPending] = useState(false);
  const [draft, setDraft] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [editText, setEditText] = useState('');
  const [deleting, setDeleting] = useState<string | null>(null);
  const [reporting, setReporting] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  useEffect(() => {
    const active = ++generation.current;
    api.list(poi.id).then(page => {
      if (generation.current !== active) return;
      setItems(page.items);
      setCursor(page.nextCursor);
    }).catch(problem => {
      if (generation.current === active) setLoadError(commentError(problem));
    }).finally(() => {
      if (generation.current === active) setLoading(false);
    });
    return () => { generation.current += 1; };
  }, [poi.id, attempt]);

  function signIn() {
    onClose();
    router.push('/login');
  }

  async function perform(action: () => Promise<void>) {
    if (pending) return;
    const active = generation.current;
    setPending(true);
    setError(null);
    setNotice(null);
    try { await action(); }
    catch (problem) { if (generation.current === active) setError(commentError(problem)); }
    finally { if (generation.current === active) setPending(false); }
  }

  function addComment() {
    const active = generation.current;
    void perform(async () => {
      const comment = await api.create(poi.id, draft);
      if (generation.current !== active) return;
      setItems(current => [comment, ...current]);
      setDraft('');
    });
  }

  function loadMore() {
    const active = generation.current;
    void perform(async () => {
      const page = await api.list(poi.id, cursor);
      if (generation.current !== active) return;
      setItems(current => mergeComments(current, page.items));
      setCursor(page.nextCursor);
    });
  }

  function renderComment({ item }: { item: PoiComment }) {
    const owned = item.authorId === session?.user.id;
    return <View style={styles.comment}>
      <View style={styles.commentHeader}>
        <View style={styles.authorDetails}>
          <Text style={styles.author}>{item.authorName}</Text>
          <Text style={styles.meta}>{new Date(item.createdAt).toLocaleDateString()}{item.updatedAt !== item.createdAt ? ' (edited)' : ''}</Text>
        </View>
        {owned ? <>
          <Action label="Edit comment" icon={Pencil} disabled={pending} onPress={() => {
            setEditing(item.id); setEditText(item.body); setDeleting(null); setReporting(null);
          }} />
          <Action label="Delete comment" icon={Trash2} disabled={pending} onPress={() => {
            setDeleting(item.id); setEditing(null); setReporting(null);
          }} />
        </> : <Action label="Report comment" icon={Flag} disabled={pending} onPress={() => {
          if (!session) { signIn(); return; }
          setReporting(item.id); setReason(''); setEditing(null); setDeleting(null);
        }} />}
      </View>
      {editing === item.id ? <View style={styles.form}>
        <TextInput accessibilityLabel="Edit comment" value={editText} onChangeText={setEditText} multiline
          maxLength={2000} editable={!pending} style={styles.input} />
        <View style={styles.row}>
          <Pressable accessibilityRole="button" disabled={pending} onPress={() => setEditing(null)} style={styles.secondary}><Text>Cancel</Text></Pressable>
          <Pressable accessibilityRole="button" disabled={pending || !editText.trim()} style={styles.primary} onPress={() => {
            const active = generation.current;
            void perform(async () => {
              const updated = await api.edit(item.id, editText);
              if (generation.current !== active) return;
              setItems(current => current.map(comment => comment.id === item.id ? updated : comment));
              setEditing(null);
            });
          }}><Text style={styles.primaryText}>Save</Text></Pressable>
        </View>
      </View> : <Text style={styles.body}>{item.body}</Text>}
      {deleting === item.id && <View style={styles.form}>
        <Text style={styles.body}>Delete this comment?</Text>
        <View style={styles.row}>
          <Pressable accessibilityRole="button" disabled={pending} style={styles.secondary} onPress={() => setDeleting(null)}><Text>Cancel</Text></Pressable>
          <Pressable accessibilityRole="button" disabled={pending} style={styles.danger} onPress={() => {
            const active = generation.current;
            void perform(async () => {
              await api.remove(item.id);
              if (generation.current !== active) return;
              setItems(current => current.filter(comment => comment.id !== item.id)); setDeleting(null);
            });
          }}><Text style={styles.primaryText}>Delete</Text></Pressable>
        </View>
      </View>}
      {reporting === item.id && <View style={styles.form}>
        <TextInput accessibilityLabel="Report reason" placeholder="Reason for reporting" value={reason} onChangeText={setReason}
          maxLength={500} multiline editable={!pending} style={styles.input} />
        <View style={styles.row}>
          <Pressable accessibilityRole="button" disabled={pending} style={styles.secondary} onPress={() => setReporting(null)}><Text>Cancel</Text></Pressable>
          <Pressable accessibilityRole="button" disabled={pending || !reason.trim()} style={styles.primary} onPress={() => {
            const active = generation.current;
            void perform(async () => {
              await api.report(item.id, reason);
              if (generation.current !== active) return;
              setReporting(null); setNotice('Report submitted.');
            });
          }}><Text style={styles.primaryText}>Submit report</Text></Pressable>
        </View>
      </View>}
    </View>;
  }

  return <KeyboardAvoidingView style={styles.overlay} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
    <Pressable accessibilityLabel="Close comments" onPress={onClose} style={styles.backdrop} />
    <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, 12) }]}>
      <View style={styles.header}>
        <View style={styles.heading}><Text style={styles.title} numberOfLines={2}>{poi.name}</Text><Text style={styles.subtitle}>Comments</Text></View>
        <Action label="Close comments" icon={X} onPress={onClose} />
      </View>
      {loading ? <View style={styles.status}><ActivityIndicator color="#0065EB" accessibilityLabel="Loading comments" /></View>
        : loadError ? <View style={styles.status}>
          <Text accessibilityRole="alert" style={styles.error}>{loadError}</Text>
          <Pressable accessibilityRole="button" style={styles.secondary} onPress={() => {
            setLoading(true); setLoadError(null); setAttempt(value => value + 1);
          }}><Text>Try again</Text></Pressable>
        </View> : <FlatList style={styles.list} data={items} keyExtractor={item => item.id} renderItem={renderComment}
          keyboardShouldPersistTaps="handled" contentContainerStyle={styles.listContent}
          ListEmptyComponent={<Text style={styles.empty}>No comments yet.</Text>}
          ListFooterComponent={cursor ? <Pressable accessibilityRole="button" disabled={pending} style={styles.secondary} onPress={loadMore}>
            <Text>Load more</Text>
          </Pressable> : undefined} />}
      {!!error && <Text accessibilityRole="alert" style={styles.feedback}>{error}</Text>}
      {!!notice && <Text accessibilityLiveRegion="polite" style={styles.feedback}>{notice}</Text>}
      {pending && <ActivityIndicator size="small" color="#0065EB" accessibilityLabel="Updating comments" />}
      {session ? <View style={styles.composer}>
        <TextInput accessibilityLabel="Add a comment" placeholder="Share a tip about this place" value={draft} onChangeText={setDraft}
          multiline maxLength={2000} editable={!pending && !loading && !loadError} style={styles.input} />
        <View style={styles.row}>
          <Text style={styles.meta}>{draft.length}/2000</Text>
          <Pressable accessibilityRole="button" disabled={pending || loading || !!loadError || !draft.trim()}
            onPress={addComment} style={[styles.primary, (pending || loading || !!loadError || !draft.trim()) && styles.disabled]}>
            <Send size={16} color="#fff" /><Text style={styles.primaryText}>Post</Text>
          </Pressable>
        </View>
      </View> : <Pressable accessibilityRole="button" onPress={signIn} style={styles.signIn}><Text style={styles.link}>Sign in to comment</Text></Pressable>}
    </View>
  </KeyboardAvoidingView>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', alignItems: 'center' },
  backdrop: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: { width: '100%', maxWidth: 640, height: '82%', backgroundColor: '#fff', borderTopLeftRadius: 8, borderTopRightRadius: 8 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, borderBottomWidth: 1, borderColor: '#dce2e9' },
  heading: { flex: 1, minWidth: 0, gap: 4 },
  title: { fontSize: 18, fontWeight: '700', color: '#202632' },
  subtitle: { fontSize: 14, color: '#5d6875' },
  iconButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  tooltip: { position: 'absolute', top: 36, right: 0, backgroundColor: '#202632', color: '#fff', padding: 6, fontSize: 12, zIndex: 5, minWidth: 110 },
  list: { flex: 1 },
  listContent: { paddingHorizontal: 16 },
  comment: { paddingVertical: 16, borderBottomWidth: 1, borderColor: '#e5e8ed', gap: 8 },
  commentHeader: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  authorDetails: { flex: 1, minWidth: 0, gap: 4 },
  author: { fontSize: 14, fontWeight: '700', color: '#202632' },
  meta: { fontSize: 12, color: '#5d6875' },
  body: { fontSize: 15, lineHeight: 22, color: '#263241', flexShrink: 1 },
  composer: { paddingHorizontal: 16, paddingTop: 12, borderTopWidth: 1, borderColor: '#dce2e9', gap: 8 },
  input: { borderWidth: 1, borderColor: '#b8c2ce', borderRadius: 6, minHeight: 64, maxHeight: 112, padding: 10, fontSize: 15, color: '#202632', textAlignVertical: 'top' },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'flex-end', gap: 12 },
  form: { gap: 8 },
  primary: { backgroundColor: '#0065EB', borderRadius: 6, minHeight: 40, paddingHorizontal: 16, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center' },
  primaryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
  secondary: { paddingHorizontal: 12, minHeight: 40, justifyContent: 'center', alignItems: 'center' },
  danger: { backgroundColor: '#c52d37', borderRadius: 6, paddingHorizontal: 16, minHeight: 40, justifyContent: 'center' },
  disabled: { opacity: 0.45 },
  status: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 12 },
  empty: { paddingVertical: 28, color: '#5d6875', textAlign: 'center' },
  error: { color: '#ae2633', textAlign: 'center' },
  feedback: { paddingHorizontal: 16, paddingVertical: 8, color: '#394655', fontSize: 14 },
  signIn: { padding: 18, borderTopWidth: 1, borderColor: '#dce2e9', alignItems: 'center' },
  link: { color: '#0065EB', fontWeight: '600', fontSize: 15 },
});