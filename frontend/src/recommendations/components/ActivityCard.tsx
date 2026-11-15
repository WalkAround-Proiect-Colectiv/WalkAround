import { StyleSheet, Text, View } from 'react-native';
import { Building2, MapPin, Trees } from 'lucide-react-native';
import type { ActivityRecommendationItem } from '../recommendations.types';

const BLUE = '#0065EB';
const PURPLE = '#23012C';
const INK = '#202632';

export interface ActivityCardProps {
  item: ActivityRecommendationItem;
}

export function formatDistance(distanceMeters: number): string {
  if (distanceMeters < 1000) {
    return `${Math.round(distanceMeters)} m`;
  }
  return `${(distanceMeters / 1000).toFixed(1)} km`;
}

export default function ActivityCard({ item }: ActivityCardProps) {
  const distanceLabel = formatDistance(item.distanceMeters);
  const environmentLabel = item.indoor ? 'Indoor' : 'Outdoor';

  return (
    <View
      accessibilityLabel={`${item.name}, ${item.categoryDisplayName}, ${environmentLabel}, ${distanceLabel} away`}
      style={styles.card}
    >
      <View style={styles.headerRow}>
        <View style={styles.iconBox}>
          <MapPin size={19} color={BLUE} />
        </View>
        <View style={styles.titleColumn}>
          <View style={styles.badgesRow}>
            <View style={styles.categoryChip}>
              <Text style={styles.categoryChipText}>{item.categoryDisplayName}</Text>
            </View>
            <View
              style={[
                styles.environmentBadge,
                item.indoor ? styles.indoorBadge : styles.outdoorBadge,
              ]}
            >
              {item.indoor ? (
                <Building2 size={12} color={PURPLE} />
              ) : (
                <Trees size={12} color="#0D6832" />
              )}
              <Text
                style={[
                  styles.environmentBadgeText,
                  item.indoor ? styles.indoorBadgeText : styles.outdoorBadgeText,
                ]}
              >
                {environmentLabel}
              </Text>
            </View>
            <Text style={styles.distance}>{distanceLabel}</Text>
          </View>
          <Text style={styles.name}>{item.name}</Text>
        </View>
      </View>

      <Text style={styles.reason}>{item.reason}</Text>

      {item.address && <Text style={styles.meta}>{item.address}</Text>}
      {item.openingHours && <Text style={styles.meta}>Hours: {item.openingHours}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#E3EAF2',
    borderRadius: 20,
    padding: 16,
    gap: 10,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
  },
  iconBox: {
    width: 40,
    height: 40,
    borderRadius: 13,
    backgroundColor: '#EAF3FF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  titleColumn: {
    flex: 1,
    gap: 6,
  },
  badgesRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  categoryChip: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
    backgroundColor: '#EAF3FF',
  },
  categoryChipText: {
    color: BLUE,
    fontSize: 11,
    fontWeight: '800',
  },
  environmentBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  indoorBadge: {
    backgroundColor: '#F3EBF6',
  },
  outdoorBadge: {
    backgroundColor: '#E6F6EC',
  },
  environmentBadgeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  indoorBadgeText: {
    color: PURPLE,
  },
  outdoorBadgeText: {
    color: '#0D6832',
  },
  distance: {
    marginLeft: 'auto',
    color: '#566276',
    fontSize: 12,
    fontWeight: '700',
  },
  name: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '800',
  },
  reason: {
    color: '#455267',
    fontSize: 13,
    lineHeight: 19,
  },
  meta: {
    color: '#626b78',
    fontSize: 12,
  },
});
