import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Redirect } from 'expo-router';
import { useSession } from '../components/SessionProvider';
import { getCurrentUser, signOut } from '../api/session';
import type { RegisteredUser } from '../../register/api/registration';

export default function AccountScreen() {
  const { session, ready } = useSession();
  const [profile, setProfile] = useState<RegisteredUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const userId = session?.user.id;
  useEffect(() => {
    if (!userId) return;
    let active = true;
    getCurrentUser().then(user => { if (active) setProfile(user); })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'Could not load your account.'); });
    return () => { active = false; };
  }, [userId]);

  async function logout() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try { await signOut(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not sign out. Please try again.'); }
    finally { setBusy(false); }
  }

  if (!ready) return <ActivityIndicator />;
  if (!session) return <Redirect href="/login" />;
  const user = profile ?? session.user;
  return <View style={styles.container}>
    <Text style={styles.brand}>WalkAround</Text>
    <Text accessibilityRole="header" style={styles.title}>Welcome{user.firstName ? ', ' + user.firstName : ''}</Text>
    <Text style={styles.description}>You are signed in.</Text>
    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
    <Pressable onPress={logout} disabled={busy} accessibilityRole="button" style={styles.button}>
      <Text style={styles.buttonText}>{busy ? 'Signing out…' : 'Sign out'}</Text>
    </Pressable>
  </View>;
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24, gap: 24, backgroundColor: '#f3f7f5' },
  brand: { fontSize: 20, fontWeight: '700', color: '#246448' },
  title: { fontSize: 30, fontWeight: '700', color: '#17372b', textAlign: 'center' },
  description: { fontSize: 16, color: '#52635c' },
  error: { fontSize: 14, color: '#a32929', textAlign: 'center' },
  button: { padding: 16, borderRadius: 12, backgroundColor: '#246448' },
  buttonText: { color: '#fff', fontWeight: '600' },
});
