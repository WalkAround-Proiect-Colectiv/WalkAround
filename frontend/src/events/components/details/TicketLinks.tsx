import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ExternalLink, Ticket } from 'lucide-react-native';
import type { TicketLinkGroups } from '../../details';
import { isSafeLink } from '../../format';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

export interface TicketLinksProps {
  ticketGroups: TicketLinkGroups;
  eventUrl: string | null;
  venueUrl: string | null;
  onOpenLink(url: string): void;
}

export default function TicketLinks({
  ticketGroups,
  eventUrl,
  venueUrl,
  onOpenLink,
}: TicketLinksProps) {
  const { buy, info } = ticketGroups;
  const fallbackUrl = isSafeLink(venueUrl)
    ? venueUrl
    : isSafeLink(eventUrl)
      ? eventUrl
      : null;

  return (
    <View style={styles.card}>
      <Text style={styles.heading}>Tickets & links</Text>

      {buy.map(ticket => {
        const label = `Buy tickets${ticket.source ? ` · ${ticket.source}` : ''}`;
        return (
          <Pressable
            key={ticket.url}
            accessibilityRole="link"
            accessibilityLabel={label}
            onPress={() => onOpenLink(ticket.url)}
            style={styles.buyButton}
          >
            <Ticket size={18} color="#fff" />
            <Text style={styles.buyButtonText}>{label}</Text>
            <ExternalLink size={16} color="#fff" />
          </Pressable>
        );
      })}

      {info.map(ticket => {
        const label = `More info${ticket.source ? ` · ${ticket.source}` : ''}`;
        return (
          <Pressable
            key={ticket.url}
            accessibilityRole="link"
            accessibilityLabel={label}
            onPress={() => onOpenLink(ticket.url)}
            style={styles.infoRow}
          >
            <ExternalLink size={16} color={BLUE} />
            <Text style={styles.infoRowText}>{label}</Text>
          </Pressable>
        );
      })}

      {buy.length === 0 && info.length === 0 && (
        <View style={styles.emptyBlock}>
          <Text style={styles.emptyText}>
            No ticket links found. Check the venue&apos;s website.
          </Text>
          {fallbackUrl && (
            <Pressable
              accessibilityRole="link"
              accessibilityLabel="Open event or venue website"
              onPress={() => onOpenLink(fallbackUrl)}
              style={styles.infoRow}
            >
              <ExternalLink size={16} color={BLUE} />
              <Text style={styles.infoRowText}>Visit official page</Text>
            </Pressable>
          )}
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
    gap: 10,
  },
  heading: { color: INK, fontSize: 15, fontWeight: '700' },
  buyButton: {
    minHeight: 46,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: BLUE,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 11,
  },
  buyButtonText: { color: '#fff', fontSize: 15, fontWeight: '700', flexShrink: 1 },
  infoRow: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#d5dce6',
    backgroundColor: '#f8fafc',
  },
  infoRowText: { color: BLUE, fontSize: 14, fontWeight: '600', flexShrink: 1 },
  emptyBlock: { gap: 8 },
  emptyText: { color: MUTED, fontSize: 14, lineHeight: 20 },
});
