import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Link, Redirect } from 'expo-router';
import { LogOut, UserRound } from 'lucide-react-native';
import AuthLayout, { authColors, authStyles } from '../../auth/components/AuthLayout';
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

  if (!ready) return <AuthLayout><ActivityIndicator color={authColors.primary} accessibilityLabel="Loading account" /></AuthLayout>;
  if (!session) return <Redirect href="/login" />;
  const user = profile ?? session.user;
  return <AuthLayout>
    <View style={authStyles.intro}>
      <View style={authStyles.icon}><UserRound size={30} color={authColors.primary} /></View>
      <Text accessibilityRole="header" style={authStyles.title}>Welcome{user.firstName ? ', ' + user.firstName : ''}</Text>
      <Text style={authStyles.description}>You’re signed in and ready to explore.</Text>
    </View>
    <Link href="/map" replace style={authStyles.primaryLink}>Explore the map</Link>
    {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={authStyles.error}>{error}</Text>}
    <View style={authStyles.footer}>
      <Pressable onPress={logout} disabled={busy} accessibilityRole="button" accessibilityLabel={busy ? 'Signing out' : 'Sign out'} accessibilityState={{ disabled: busy, busy }}
        style={({ pressed }) => [styles.button, pressed && styles.pressed, busy && styles.disabled]}>
        {busy ? <ActivityIndicator color={authColors.danger} /> : <LogOut size={20} color={authColors.danger} />}
        <Text style={styles.buttonText}>{busy ? 'Signing out…' : 'Sign out'}</Text>
      </Pressable>
    </View>
  </AuthLayout>;
}

const styles = StyleSheet.create({
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, minHeight: 48, paddingVertical: 12, paddingHorizontal: 24, borderRadius: 8, borderWidth: 1, borderColor: authColors.danger, backgroundColor: authColors.surface },
  buttonText: { color: '#a60000', fontSize: 16, lineHeight: 24, fontWeight: '600' },
  pressed: { backgroundColor: '#fff0f0' },
  disabled: { opacity: 0.6 },
});
