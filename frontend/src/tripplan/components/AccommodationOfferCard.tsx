import { useState } from 'react';
import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { BedDouble, CalendarDays, ExternalLink, MapPin, Star } from 'lucide-react-native';
import type { AccommodationOffer } from '../api/tripPlanning';

const BLUE = '#0065EB';
const INK = '#202632';

function priceLabel(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency', currency: currency || 'EUR', maximumFractionDigits: 2,
  }).format(amount);
}

function dateLabel(value: string): string {
  const date = new Date(`${value}T12:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

export default function AccommodationOfferCard({ offer, index }: { offer: AccommodationOffer; index: number }) {
  const [linkError, setLinkError] = useState<string | null>(null);
  const bookingOptions = offer.bookingOptions ?? [];
  const amenities = offer.amenities ?? [];
  const roomTypes = offer.roomTypes ?? [];

  async function openLink(url: string) {
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== 'https:' || !parsed.hostname || parsed.username || parsed.password ||
          parsed.search.toLowerCase().includes('api_key')) {
        throw new Error('Invalid accommodation link');
      }
      await Linking.openURL(url);
      setLinkError(null);
    } catch {
      setLinkError('Could not open this website. Please try another option.');
    }
  }

  return <View style={styles.card}>
    <View style={styles.topRow}>
      <View style={styles.icon}><BedDouble size={21} color={BLUE} /></View>
      <View style={styles.nameColumn}>
        <Text style={styles.eyebrow}>STAY OPTION {index + 1}{offer.propertyType ? ` · ${offer.propertyType}` : ''}</Text>
        <Text style={styles.name}>{offer.name}</Text>
      </View>
    </View>

    <View style={styles.priceRow}>
      <View>
        <Text style={styles.price}>{priceLabel(offer.totalPrice, offer.currency)}</Text>
        <Text style={styles.priceHelp}>Total for the stay, including taxes and fees · 1 adult</Text>
      </View>
    </View>

    <View style={styles.detailList}>
      <View style={styles.detailRow}>
        <CalendarDays size={17} color={BLUE} />
        <Text style={styles.detailText}>{dateLabel(offer.checkInDate)} → {dateLabel(offer.checkOutDate)}</Text>
      </View>
      {offer.address && <View style={styles.detailRow}>
        <MapPin size={17} color={BLUE} />
        <Text style={styles.detailText}>{offer.address}</Text>
      </View>}
      {offer.overallRating != null && <View style={styles.detailRow}>
        <Star size={17} color="#E9A51B" fill="#E9A51B" />
        <Text style={styles.detailText}>{Number(offer.overallRating).toFixed(1)} rating{offer.reviews != null ? ` · ${offer.reviews.toLocaleString()} reviews` : ''}</Text>
      </View>}
    </View>

    {offer.description && <Text style={styles.description}>{offer.description}</Text>}

    {roomTypes.length > 0 && <View style={styles.group}>
      <Text style={styles.groupTitle}>ROOM TYPES SHOWN</Text>
      <View style={styles.chips}>{roomTypes.map((room, roomIndex) => <View key={`${room}-${roomIndex}`} style={styles.chip}>
        <Text style={styles.chipText}>{room}</Text>
      </View>)}</View>
      <Text style={styles.note}>Room names are indicative; booking options may offer different rooms.</Text>
    </View>}

    {amenities.length > 0 && <View style={styles.group}>
      <Text style={styles.groupTitle}>AMENITIES</Text>
      <View style={styles.chips}>{amenities.map((amenity, amenityIndex) => <View key={`${amenity}-${amenityIndex}`} style={styles.chip}>
        <Text style={styles.chipText}>{amenity}</Text>
      </View>)}</View>
    </View>}

    <View style={styles.bookingGroup}>
      <Text style={styles.groupTitle}>VIEW BOOKING OPTIONS</Text>
      {bookingOptions.length > 0 ? bookingOptions.map((option, optionIndex) => <Pressable
        key={`${option.bookingUrl}-${optionIndex}`} accessibilityRole="link"
        accessibilityLabel={`View ${option.source || 'booking option'} for ${priceLabel(option.totalPrice, option.currency)}`}
        onPress={() => openLink(option.bookingUrl)} style={styles.bookingButton}>
        <View style={styles.bookingCopy}>
          <Text style={styles.bookingSource}>{option.source || 'Booking site'}</Text>
          <Text style={styles.bookingPrice}>{priceLabel(option.totalPrice, option.currency)} total</Text>
        </View>
        <ExternalLink size={17} color="#fff" />
      </Pressable>) : <Text style={styles.note}>No direct booking link was provided for this offer.</Text>}
      {offer.propertyWebsiteUrl && <Pressable accessibilityRole="link" accessibilityLabel={`Visit ${offer.name} website`}
        onPress={() => openLink(offer.propertyWebsiteUrl!)} style={styles.websiteButton}>
        <Text style={styles.websiteText}>Visit property website</Text>
        <ExternalLink size={16} color={BLUE} />
      </Pressable>}
      {offer.propertyWebsiteUrl && <Text style={styles.note}>The property website may show a different price or may not support booking.</Text>}
      <Text style={styles.note}>Prices and availability can change when you open a booking site.</Text>
      {linkError && <Text accessibilityRole="alert" style={styles.error}>{linkError}</Text>}
    </View>
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E3EAF2', borderRadius: 20, padding: 16, gap: 16 },
  topRow: { flexDirection: 'row', gap: 11, alignItems: 'flex-start' },
  icon: { width: 42, height: 42, borderRadius: 14, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  nameColumn: { flex: 1, gap: 4 },
  eyebrow: { color: '#7A8595', fontSize: 10, fontWeight: '800', letterSpacing: 0.8 },
  name: { color: INK, fontSize: 19, fontWeight: '800', lineHeight: 24 },
  priceRow: { borderRadius: 15, backgroundColor: '#F1F7FF', padding: 13 },
  price: { color: BLUE, fontSize: 23, fontWeight: '800' },
  priceHelp: { color: '#66758A', fontSize: 12, marginTop: 3, lineHeight: 18 },
  detailList: { gap: 10 },
  detailRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 9 },
  detailText: { flex: 1, color: '#3E495A', fontSize: 14, lineHeight: 20 },
  description: { color: '#566276', fontSize: 14, lineHeight: 21 },
  group: { gap: 9 },
  groupTitle: { color: '#23012C', fontSize: 11, fontWeight: '800', letterSpacing: 0.9 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  chip: { borderRadius: 11, backgroundColor: '#F4F7FB', borderWidth: 1, borderColor: '#E8EDF3', paddingHorizontal: 10, paddingVertical: 7 },
  chipText: { color: '#455267', fontSize: 12 },
  note: { color: '#7A8595', fontSize: 12, lineHeight: 18 },
  bookingGroup: { gap: 9, borderTopWidth: 1, borderColor: '#EDF1F5', paddingTop: 15 },
  bookingButton: { minHeight: 55, borderRadius: 13, backgroundColor: BLUE, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14, paddingVertical: 9 },
  bookingCopy: { flex: 1, gap: 2 },
  bookingSource: { color: '#fff', fontSize: 14, fontWeight: '800' },
  bookingPrice: { color: '#EAF3FF', fontSize: 12 },
  websiteButton: { minHeight: 44, borderRadius: 13, borderWidth: 1, borderColor: '#B5D1FA', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 13 },
  websiteText: { color: BLUE, fontWeight: '800', fontSize: 13 },
  error: { color: '#C51B2D', fontSize: 13 },
});
