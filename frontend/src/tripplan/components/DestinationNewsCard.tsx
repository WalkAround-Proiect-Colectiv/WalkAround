import { useState } from 'react';
import { ActivityIndicator, Image, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowRight, ChevronDown, ExternalLink, Newspaper, RefreshCw } from 'lucide-react-native';
import type { CitySuggestion, NewsArticle } from '../api/tripPlanning';

const BLUE = '#0065EB';
const PURPLE = '#23012C';
const INK = '#202632';

function Article({ article }: { article: NewsArticle }) {
  const [imageFailed, setImageFailed] = useState(false);
  const [linkError, setLinkError] = useState(false);

  async function openArticle() {
    try {
      const url = new URL(article.url);
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) {
        throw new Error('Invalid article URL');
      }
      await Linking.openURL(url.toString());
      setLinkError(false);
    } catch {
      setLinkError(true);
    }
  }

  let publisher = 'Publisher';
  try {
    publisher = new URL(article.url).hostname.replace(/^www\./, '');
  } catch {
    // The article link still has a guarded open action below.
  }
  const published = article.publishedAt?.replace(' ', ' · ') || null;

  return <View style={styles.article}>
    <View style={styles.articleRow}>
      <View style={styles.articleCopy}>
        <Text style={styles.publisher}>{publisher}</Text>
        <Text style={styles.articleTitle}>{article.title}</Text>
      </View>
      {article.imageUrl && !imageFailed && <Image source={{ uri: article.imageUrl }}
        onError={() => setImageFailed(true)} style={styles.image} />}
    </View>
    {article.description && <Text style={styles.description} numberOfLines={3}>{article.description}</Text>}
    <Text style={styles.meta}>
      {[article.author, published, article.categories.slice(0, 2).map((value) => value.replace(/_/g, ' ')).join(', ')]
        .filter(Boolean).join(' · ')}
    </Text>
    <Pressable accessibilityRole="link" onPress={openArticle} style={styles.articleLink}>
      <Text style={styles.linkText}>Read article</Text><ExternalLink size={14} color={BLUE} />
    </Pressable>
    {linkError && <Text accessibilityRole="alert" style={styles.error}>Could not open this article.</Text>}
  </View>;
}

export default function DestinationNewsCard({ city, articles, busy, error, onLoad }: {
  city: CitySuggestion | null;
  articles: NewsArticle[] | null;
  busy: boolean;
  error: string | null;
  onLoad(): void;
}) {
  const [showAll, setShowAll] = useState(false);
  const visible = showAll ? articles : articles?.slice(0, 5);

  return <View style={styles.card}>
    <View style={styles.heading}>
      <View style={styles.icon}><Newspaper size={21} color={PURPLE} /></View>
      <View style={styles.headingCopy}>
        <Text style={styles.eyebrow}>MORE TO EXPLORE</Text>
        <Text style={styles.title}>News around your destination</Text>
        <Text style={styles.helper}>{city
          ? `English-language articles mentioning ${city.name} from ${city.country} sources`
          : 'Choose a destination to look up recent news'}</Text>
      </View>
    </View>

    {city && <Pressable accessibilityRole="button" disabled={busy} onPress={onLoad}
      style={[styles.loadButton, busy && styles.disabled]}>
      {busy ? <ActivityIndicator color="#fff" /> : articles ? <RefreshCw size={17} color="#fff" /> : <ArrowRight size={17} color="#fff" />}
      <Text style={styles.loadText}>{busy ? 'Loading news…' : articles ? 'Refresh news' : 'See destination news'}</Text>
    </Pressable>}
    {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
    {articles?.length === 0 && <Text style={styles.helper}>No matching articles were returned. Try again later.</Text>}
    {visible && visible.length > 0 && <View style={styles.list}>
      {visible.map((article, index) => <Article key={article.id || `${article.url}-${index}`} article={article} />)}
      {articles && articles.length > 5 && <Pressable accessibilityRole="button"
        accessibilityState={{ expanded: showAll }} onPress={() => setShowAll((value) => !value)}
        style={styles.moreButton}>
        <Text style={styles.linkText}>{showAll ? 'Show fewer articles' : `Show all ${articles.length} articles`}</Text>
        <ChevronDown size={17} color={BLUE} style={showAll && styles.chevronOpen} />
      </Pressable>}
    </View>}
    {articles && <Text style={styles.note}>Headlines may mention the city without describing local conditions. Open the publisher’s article for context.</Text>}
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#fff', borderRadius: 26, borderWidth: 1, borderColor: '#E5E7F0', padding: 20, gap: 14 },
  heading: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  icon: { width: 43, height: 43, borderRadius: 15, backgroundColor: '#F3ECF6', alignItems: 'center', justifyContent: 'center' },
  headingCopy: { flex: 1, gap: 3 },
  eyebrow: { color: PURPLE, fontSize: 10, fontWeight: '800', letterSpacing: 1 },
  title: { color: INK, fontSize: 19, fontWeight: '800' },
  helper: { color: '#7A8595', fontSize: 13, lineHeight: 19 },
  loadButton: { minHeight: 46, borderRadius: 14, backgroundColor: BLUE, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  loadText: { color: '#fff', fontSize: 14, fontWeight: '800' },
  disabled: { opacity: 0.55 },
  error: { color: '#C51B2D', fontSize: 13, lineHeight: 19 },
  list: { gap: 10 },
  article: { borderWidth: 1, borderColor: '#E5EAF1', borderRadius: 15, padding: 13, gap: 7 },
  articleRow: { flexDirection: 'row', gap: 10 },
  articleCopy: { flex: 1, gap: 5 },
  publisher: { color: PURPLE, fontSize: 11, fontWeight: '800' },
  articleTitle: { color: INK, fontSize: 15, fontWeight: '800', lineHeight: 21 },
  image: { width: 76, height: 76, borderRadius: 11, backgroundColor: '#F5F7FC' },
  description: { color: '#455267', fontSize: 13, lineHeight: 19 },
  meta: { color: '#7A8595', fontSize: 11, lineHeight: 17 },
  articleLink: { alignSelf: 'flex-start', minHeight: 34, flexDirection: 'row', alignItems: 'center', gap: 5 },
  linkText: { color: BLUE, fontSize: 13, fontWeight: '800' },
  moreButton: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  chevronOpen: { transform: [{ rotate: '180deg' }] },
  note: { color: '#7A8595', fontSize: 11, lineHeight: 17 },
});
