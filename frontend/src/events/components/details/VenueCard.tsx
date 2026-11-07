import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Building2, ExternalLink } from 'lucide-react-native';
import type { Venue } from '../../api/events';
import { formatRating } from '../../details';
import { isSafeLink } from '../../format';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

export interface VenueCardProps {
  venue: Venue | null;
  onOpenLink(url: string): void;
}

export default function VenueCard({ venue, onOpenLink }: VenueCardProps) {
  if (!venue) {
    return null;
  }
  const ratingLabel = formatRating(venue.rating, venue.reviewCount);
  const safeVenueUrl = isSafeLink(venue.url) ? venue.url : null;

  if (!venue.name && !ratingLabel && !safeVenueUrl) {
    return null;
  }

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <Building2 size={18} color={BLUE} />
        <View style={styles.infoBlock}>
          {venue.name && <Text style={styles.venueName}>{venue.name}</Text>}
          {ratingLabel && <Text style={styles.ratingText}>{ratingLabel}</Text>}
        </View>
      </View>

      {safeVenueUrl && (
        <Pressable
          accessibilityRole="link"
          accessibilityLabel={`Visit ${venue.name || 'venue'} website`}
          onPress={() => onOpenLink(safeVenueUrl)}
          style={styles.venueLink}
        >
          <ExternalLink size={15} color={BLUE} />
          <Text style={styles.venueLinkText}>Venue website</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#e3e8ef',
    padding: 14,
    gap: 10,
  },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  infoBlock: { flex: 1, gap: 2 },
  venueName: { color: INK, fontSize: 15, fontWeight: '700' },
  ratingText: { color: MUTED, fontSize: 13, fontWeight: '600' },
  venueLink: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    alignSelf: 'flex-start',
    paddingVertical: 6,
  },
  venueLinkText: { color: BLUE, fontSize: 14, fontWeight: '700' },
});
