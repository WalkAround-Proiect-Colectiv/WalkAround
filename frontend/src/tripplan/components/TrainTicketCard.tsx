import { useState } from 'react';
import { Image, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { ExternalLink, TrainFront } from 'lucide-react-native';
import type { TrainItinerary, TrainJourney, TrainStop } from '../api/tripPlanning';

const BLUE = '#0065EB';
const PURPLE = '#23012C';
const OMIO_SEARCH_URL = 'https://www.omio.com/links/626fa8a9-f982-43d0-ace9-9a13f6b14612';

function time(value: string | null): string {
  return value ? value.slice(0, 16).replace('T', ' at ') : 'Time unavailable';
}

function duration(minutes: number | null): string | null {
  return minutes == null ? null : `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

function stopLabel(stop: TrainStop | null): string | null {
  if (!stop?.name && !stop?.city) return null;
  return [stop.name, stop.city, stop.countryCode].filter(Boolean).join(', ');
}

function omioSearchUrl(journey: TrainJourney, returnDate?: string): string | null {
  const origin = journey.departureCity || journey.departureStation;
  const destination = journey.arrivalCity || journey.arrivalStation;
  const departureDate = journey.departureTime.slice(0, 10);
  if (!origin || !destination || !/^\d{4}-\d{2}-\d{2}$/.test(departureDate)) return null;

  const params = [
    `departurePosTerm=${encodeURIComponent(origin)}`,
    `arrivalPosTerm=${encodeURIComponent(destination)}`,
    `departureDate=${departureDate}`,
    'travelMode=TRAIN',
    'currency=EUR',
  ];
  if (returnDate && /^\d{4}-\d{2}-\d{2}$/.test(returnDate)) {
    params.push(`returnDate=${returnDate}`);
  }
  return `${OMIO_SEARCH_URL}?${params.join('&')}`;
}

function CarrierIcon({ journey }: { journey: TrainJourney }) {
  const [failed, setFailed] = useState(false);
  const url = journey.carrierLogoUrl;
  return <View style={styles.icon}>
    {url?.startsWith('https://static.omio.design/') && !failed
      ? <Image source={{ uri: url }} onError={() => setFailed(true)} resizeMode="contain" style={styles.logo} />
      : <TrainFront size={19} color={BLUE} />}
  </View>;
}

function Journey({ title, journey }: { title: string; journey: TrainJourney }) {
  const departurePlace = [journey.departureCity, journey.departureCountryCode].filter(Boolean).join(', ');
  const arrivalPlace = [journey.arrivalCity, journey.arrivalCountryCode].filter(Boolean).join(', ');

  return <View style={styles.journey}>
    <Text style={styles.label}>{title}</Text>
    <View style={styles.carrierRow}>
      <CarrierIcon journey={journey} />
      <Text style={styles.carrier}>{journey.carrier || 'Rail operator unavailable'}</Text>
    </View>
    <Text style={styles.route}>{journey.departureStation || 'Departure station'} to {journey.arrivalStation || 'Arrival station'}</Text>
    {(departurePlace || arrivalPlace) && <Text style={styles.detail}>{departurePlace || 'Origin'} to {arrivalPlace || 'Destination'}</Text>}
    <Text style={styles.detail}>{time(journey.departureTime)} to {time(journey.arrivalTime)}</Text>
    <Text style={styles.detail}>{[
      duration(journey.durationMinutes),
      journey.stops == null ? null : `${journey.stops} change${journey.stops === 1 ? '' : 's'}`,
    ].filter(Boolean).join(' | ')}</Text>
    {journey.ticketsLeft != null && <Text style={styles.availability}>
      {journey.ticketsLeft} ticket{journey.ticketsLeft === 1 ? '' : 's'} left at search time
    </Text>}
    {journey.splitTicket && <Text style={styles.note}>This journey uses separate tickets for its legs.</Text>}
    {journey.legs.map((leg, index) => <View key={`${title}-leg-${index}`} style={styles.leg}>
      <Text style={styles.legTitle}>
        {index + 1}. {[leg.mode?.toUpperCase(), leg.company, leg.lineNumber].filter(Boolean).join(' | ') || 'Connection'}
      </Text>
      <Text style={styles.detail}>{time(leg.departureTime)} to {time(leg.arrivalTime)}</Text>
      {(leg.departureZone || leg.arrivalZone || leg.durationMinutes != null) && <Text style={styles.detail}>
        {[duration(leg.durationMinutes), leg.departureZone && leg.arrivalZone && leg.departureZone !== leg.arrivalZone
          ? `${leg.departureZone} to ${leg.arrivalZone}` : leg.departureZone || leg.arrivalZone].filter(Boolean).join(' | ')}
      </Text>}
      {journey.transfers.filter((transfer) => transfer.afterLeg === index).map((transfer, transferIndex) => {
        const from = stopLabel(transfer.fromStop);
        const to = stopLabel(transfer.toStop);
        const sameStop = transfer.fromStop?.positionId === transfer.toStop?.positionId;
        return <View key={`${title}-transfer-${index}-${transferIndex}`}>
          <Text style={styles.transfer}>{transfer.type || 'Transfer'}
            {transfer.durationMinutes != null ? ` | ${duration(transfer.durationMinutes)}` : ''}
          </Text>
          {(from || to) && <Text style={styles.detail}>
            {sameStop ? from || to : `${from || 'Unknown stop'} to ${to || 'Unknown stop'}`}
          </Text>}
        </View>;
      })}
    </View>)}
  </View>;
}

export default function TrainTicketCard({ itinerary, index, selected, onSelect }: {
  itinerary: TrainItinerary;
  index: number;
  selected: boolean;
  onSelect(): void;
}) {
  const [linkError, setLinkError] = useState(false);
  const price = new Intl.NumberFormat('en-US', { style: 'currency', currency: itinerary.currency || 'EUR' })
    .format(itinerary.totalPrice);
  const outboundUrl = omioSearchUrl(itinerary.outbound,
    itinerary.ticketOption === 'ROUND_TRIP' ? itinerary.returning.departureTime.slice(0, 10) : undefined);
  const returnUrl = itinerary.ticketOption === 'TWO_ONE_WAY' ? omioSearchUrl(itinerary.returning) : null;

  async function openOmio(url: string) {
    try {
      await Linking.openURL(url);
      setLinkError(false);
    } catch {
      setLinkError(true);
    }
  }

  return <View style={styles.card}>
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: selected }}
      accessibilityLabel={`${selected ? 'Hide' : 'Show'} train option ${index + 1}, ${price}`}
      onPress={onSelect} style={styles.header}>
      <CarrierIcon journey={itinerary.outbound} />
      <View style={styles.heading}>
        <Text style={styles.label}>TRAIN OPTION {index + 1} | {itinerary.ticketOption === 'ROUND_TRIP' ? 'ROUND TRIP' : 'TWO ONE-WAY'}</Text>
        <Text style={styles.route}>{itinerary.outbound.departureStation || 'Origin'} to {itinerary.outbound.arrivalStation || 'Destination'}</Text>
        <Text style={styles.detail}>{itinerary.outbound.carrier || 'Rail operator'} | Outbound {time(itinerary.outbound.departureTime)}</Text>
        <Text style={styles.detail}>Return {time(itinerary.returning.departureTime)} | {itinerary.returning.departureStation || 'Departure station'}</Text>
      </View>
      <Text style={styles.price}>{price}</Text>
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: selected }} onPress={onSelect}
      style={styles.toggle}><Text style={styles.toggleText}>{selected ? 'Hide details' : 'Tap for details'}</Text></Pressable>
    {selected && <View style={styles.details}>
      <Journey title="OUTBOUND" journey={itinerary.outbound} />
      <Journey title="RETURN" journey={itinerary.returning} />
      <Text style={styles.note}>Omio opens a search with these cities and dates. Select the offer again there; prices and availability can change.</Text>
      {outboundUrl && <Pressable accessibilityRole="link" onPress={() => { void openOmio(outboundUrl); }} style={styles.link}>
        <ExternalLink size={16} color="#fff" />
        <Text style={styles.linkText}>{itinerary.ticketOption === 'ROUND_TRIP' ? 'Search round trip on Omio' : 'Search outbound on Omio'}</Text>
      </Pressable>}
      {returnUrl && <Pressable accessibilityRole="link" onPress={() => { void openOmio(returnUrl); }} style={styles.link}>
        <ExternalLink size={16} color="#fff" /><Text style={styles.linkText}>Search return on Omio</Text>
      </Pressable>}
      {linkError && <Text accessibilityRole="alert" style={styles.error}>Could not open Omio. Please try again.</Text>}
    </View>}
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E3EAF2', borderRadius: 20, overflow: 'hidden' },
  header: { flexDirection: 'row', alignItems: 'center', padding: 15, gap: 10 },
  icon: { width: 36, height: 36, borderRadius: 12, backgroundColor: '#EDF5FF', alignItems: 'center', justifyContent: 'center' },
  logo: { width: 30, height: 25 },
  heading: { flex: 1, gap: 4 },
  label: { fontSize: 11, color: PURPLE, fontWeight: '800', letterSpacing: 0.5 },
  route: { color: '#202632', fontSize: 14, fontWeight: '800' },
  carrierRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  carrier: { color: '#202632', fontSize: 13, fontWeight: '700' },
  detail: { color: '#697587', fontSize: 12 },
  availability: { color: BLUE, fontSize: 12, fontWeight: '700' },
  price: { color: BLUE, fontSize: 18, fontWeight: '800' },
  toggle: { borderTopWidth: 1, borderColor: '#EDF1F5', paddingHorizontal: 15, paddingVertical: 10 },
  toggleText: { color: BLUE, fontSize: 12, fontWeight: '700' },
  details: { borderTopWidth: 1, borderColor: '#EDF1F5', padding: 15, gap: 15 },
  journey: { gap: 6 },
  leg: { gap: 3, paddingLeft: 10, borderLeftWidth: 2, borderLeftColor: '#DDE9FA' },
  legTitle: { color: '#566276', fontSize: 12, fontWeight: '700' },
  transfer: { color: PURPLE, fontSize: 12 },
  note: { color: '#697587', fontSize: 12 },
  link: { backgroundColor: BLUE, borderRadius: 12, minHeight: 43, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  linkText: { color: '#fff', fontWeight: '800' },
  error: { color: '#C62626', fontSize: 12 },
});
