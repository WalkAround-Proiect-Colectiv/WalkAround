import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { Redirect } from 'expo-router';
import { useSession } from '../login/components/SessionProvider';
import MapScreen from '../map/MapScreen';
import { authColors } from '../auth/components/AuthLayout';

export default function MapRoute() {
  const { session, ready } = useSession();
  if (!ready) return <View style={styles.loading}><ActivityIndicator accessibilityLabel="Restoring session" color={authColors.primary} /></View>;
  if (!session) return <Redirect href="/login" />;
  return <MapScreen />;
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: authColors.background },
});
