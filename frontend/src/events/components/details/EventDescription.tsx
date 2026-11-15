import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ChevronDown, ChevronUp } from 'lucide-react-native';

const BLUE = '#0065EB';
const INK = '#202632';
const COLLAPSE_CHAR_THRESHOLD = 260;

export interface EventDescriptionProps {
  description: string | null;
  expanded: boolean;
  onToggleExpanded(): void;
}

export default function EventDescription({
  description,
  expanded,
  onToggleExpanded,
}: EventDescriptionProps) {
  if (!description || !description.trim()) {
    return null;
  }
  const clean = description.trim();
  const isCollapsible = clean.length > COLLAPSE_CHAR_THRESHOLD;

  return (
    <View style={styles.card}>
      <Text style={styles.heading}>About this event</Text>
      <Text
        style={styles.body}
        numberOfLines={isCollapsible && !expanded ? 6 : undefined}
      >
        {clean}
      </Text>
      {isCollapsible && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={expanded ? 'Show less description' : 'Show more description'}
          onPress={onToggleExpanded}
          style={styles.toggleButton}
        >
          <Text style={styles.toggleText}>{expanded ? 'Show less' : 'Show more'}</Text>
          {expanded ? <ChevronUp size={16} color={BLUE} /> : <ChevronDown size={16} color={BLUE} />}
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
    gap: 8,
  },
  heading: { color: INK, fontSize: 15, fontWeight: '700' },
  body: { color: INK, fontSize: 15, lineHeight: 22 },
  toggleButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    minHeight: 44,
    alignSelf: 'flex-start',
    paddingVertical: 6,
  },
  toggleText: { color: BLUE, fontSize: 14, fontWeight: '700' },
});
