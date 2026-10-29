import { useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Link, Redirect } from 'expo-router';
import GoogleAuthButton from '../../auth/components/GoogleAuthButton';
import { environment } from '../../config/environment';
import { signIn } from '../api/session';
import { useSession } from '../components/SessionProvider';

export default function LoginScreen() {
  const { session, ready } = useSession();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pending = useRef(false);
  const configured = Boolean(environment.googleWebClientId && environment.apiBaseUrl);

  async function login(idToken: string) {
    if (pending.current) return;
    pending.current = true;
    setLoading(true);
    setError(null);
    try { await signIn(idToken); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not sign in. Please try again.'); }
    finally { pending.current = false; setLoading(false); }
  }

  if (!ready) return <View style={styles.container}><ActivityIndicator accessibilityLabel="Restoring session" /></View>;
  if (session) return <Redirect href="/home" />;
  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.card}>
        <Text style={styles.brand}>WalkAround</Text>
        <Text accessibilityRole="header" style={styles.title}>Welcome back</Text>
        <Text style={styles.description}>Sign in to continue exploring.</Text>
        <GoogleAuthButton intent="login" disabled={!configured || loading} onCredential={login} onError={setError} />
        {!configured && <Text style={styles.description}>Google sign-in is not available yet. Please check back soon.</Text>}
        {loading && <ActivityIndicator accessibilityLabel="Signing in" />}
        {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.error}>{error}</Text>}
        <Link href="/register" style={styles.link}>New to WalkAround? Create an account</Link>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', padding: 24, backgroundColor: '#f3f7f5' },
  card: { width: '100%', maxWidth: 420, alignItems: 'center', padding: 24, paddingVertical: 36, gap: 24, backgroundColor: '#fff', borderRadius: 20 },
  brand: { fontSize: 20, fontWeight: '700', color: '#246448' },
  title: { fontSize: 30, fontWeight: '700', color: '#17372b', textAlign: 'center' },
  description: { fontSize: 16, lineHeight: 24, color: '#52635c', textAlign: 'center' },
  error: { fontSize: 14, color: '#a32929', textAlign: 'center' },
  link: { color: '#246448', fontWeight: '600', textAlign: 'center' },
});
