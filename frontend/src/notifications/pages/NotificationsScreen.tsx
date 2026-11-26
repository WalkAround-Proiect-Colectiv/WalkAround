import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, FlatList, Pressable, StyleSheet, Text, View, type FlatListProps } from 'react-native';
import { Redirect, useFocusEffect, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, Bell, ChevronRight } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import { listNotifications, markNotificationRead, type InboxNotification } from '../api/inbox';
import { openNotificationTarget } from '../navigation';
import { subscribeToInboxChanges } from '../liveInbox';

export default function NotificationsScreen() {
  const { session, ready } = useSession();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [items, setItems] = useState<InboxNotification[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const listRef = useRef<FlatList<InboxNotification>>(null);
  const focusedRef = useRef(false);
  const readIdsRef = useRef(new Set<string>());
  const pendingReadsRef = useRef(new Map<string, Promise<void>>());
  const viewabilityConfig = useRef({ itemVisiblePercentThreshold: 50, minimumViewTime: 750 }).current;

  const markReadOnce = useCallback((item: InboxNotification): Promise<void> => {
    if (item.readAt || readIdsRef.current.has(item.id)) return Promise.resolve();

    const pending = pendingReadsRef.current.get(item.id);
    if (pending) return pending;

    const request = markNotificationRead(item.id)
      .then(() => {
        readIdsRef.current.add(item.id);
        setItems(current => current.map(existing => existing.id === item.id
          ? { ...existing, readAt: new Date().toISOString() } : existing));
      })
      .catch(error => {
        setError('Some notifications could not be marked as read. Tap to retry.');
        throw error;
      })
      .finally(() => pendingReadsRef.current.delete(item.id));

    pendingReadsRef.current.set(item.id, request);
    return request;
  }, []);

  const onViewableItemsChanged = useRef<NonNullable<FlatListProps<InboxNotification>['onViewableItemsChanged']>>(
    ({ viewableItems }) => {
      if (!focusedRef.current || AppState.currentState === 'background'
          || AppState.currentState === 'inactive') return;

      viewableItems.forEach(({ item, isViewable }) => {
        if (isViewable && item && !item.readAt) {
          void markReadOnce(item).catch(() => {});
        }
      });
    },
  ).current;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active' && focusedRef.current) listRef.current?.recordInteraction();
    });
    return () => subscription.remove();
  }, []);

  useFocusEffect(useCallback(() => {
    if (!session) return;
    let active = true;
    focusedRef.current = true;
    const unsubscribe = subscribeToInboxChanges(() => setReloadKey(value => value + 1));
    setLoading(true);
    listNotifications()
      .then(page => {
        if (!active) return;
        setItems(page.items);
        setCursor(page.nextCursor);
        setError(null);
      })
      .catch(() => { if (active) setError('Could not load notifications. Try again.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; focusedRef.current = false; unsubscribe(); };
  }, [session?.user.id, reloadKey]));

  if (!ready) return <View style={styles.center}><ActivityIndicator color="#0065EB" /></View>;
  if (!session) return <Redirect href="/login" />;

  async function loadMore() {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listNotifications(cursor);
      setItems(current => [...current, ...page.items.filter(item => !current.some(old => old.id === item.id))]);
      setCursor(page.nextCursor);
      setError(null);
    } catch {
      setError('Could not load more notifications. Try again.');
    } finally {
      setLoadingMore(false);
    }
  }

  async function open(item: InboxNotification) {
    try {
      await markReadOnce(item);
    } catch {
      return;
    }
    if (item.target && !openNotificationTarget(item.target, router)) {
      setError('This destination is not available in this version of the app.');
    }
  }

  return <View style={[styles.screen, { paddingTop: insets.top + 12 }]}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={() => router.back()} style={styles.back}>
        <ArrowLeft size={22} color="#263241" />
      </Pressable>
      <View style={styles.heading}><Text style={styles.title}>Inbox</Text><Text style={styles.subtitle}>Updates worth keeping</Text></View>
      <View style={styles.headerIcon}><Bell size={21} color="#0065EB" /></View>
    </View>
    <FlatList
      ref={listRef}
      data={items}
      keyExtractor={item => item.id}
      viewabilityConfig={viewabilityConfig}
      onViewableItemsChanged={onViewableItemsChanged}
      contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 24 }]}
      ListHeaderComponent={error ? <Pressable accessibilityRole="button" onPress={() => setReloadKey(value => value + 1)}
        style={styles.error}><Text style={styles.errorText}>{error}</Text></Pressable> : null}
      ListEmptyComponent={loading ? <ActivityIndicator color="#0065EB" style={styles.spinner} />
        : error ? null : <View style={styles.empty}>
          <Bell size={32} color="#8B98AA" />
          <Text style={styles.emptyTitle}>All caught up</Text>
          <Text style={styles.emptyText}>Important updates from your trips and other activities will appear here.</Text>
        </View>}
      renderItem={({ item }) => <Pressable accessibilityRole="button"
        accessibilityLabel={`${item.readAt ? '' : 'Unread. '}${item.title}`}
        onPress={() => open(item)} style={[styles.card, !item.readAt && styles.unread]}>
        <View style={styles.cardHeader}>
          <Text style={styles.category}>{item.category.replaceAll('_', ' ')}</Text>
          <Text style={styles.date}>{new Date(item.createdAt).toLocaleDateString()}</Text>
        </View>
        <View style={styles.cardTitleRow}>
          {!item.readAt && <View style={styles.dot} />}
          <Text style={styles.cardTitle}>{item.title}</Text>
        </View>
        <Text style={styles.body}>{item.body}</Text>
        {item.target && <View style={styles.openRow}><Text style={styles.openText}>Open</Text><ChevronRight size={16} color="#0065EB" /></View>}
      </Pressable>}
      ListFooterComponent={cursor ? <Pressable accessibilityRole="button" onPress={loadMore} disabled={loadingMore} style={styles.more}>
        <Text style={styles.moreText}>{loadingMore ? 'Loading...' : 'Show more'}</Text>
      </Pressable> : null}
    />
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F6F8FB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, paddingBottom: 18, gap: 12 },
  back: { width: 44, height: 44, borderRadius: 15, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' },
  heading: { flex: 1 },
  title: { color: '#172234', fontSize: 26, fontWeight: '800' },
  subtitle: { color: '#718096', fontSize: 13, marginTop: 2 },
  headerIcon: { width: 44, height: 44, borderRadius: 15, backgroundColor: '#EAF2FF', alignItems: 'center', justifyContent: 'center' },
  list: { paddingHorizontal: 18, gap: 12 },
  spinner: { marginTop: 32 },
  error: { backgroundColor: '#FFF0EF', padding: 12, borderRadius: 12 },
  errorText: { color: '#B42318' },
  empty: { alignItems: 'center', paddingHorizontal: 28, paddingVertical: 70, gap: 9 },
  emptyTitle: { color: '#263241', fontSize: 19, fontWeight: '800' },
  emptyText: { color: '#718096', textAlign: 'center', lineHeight: 21 },
  card: { backgroundColor: '#fff', borderRadius: 18, padding: 16, borderWidth: 1, borderColor: '#E6EAF0' },
  unread: { borderColor: '#B9D5FF', backgroundColor: '#FDFEFF' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  category: { color: '#0065EB', fontSize: 11, fontWeight: '800', letterSpacing: 0.7, textTransform: 'uppercase' },
  date: { color: '#8290A1', fontSize: 12 },
  cardTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 10 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#0065EB' },
  cardTitle: { flex: 1, color: '#202C3C', fontSize: 16, fontWeight: '800' },
  body: { color: '#586779', lineHeight: 20, marginTop: 7 },
  openRow: { flexDirection: 'row', alignItems: 'center', marginTop: 12, gap: 2 },
  openText: { color: '#0065EB', fontWeight: '700' },
  more: { alignItems: 'center', padding: 14 },
  moreText: { color: '#0065EB', fontWeight: '700' },
});
