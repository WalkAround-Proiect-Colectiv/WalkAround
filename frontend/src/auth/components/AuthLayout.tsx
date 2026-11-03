import type { ReactNode } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { Link } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { ArrowLeft, Compass } from 'lucide-react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export const authColors = {
  primary: '#0065EB',
  danger: '#FF0000',
  text: '#363d47',
  muted: '#626b78',
  border: '#dce2e9',
  background: '#f5f7fa',
  surface: '#ffffff',
  blueTint: '#eef5ff',
};

export default function AuthLayout({ children }: { children: ReactNode }) {
  const insets = useSafeAreaInsets();
  return <View style={layout.screen}>
    <StatusBar style="dark" />
    <View style={[layout.header, { paddingTop: Math.max(insets.top, 12) }]}>
      <View style={layout.brand}>
        <Compass size={28} color={authColors.primary} />
        <Text style={layout.brandText}>WalkAround</Text>
      </View>
      <Link href="/map" replace accessibilityLabel="Back to map" style={layout.mapLink}>
        <ArrowLeft size={18} color={authColors.primary} />{' '}Map
      </Link>
    </View>
    <ScrollView contentContainerStyle={[layout.content, { paddingBottom: Math.max(insets.bottom, 24) }]}>
      <View style={authStyles.card}>{children}</View>
    </ScrollView>
  </View>;
}

const layout = StyleSheet.create({
  screen: { flex: 1, backgroundColor: authColors.background },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingBottom: 12, borderBottomWidth: 1, borderColor: authColors.border, backgroundColor: authColors.surface },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  brandText: { fontSize: 22, fontWeight: '700', color: authColors.primary },
  mapLink: { color: authColors.primary, fontSize: 14, fontWeight: '600', padding: 12, minHeight: 44 },
  content: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 16, paddingTop: 24 },
});

export const authStyles = StyleSheet.create({
  card: { width: '100%', maxWidth: 440, alignItems: 'center', paddingVertical: 32, gap: 24, backgroundColor: authColors.surface, borderRadius: 16, borderWidth: 1, borderColor: authColors.border },
  intro: { alignItems: 'center', gap: 12, paddingHorizontal: 24, width: '100%' },
  icon: { width: 64, height: 64, borderRadius: 20, backgroundColor: authColors.blueTint, alignItems: 'center', justifyContent: 'center', marginBottom: 8 },
  title: { fontSize: 28, lineHeight: 36, fontWeight: '700', textAlign: 'center', color: authColors.text },
  description: { fontSize: 16, lineHeight: 24, textAlign: 'center', color: authColors.muted },
  note: { fontSize: 13, lineHeight: 20, textAlign: 'center', color: authColors.muted, paddingHorizontal: 24 },
  error: { fontSize: 14, lineHeight: 21, color: '#a60000', textAlign: 'center', padding: 12, marginHorizontal: 16, borderWidth: 1, borderColor: authColors.danger, borderRadius: 8, backgroundColor: '#fff5f5' },
  progress: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingHorizontal: 16 },
  progressText: { fontSize: 14, color: authColors.muted },
  footer: { alignItems: 'center', width: '100%', borderTopWidth: 1, borderColor: authColors.border, paddingTop: 20, paddingHorizontal: 16, gap: 4 },
  link: { color: authColors.primary, fontSize: 15, lineHeight: 22, fontWeight: '600', textAlign: 'center', padding: 12, minHeight: 44 },
  primaryLink: { color: authColors.surface, backgroundColor: authColors.primary, fontSize: 16, lineHeight: 24, fontWeight: '600', paddingVertical: 12, paddingHorizontal: 24, borderRadius: 8, textAlign: 'center', overflow: 'hidden' },
});
