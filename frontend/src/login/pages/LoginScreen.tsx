import { useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { LogIn } from 'lucide-react-native';
import AuthLayout, { authColors, authStyles as styles } from '../../auth/components/AuthLayout';
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

  if (!ready) return <AuthLayout><ActivityIndicator color={authColors.primary} accessibilityLabel="Restoring session" /><Text style={styles.description}>Getting your account ready…</Text></AuthLayout>;
  if (session) return <Redirect href="/home" />;
  return (
    <AuthLayout>
      <View style={styles.intro}>
        <View style={styles.icon}><LogIn size={30} color={authColors.primary} /></View>
        <Text accessibilityRole="header" style={styles.title}>Welcome back</Text>
        <Text style={styles.description}>Sign in to continue exploring.</Text>
      </View>
      <GoogleAuthButton intent="login" disabled={!configured || loading} onCredential={login} onError={setError} />
      {!configured && <Text style={styles.note}>Google sign-in is not available yet. Please check back soon.</Text>}
      {loading && <View style={styles.progress}><ActivityIndicator color={authColors.primary} /><Text style={styles.progressText}>Signing in…</Text></View>}
      {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.error}>{error}</Text>}
      <View style={styles.footer}>
        <Text style={styles.description}>New to WalkAround?</Text>
        <Link href="/register" replace style={styles.link}>Create an account</Link>
      </View>
    </AuthLayout>
  );
}

