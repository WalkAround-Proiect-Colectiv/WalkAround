import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { Check, UserRoundPlus } from 'lucide-react-native';
import AuthLayout, { authColors, authStyles as styles } from '../../auth/components/AuthLayout';
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
    <AuthLayout>
      <View style={styles.intro}>
        <View style={styles.icon}>
          {user ? <Check size={30} color={authColors.primary} /> : <UserRoundPlus size={30} color={authColors.primary} />}
        </View>
        <Text accessibilityRole="header" style={styles.title}>
          {user ? 'Your account is ready' : 'Start your next adventure'}
        </Text>
        <Text style={styles.description}>
          {user
            ? `Welcome${user.firstName ? `, ${user.firstName}` : ''}! Your WalkAround account has been created.`
            : 'Create your account with Google and get ready to discover more around you.'}
        </Text>
      </View>
      {!user && (
        <>
          <GoogleAuthButton
            intent="register"
            disabled={!configured || loading}
            onCredential={register}
            onError={setError}
          />
          {!configured && <Text style={styles.note}>Google sign-up is not available yet. Please check back soon.</Text>}
          {loading && <View style={styles.progress}><ActivityIndicator color={authColors.primary} /><Text style={styles.progressText}>Creating your account…</Text></View>}
          {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.error}>{error}</Text>}
          <Text style={styles.note}>We use your Google name and photo to get your profile started.</Text>
        </>
      )}
      <View style={styles.footer}>
        {!user && <Text style={styles.description}>Already have an account?</Text>}
        <Link href="/login" replace style={user ? styles.primaryLink : styles.link}>
          {user ? 'Continue to sign in' : 'Sign in'}
        </Link>
      </View>
    </AuthLayout>
  );
}
