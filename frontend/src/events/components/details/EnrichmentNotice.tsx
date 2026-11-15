import { StyleSheet, Text, View } from 'react-native';
import { Info } from 'lucide-react-native';
import type { EnrichmentStatus } from '../../api/events';

export interface EnrichmentNoticeProps {
  enrichment: EnrichmentStatus | null;
}

export default function EnrichmentNotice({ enrichment }: EnrichmentNoticeProps) {
  if (enrichment !== 'UNAVAILABLE') {
    return null;
  }

  return (
    <View accessibilityRole="alert" style={styles.notice}>
      <Info size={16} color="#8a5a00" />
      <Text style={styles.text}>Some details couldn&apos;t be loaded right now.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  notice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#fff6e0',
    borderWidth: 1,
    borderColor: '#f5d491',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  text: { flex: 1, color: '#8a5a00', fontSize: 13, fontWeight: '600' },
});
