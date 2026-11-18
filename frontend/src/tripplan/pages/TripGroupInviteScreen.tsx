import { useEffect, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import * as Linking from 'expo-linking';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, UsersRound } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import { acceptGroupInvite, previewGroupInvite, type TripGroupInvitePreview } from '../api/tripGroups';
import { clearPendingGroupInvite, readPendingGroupInvite, savePendingGroupInvite } from '../api/pendingGroupInvite';
import GroupPicture from '../components/GroupPicture';

const BLUE = '#0065EB';

function tokenFromUrl(url: string | null): string | null {
  return /#token=([A-Za-z0-9_-]{43})(?:&|$)/.exec(url ?? '')?.[1] ?? null;
}

export default function TripGroupInviteScreen() {
  const { session, ready } = useSession();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const url = Linking.useURL();
  const [token, setToken] = useState<string | null>(null);
  const [preview, setPreview] = useState<TripGroupInvitePreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    let active = true;
    async function loadToken() {
      try {
        const fromLink = tokenFromUrl(url)
          ?? (Platform.OS === 'web' ? tokenFromUrl(window.location.href) : null);
        if (fromLink) {
          await savePendingGroupInvite(fromLink);
          if (Platform.OS === 'web') {
            window.history.replaceState(null, '', window.location.pathname + window.location.search);
          }
        }
        const found = fromLink ?? await readPendingGroupInvite();
        if (!active) return;
        setToken(found);
        setLoading(false);
        if (found && !session) router.replace('/login');
      } catch {
        if (active) {
          setError('Could not open this invitation. Try the link again.');
          setLoading(false);
        }
      }
    }
    void loadToken();
    return () => { active = false; };
  }, [ready, session, url, router]);

  useEffect(() => {
    if (!session || !token) return;
    let active = true;
    setLoading(true);
    void previewGroupInvite(token)
      .then((result) => { if (active) setPreview(result); })
      .catch((reason) => {
        if (active) {
          setError(reason instanceof Error ? reason.message : 'Invitation is unavailable.');
          void clearPendingGroupInvite();
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [session, token]);

  async function join() {
    if (!token || busy) return;
    setBusy(true);
    setError(null);
    try {
      const group = await acceptGroupInvite(token);
      await clearPendingGroupInvite();
      router.replace({ pathname: '/trip-groups', params: { groupId: group.id } });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not join the group.');
    } finally {
      setBusy(false);
    }
  }

  async function leave() {
    await clearPendingGroupInvite();
    router.replace('/trip-plan');
  }

  return <View style={styles.screen}>
    <ScrollView contentContainerStyle={[styles.content, {
      paddingTop: Math.max(insets.top, 18), paddingBottom: Math.max(insets.bottom, 24) + 24,
    }]}>
      <Pressable accessibilityRole="button" accessibilityLabel="Back to trip planner"
        onPress={() => { void leave(); }} style={styles.back}>
        <ArrowLeft size={20} color="#202632" />
      </Pressable>
      <View style={styles.card}>
        {preview?.pictureAvailable && token ? <GroupPicture inviteToken={token} size={70} />
          : <View style={styles.icon}><UsersRound size={27} color={BLUE} /></View>}
        <Text accessibilityRole="header" style={styles.title}>Group invitation</Text>
        {loading ? <ActivityIndicator color={BLUE} /> : preview ? <>
          <Text style={styles.body}>You have been invited to join</Text>
          <Text style={styles.groupName}>{preview.groupName}</Text>
          <Text style={styles.hint}>This invitation expires {new Date(preview.expiresAt).toLocaleString()}.</Text>
          <Pressable accessibilityRole="button" disabled={busy} onPress={() => { void join(); }}
            style={[styles.button, busy && styles.disabled]}>
            {busy ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Join group</Text>}
          </Pressable>
        </> : <Text style={styles.body}>This invitation is unavailable.</Text>}
        {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
      </View>
    </ScrollView>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4F7FB' },
  content: { paddingHorizontal: 16, width: '100%', maxWidth: 560, alignSelf: 'center', gap: 20 },
  back: { width: 44, height: 44, borderRadius: 15, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' },
  card: { backgroundColor: '#fff', borderRadius: 24, padding: 24, alignItems: 'center', gap: 14 },
  icon: { width: 58, height: 58, borderRadius: 20, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  title: { color: '#202632', fontSize: 25, fontWeight: '800', textAlign: 'center' },
  body: { color: '#596679', fontSize: 15, textAlign: 'center' },
  groupName: { color: '#202632', fontSize: 21, fontWeight: '800', textAlign: 'center' },
  hint: { color: '#798494', fontSize: 12, textAlign: 'center' },
  button: { backgroundColor: BLUE, minHeight: 52, alignSelf: 'stretch', borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 15 },
  disabled: { opacity: 0.5 },
  error: { color: '#C51B2D', fontSize: 13, lineHeight: 19, textAlign: 'center' },
});
