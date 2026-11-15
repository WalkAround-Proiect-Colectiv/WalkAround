import { StyleSheet, Text, View } from 'react-native';
import { CalendarDays, MapPin } from 'lucide-react-native';
import { formatEventDate } from '../../format';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

export interface EventMetaProps {
  startsAt: string;
  allDay: boolean;
  whenText: string | null;
  venueName: string | null;
  address: string | null;
}

export default function EventMeta({
  startsAt,
  allDay,
  whenText,
  venueName,
  address,
}: EventMetaProps) {
  const formattedDate = formatEventDate(startsAt, allDay);
  const dateDisplay = formattedDate || whenText || '';
  const locationParts = [
    venueName,
    address && address !== venueName ? address : null,
  ].filter((part): part is string => Boolean(part && part.trim().length > 0));
  const locationDisplay = locationParts.join(' · ');

  return (
    <View style={styles.card}>
      {Boolean(dateDisplay) && (
        <View style={styles.row}>
          <CalendarDays size={18} color={BLUE} />
          <View style={styles.textBlock}>
            <Text style={styles.primaryText}>{dateDisplay}</Text>
            {whenText && whenText !== dateDisplay && (
              <Text style={styles.secondaryText}>{whenText}</Text>
            )}
          </View>
        </View>
      )}

      {Boolean(locationDisplay) && (
        <View style={styles.row}>
          <MapPin size={18} color={BLUE} />
          <View style={styles.textBlock}>
            <Text style={styles.primaryText}>{locationDisplay}</Text>
          </View>
        </View>
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
    gap: 12,
  },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  textBlock: { flex: 1, gap: 4 },
  primaryText: { color: INK, fontSize: 14, fontWeight: '600', lineHeight: 20 },
  secondaryText: { color: MUTED, fontSize: 13 },
});
