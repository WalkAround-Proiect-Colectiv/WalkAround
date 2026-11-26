import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Bell, ChevronRight, X } from 'lucide-react-native';

interface Props {
  count: number;
  top: number;
  onOpen: () => void;
  onDismiss: () => void;
}

export default function MapInboxNotice({ count, top, onOpen, onDismiss }: Props) {
  const label = `${count} unread notification${count === 1 ? '' : 's'}`;

  return <View style={[styles.notice, { top }]} accessibilityRole="alert">
    <View style={styles.icon}><Bell size={19} color="#0065EB" /></View>
    <Pressable accessibilityRole="button" accessibilityLabel={`Open inbox, ${label}`}
      onPress={onOpen} style={styles.content}>
      <Text style={styles.title}>{label}</Text>
      <View style={styles.link}><Text style={styles.linkText}>View inbox</Text><ChevronRight size={15} color="#0065EB" /></View>
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel="Dismiss inbox reminder"
      onPress={onDismiss} hitSlop={10} style={styles.close}>
      <X size={17} color="#758091" />
    </Pressable>
  </View>;
}

const styles = StyleSheet.create({
  notice: {
    position: 'absolute', left: 70, right: 12, maxWidth: 360,
    zIndex: 120, flexDirection: 'row', alignItems: 'flex-start', gap: 10,
    padding: 12, borderRadius: 16, borderWidth: 1, borderColor: '#C6DCFF',
    backgroundColor: '#fff', elevation: 7, shadowColor: '#14243f',
    shadowOpacity: 0.17, shadowRadius: 12, shadowOffset: { width: 0, height: 4 },
  },
  icon: { width: 34, height: 34, borderRadius: 12, alignItems: 'center',
    justifyContent: 'center', backgroundColor: '#EAF2FF' },
  content: { flex: 1, minHeight: 34 },
  title: { color: '#263241', fontSize: 14, fontWeight: '800' },
  link: { flexDirection: 'row', alignItems: 'center', marginTop: 3 },
  linkText: { color: '#0065EB', fontSize: 12, fontWeight: '700' },
  close: { minWidth: 24, minHeight: 24, alignItems: 'center', justifyContent: 'center' },
});
