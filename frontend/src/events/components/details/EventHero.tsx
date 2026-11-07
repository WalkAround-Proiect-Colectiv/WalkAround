import { Image, StyleSheet, Text, View } from 'react-native';
import { CalendarDays, Clock } from 'lucide-react-native';
import { isSafeLink } from '../../format';

const BLUE = '#0065EB';
const INK = '#202632';

export interface EventHeroProps {
  title: string;
  imageUrl: string | null;
  past: boolean;
}

export default function EventHero({ title, imageUrl, past }: EventHeroProps) {
  return (
    <View style={styles.container}>
      {isSafeLink(imageUrl) ? (
        <Image source={{ uri: imageUrl }} style={styles.image} accessibilityIgnoresInvertColors />
      ) : (
        <View style={[styles.image, styles.placeholder]}>
          <CalendarDays size={36} color={BLUE} />
        </View>
      )}
      {past && (
        <View style={styles.pastBadge}>
          <Clock size={14} color="#6b7280" />
          <Text style={styles.pastBadgeText}>Past event</Text>
        </View>
      )}
      <Text style={styles.title}>{title}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 10 },
  image: { width: '100%', height: 190, borderRadius: 16, backgroundColor: '#eef4ff' },
  placeholder: { alignItems: 'center', justifyContent: 'center' },
  pastBadge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 999,
    backgroundColor: '#e5e7eb',
  },
  pastBadgeText: { color: '#374151', fontSize: 12, fontWeight: '700' },
  title: { color: INK, fontSize: 22, fontWeight: '800', lineHeight: 28 },
});
