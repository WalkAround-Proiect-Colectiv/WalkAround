import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import GoogleAuthButton from '../../auth/components/GoogleAuthButton';
import { Link } from 'expo-router';
import { registerWithGoogle, type RegisteredUser } from '../api/registration';
import { environment } from '../../config/environment';

export default function RegistrationScreen() {
  const [user, setUser] = useState<RegisteredUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const configured = Boolean(environment.googleWebClientId && environment.apiBaseUrl);

  useEffect(() => () => pending.current?.abort(), []);

  async function register(idToken: string) {
    if (pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    const timeout = setTimeout(() => controller.abort(), 15000);
    setLoading(true);
    setError(null);
    try {
      const registered = await registerWithGoogle(environment.apiBaseUrl, idToken, controller.signal);
      setUser(registered);
    } catch (reason) {
      setError(controller.signal.aborted
        ? 'The request timed out. Please check your connection and try again.'
        : reason instanceof TypeError
          ? 'Could not reach WalkAround. Check your connection and try again.'
          : reason instanceof Error ? reason.message : 'Registration failed. Please try again.');
    } finally {
      clearTimeout(timeout);
      pending.current = null;
      setLoading(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <StatusBar style="dark" />
      <View style={styles.card}>
        <Text style={styles.brand}>WalkAround</Text>
        <Text accessibilityRole="header" style={styles.title}>
          {user ? 'Your account is ready' : 'Start your next adventure'}
        </Text>
        <Text style={styles.description}>
          {user
            ? `Welcome${user.firstName ? `, ${user.firstName}` : ''}! Your WalkAround account has been created.`
            : 'Create your account with Google and get ready to discover more around you.'}
        </Text>
        {!user && (
          <>
            <GoogleAuthButton
              disabled={!configured || loading}
              onCredential={register}
              onError={setError}
            />
            {!configured && <Text style={styles.notice}>Google sign-up is not available yet. Please check back soon.</Text>}
            {loading && <View style={styles.progress}><ActivityIndicator /><Text>Creating your account…</Text></View>}
            {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.error}>{error}</Text>}
            <Text style={styles.note}>We use your Google name and photo to get your profile started.</Text>
          </>
        )}
        <Link href="/login" replace style={{ color: '#246448', fontWeight: '600' }}>
          {user ? 'Continue to sign in' : 'Already have an account? Sign in'}
        </Link>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', padding: 24, backgroundColor: '#f3f7f5' },
  card: { width: '100%', maxWidth: 420, alignItems: 'center', paddingVertical: 36, paddingHorizontal: 20, gap: 24, backgroundColor: '#ffffff', borderRadius: 20 },
  brand: { fontSize: 20, fontWeight: '700', color: '#246448' },
  title: { fontSize: 30, fontWeight: '700', textAlign: 'center', color: '#17372b' },
  description: { fontSize: 16, lineHeight: 24, textAlign: 'center', color: '#52635c' },
  note: { fontSize: 13, lineHeight: 20, textAlign: 'center', color: '#66756e' },
  notice: { fontSize: 14, textAlign: 'center', color: '#52635c' },
  error: { fontSize: 14, lineHeight: 20, color: '#a32929', textAlign: 'center' },
  progress: { flexDirection: 'row', alignItems: 'center', gap: 10 },
});
