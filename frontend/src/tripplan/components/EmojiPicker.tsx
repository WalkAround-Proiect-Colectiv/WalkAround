import { useMemo, useState } from 'react';
import { FlatList, Pressable, ScrollView, StyleSheet, Text, TextInput, useWindowDimensions, View } from 'react-native';
import { Search, X } from 'lucide-react-native';
import catalog from '../data/emojiCatalog.json';

type EmojiEntry = [glyph: string, name: string, groupIndex: number];

const EMOJIS = catalog.emojis as EmojiEntry[];
const GROUPS = catalog.groups;

interface Props {
  onClose: () => void;
  onSelect: (emoji: string) => void;
}

export default function EmojiPicker({ onClose, onSelect }: Props) {
  const { height } = useWindowDimensions();
  const [groupIndex, setGroupIndex] = useState<number | null>(null);
  const [search, setSearch] = useState('');

  const results = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return EMOJIS.filter(([glyph, name, group]) =>
      (groupIndex === null || group === groupIndex)
      && (!query || name.toLocaleLowerCase().includes(query) || glyph.includes(query)));
  }, [groupIndex, search]);

  return <View style={[styles.panel, { height: Math.min(340, Math.max(210, height * 0.42)) }]}>
    <View style={styles.heading}>
      <View>
        <Text style={styles.title}>Add an emoji</Text>
        <Text style={styles.subtitle}>Browse the full emoji collection</Text>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Close emoji palette"
        onPress={onClose} style={styles.closeButton}><X size={18} color="#4D5A6B" /></Pressable>
    </View>
    <View style={styles.searchBox}>
      <Search size={17} color="#8693A5" />
      <TextInput accessibilityLabel="Search emojis" value={search} onChangeText={setSearch}
        placeholder="Search emojis" placeholderTextColor="#8A96A6" style={styles.searchInput} />
    </View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.categoryScroll}
      keyboardShouldPersistTaps="always" contentContainerStyle={styles.categories}>
      <Pressable accessibilityRole="button" accessibilityState={{ selected: groupIndex === null }}
        onPress={() => setGroupIndex(null)} style={[styles.category, groupIndex === null && styles.activeCategory]}>
        <Text style={[styles.categoryText, groupIndex === null && styles.activeCategoryText]}>All</Text>
      </Pressable>
      {GROUPS.map((group, index) => <Pressable key={group} accessibilityRole="button"
        accessibilityState={{ selected: groupIndex === index }} onPress={() => setGroupIndex(index)}
        style={[styles.category, groupIndex === index && styles.activeCategory]}>
        <Text style={[styles.categoryText, groupIndex === index && styles.activeCategoryText]}>{group}</Text>
      </Pressable>)}
    </ScrollView>
    <FlatList data={results} keyExtractor={([glyph], index) => `${glyph}-${index}`} numColumns={8}
      keyboardShouldPersistTaps="always" initialNumToRender={48} maxToRenderPerBatch={64}
      windowSize={5} style={styles.list} contentContainerStyle={styles.grid}
      renderItem={({ item: [glyph, name] }) => <Pressable accessibilityRole="button"
        accessibilityLabel={`Add ${name} emoji`} onPress={() => onSelect(glyph)} style={styles.emojiButton}>
        <Text style={styles.emoji}>{glyph}</Text>
      </Pressable>}
      ListEmptyComponent={<Text style={styles.noResults}>No emojis match this search.</Text>} />
  </View>;
}

const styles = StyleSheet.create({
  panel: { alignSelf: 'center', width: '94%', maxWidth: 600, marginBottom: 8,
    padding: 12, borderRadius: 22,
    backgroundColor: '#fff', borderWidth: 1, borderColor: '#E3EAF3',
    shadowColor: '#253043', shadowOpacity: 0.09, shadowRadius: 14,
    shadowOffset: { width: 0, height: -2 }, elevation: 4 },
  heading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 },
  title: { color: '#253043', fontSize: 15, fontWeight: '800' },
  subtitle: { color: '#8390A2', fontSize: 11, marginTop: 2 },
  closeButton: { width: 32, height: 32, borderRadius: 11, backgroundColor: '#F2F5F9',
    alignItems: 'center', justifyContent: 'center' },
  searchBox: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 39, paddingHorizontal: 11,
    borderRadius: 13, backgroundColor: '#F4F7FB' },
  searchInput: { flex: 1, color: '#253043', fontSize: 13, outlineWidth: 0 },
  categoryScroll: { flexGrow: 0, maxHeight: 50 },
  categories: { gap: 6, paddingVertical: 10 },
  category: { paddingHorizontal: 11, height: 30, borderRadius: 15, backgroundColor: '#F3F6FA',
    alignItems: 'center', justifyContent: 'center' },
  activeCategory: { backgroundColor: '#E5F0FF' },
  categoryText: { color: '#687587', fontSize: 11, fontWeight: '700' },
  activeCategoryText: { color: '#0065EB' },
  list: { flex: 1 },
  grid: { paddingBottom: 8 },
  emojiButton: { width: '12.5%', height: 42, alignItems: 'center', justifyContent: 'center' },
  emoji: { fontSize: 25 },
  noResults: { color: '#8390A2', fontSize: 12, textAlign: 'center', marginTop: 30 },
});
