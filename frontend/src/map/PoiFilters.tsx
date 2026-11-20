import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { X } from 'lucide-react-native';
import type { EventTypeFilter, PoiFilter } from './filters';

type Props = {
  visible: boolean;
  poiFilter: PoiFilter;
  eventType: EventTypeFilter;
  signedIn: boolean;
  onChange(poi: PoiFilter, event: EventTypeFilter): void;
  onClose(): void;
};

export default function PoiFilters({ visible, poiFilter, eventType, signedIn, onChange, onClose }: Props) {
  function option(label: string, selected: boolean, onPress: () => void) {
    return <Pressable key={label} accessibilityRole="radio" accessibilityState={{ checked: selected }} aria-checked={selected}
      onPress={onPress} style={[styles.option, selected && styles.selected]}>
      <Text style={[styles.optionText, selected && styles.selectedText]}>{label}</Text>
    </Pressable>;
  }

  return <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
    <View style={styles.overlay}>
      <View style={styles.panel} accessibilityViewIsModal>
        <View style={styles.header}>
          <Text accessibilityRole="header" style={styles.title}>Map filters</Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Close map filters" onPress={onClose} style={styles.close}><X size={24} color="#404954" /></Pressable>
        </View>
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.label}>Show on map</Text>
          <View accessibilityRole="radiogroup" accessibilityLabel="Points of interest" style={styles.options}>
            {([['all', 'Show all'], ['events', 'Events'], ['attractions', 'Tourist attractions']] as const).map(([value, label]) =>
              option(label, poiFilter === value, () => onChange(value, value === 'all' ? 'all' : eventType)))}
          </View>
          {poiFilter !== 'attractions' && <>
            <Text style={styles.label}>Event type</Text>
            <View accessibilityRole="radiogroup" accessibilityLabel="Event type" style={styles.options}>
              {([['all', 'All types'], ['CULTURAL', 'Cultural'], ['SPORTS', 'Sports'], ['OTHER', 'Other']] as const).map(([value, label]) =>
                option(label, eventType === value, () => onChange(poiFilter, value)))}
            </View>
            <Text style={styles.hint}>Types are inferred from event titles and descriptions. Unclassified events appear under Other.</Text>
            {!signedIn && <Text style={styles.hint}>Sign in to see nearby events.</Text>}
          </>}
          {poiFilter === 'attractions' && <Text style={styles.hint}>Tourism and cultural places, including museums and theatres. Zoom in to see individual places.</Text>}
          <Pressable accessibilityRole="button" onPress={() => onChange('all', 'all')} style={styles.option}><Text style={styles.optionText}>Reset filters</Text></Pressable>
        </ScrollView>
        <Pressable accessibilityRole="button" onPress={onClose} style={styles.done}><Text style={styles.selectedText}>Done</Text></Pressable>
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: '#00000066', justifyContent: 'center', alignItems: 'center', padding: 20 },
  panel: { width: '100%', maxWidth: 440, maxHeight: '90%', backgroundColor: '#fff', borderRadius: 24, padding: 20 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  title: { fontSize: 22, fontWeight: '700', color: '#26313e' },
  close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  content: { gap: 14 },
  label: { fontSize: 16, fontWeight: '600', color: '#26313e' },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  option: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 14, borderWidth: 1, borderColor: '#dce0e5' },
  optionText: { color: '#0065EB', fontSize: 15, fontWeight: '600' },
  selected: { backgroundColor: '#0065EB', borderColor: '#0065EB' },
  selectedText: { color: '#fff', fontSize: 15, fontWeight: '600' },
  hint: { color: '#626d7a', fontSize: 13, lineHeight: 20 },
  done: { marginTop: 16, minHeight: 48, borderRadius: 14, backgroundColor: '#0065EB', alignItems: 'center', justifyContent: 'center' },
});
