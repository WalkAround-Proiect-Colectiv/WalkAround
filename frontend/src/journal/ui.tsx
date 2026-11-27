import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Redirect, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, BookOpen, type LucideIcon } from 'lucide-react-native';
import { useSession } from '../login/components/SessionProvider';

export function JournalPage({ title, children, root = false }: { title: string; children: ReactNode; root?: boolean }) {
  const { session, ready } = useSession();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  if (!ready) return <ActivityIndicator accessibilityLabel="Restoring session" />;
  if (!session) return <Redirect href="/login" />;
  return <View style={[styles.page, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel={root ? 'Back to map' : 'Back to journal'} style={styles.iconButton}
        onPress={() => router.replace(root ? '/map' : '/journal')}><ArrowLeft size={22} color="#0065EB" /></Pressable>
      <BookOpen size={22} color="#0065EB" /><Text accessibilityRole="header" style={styles.title}>{title}</Text>
    </View>
    <View key={session.user.id} style={styles.fill}>{children}</View>
  </View>;
}

export function Command({ label, icon: Icon, onPress, disabled = false, primary = false, danger = false }: {
  label: string; icon: LucideIcon; onPress(): void; disabled?: boolean; primary?: boolean; danger?: boolean;
}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress}
    style={[styles.command, primary && styles.primary, danger && styles.danger, disabled && styles.dim]}>
    <Icon size={18} color={primary || danger ? '#fff' : '#0065EB'} />
    <Text style={[styles.commandText, (primary || danger) && styles.white]}>{label}</Text>
  </Pressable>;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <View style={styles.field}><Text style={styles.label}>{label}</Text>{children}</View>;
}

export const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: '#f5f7fa' }, fill: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, borderBottomWidth: 1, borderColor: '#dce2e9', backgroundColor: '#fff' },
  title: { fontSize: 21, fontWeight: '700', color: '#202632', flex: 1 },
  iconButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  content: { width: '100%', maxWidth: 820, alignSelf: 'center', paddingHorizontal: 20, paddingVertical: 24, paddingBottom: 48, gap: 20 },
  heading: { color: '#202632', fontSize: 20, fontWeight: '700', flexShrink: 1 },
  text: { color: '#354257', fontSize: 15, lineHeight: 23 },
  muted: { color: '#637084', fontSize: 13, lineHeight: 20 },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 12 },
  between: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  command: { minHeight: 44, borderRadius: 6, paddingHorizontal: 14, paddingVertical: 10, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', backgroundColor: '#e8eef6' },
  primary: { backgroundColor: '#0065EB' }, danger: { backgroundColor: '#bd2938' },
  commandText: { color: '#0053c5', fontSize: 14, fontWeight: '600', flexShrink: 1 }, white: { color: '#fff' },
  dim: { opacity: 0.45 }, field: { gap: 8 }, label: { fontSize: 14, fontWeight: '600', color: '#263447' },
  input: { borderWidth: 1, borderColor: '#b7c2d0', borderRadius: 6, backgroundColor: '#fff', paddingHorizontal: 12, paddingVertical: 12, minHeight: 46, color: '#202632', fontSize: 15 },
  description: { minHeight: 112, textAlignVertical: 'top' },
  section: { paddingVertical: 16, borderBottomWidth: 1, borderColor: '#dce2e9', gap: 10 },
  choice: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 46, paddingVertical: 10 },
  choiceText: { fontSize: 14, color: '#263447', flex: 1 },
  dot: { width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: '#8999ad' },
  selectedDot: { borderColor: '#0065EB', borderWidth: 6 },
  error: { color: '#af2334', fontSize: 14, lineHeight: 21 },
  dates: { flexDirection: 'row', flexWrap: 'wrap', gap: 16 }, dateField: { flexGrow: 1, flexBasis: 240, gap: 8 },
  dateButton: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  status: { alignItems: 'center', paddingVertical: 40, gap: 16 },
  tripRow: { paddingVertical: 18, borderBottomWidth: 1, borderColor: '#dce2e9', gap: 8 },
  suggestions: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#dce2e9', borderRadius: 6 },
  suggestion: { padding: 12, borderBottomWidth: 1, borderColor: '#edf0f4' },
  warning: { borderLeftWidth: 3, borderColor: '#bd2938', padding: 16, gap: 14, backgroundColor: '#fff' },
});