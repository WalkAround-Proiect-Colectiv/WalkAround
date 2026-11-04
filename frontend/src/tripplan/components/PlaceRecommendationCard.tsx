import { useState } from 'react';
import { Image, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { ExternalLink, MapPin, Star } from 'lucide-react-native';
import type { PlaceRecommendation } from '../api/tripPlanning';

const BLUE = '#0065EB';
const INK = '#202632';

function safeHttpsUrl(value: string | null): string | null {
  if (!value) return null;

  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password ||
        url.search.toLowerCase().includes('api_key')) return null;
    return url.toString();
  } catch {
    return null;
  }
}

export default function PlaceRecommendationCard({ place }: { place: PlaceRecommendation }) {
  const [imageFailed, setImageFailed] = useState(false);
  const [linkError, setLinkError] = useState<string | null>(null);
  const imageUrl = safeHttpsUrl(place.imageUrl);
  const websiteUrl = safeHttpsUrl(place.websiteUrl);
  const mapsUrl = safeHttpsUrl(place.mapsUrl);
  const map = mapsUrl ? new URL(mapsUrl) : null;
  const safeMapsUrl = map?.hostname === 'www.google.com' && map.pathname.startsWith('/maps/search/')
    ? mapsUrl : null;

  async function openLink(url: string) {
    try {
      await Linking.openURL(url);
      setLinkError(null);
    } catch {
      setLinkError('Could not open this link. Please try again.');
    }
  }

  return <View style={styles.card}>
    {imageUrl && !imageFailed && <Image accessibilityLabel={place.name} source={{ uri: imageUrl }}
      onError={() => setImageFailed(true)} resizeMode="cover" style={styles.image} />}
    <View style={styles.content}>
      <View style={styles.heading}>
        <View style={styles.icon}><MapPin size={19} color={BLUE} /></View>
        <View style={styles.headingText}>
          {place.category && <Text style={styles.category}>{place.category.toUpperCase()}</Text>}
          <Text style={styles.name}>{place.name}</Text>
        </View>
      </View>

      {place.rating != null && <View style={styles.ratingRow}>
        <Star size={16} color="#E9A51B" fill="#E9A51B" />
        <Text style={styles.rating}>{Number(place.rating).toFixed(1)}</Text>
        {place.reviews != null && <Text style={styles.muted}>({place.reviews.toLocaleString()} reviews)</Text>}
      </View>}
      {place.address && <Text style={styles.address}>{place.address}</Text>}
      {place.description && <Text style={styles.description}>{place.description}</Text>}

      <View style={styles.actions}>
        {safeMapsUrl && <Pressable accessibilityRole="link" accessibilityLabel={`View ${place.name} on Google Maps`}
          onPress={() => openLink(safeMapsUrl)} style={styles.mapButton}>
          <MapPin size={16} color="#fff" />
          <Text style={styles.mapButtonText}>View on map</Text>
          <ExternalLink size={14} color="#fff" />
        </Pressable>}
        {websiteUrl && <Pressable accessibilityRole="link" accessibilityLabel={`Visit ${place.name} website`}
          onPress={() => openLink(websiteUrl)} style={styles.websiteButton}>
          <Text style={styles.websiteButtonText}>Website</Text>
          <ExternalLink size={14} color={BLUE} />
        </Pressable>}
      </View>
      {linkError && <Text accessibilityRole="alert" style={styles.error}>{linkError}</Text>}
    </View>
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E3EAF2', borderRadius: 20, overflow: 'hidden' },
  image: { width: '100%', height: 168, backgroundColor: '#EAF3FF' },
  content: { padding: 16, gap: 11 },
  heading: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  icon: { width: 38, height: 38, borderRadius: 13, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  headingText: { flex: 1, gap: 3 },
  category: { color: '#23012C', fontSize: 10, fontWeight: '800', letterSpacing: 0.8 },
  name: { color: INK, fontSize: 18, lineHeight: 23, fontWeight: '800' },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  rating: { color: INK, fontSize: 14, fontWeight: '800' },
  muted: { color: '#7A8595', fontSize: 12 },
  address: { color: '#566276', fontSize: 13, lineHeight: 19 },
  description: { color: '#455267', fontSize: 13, lineHeight: 20 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 3 },
  mapButton: { minHeight: 42, borderRadius: 12, backgroundColor: BLUE, flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: 12 },
  mapButtonText: { color: '#fff', fontSize: 13, fontWeight: '800' },
  websiteButton: { minHeight: 42, borderRadius: 12, borderWidth: 1, borderColor: '#B5D1FA', flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: 12 },
  websiteButtonText: { color: BLUE, fontSize: 13, fontWeight: '800' },
  error: { color: '#C51B2D', fontSize: 12 },
});
