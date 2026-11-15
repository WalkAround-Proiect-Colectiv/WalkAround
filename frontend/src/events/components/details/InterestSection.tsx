import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Bell, Heart } from 'lucide-react-native';
import type { InterestState } from '../../api/events';
import { REMINDER_OPTIONS } from '../../format';

const BLUE = '#0065EB';
const INK = '#202632';
const MUTED = '#5f6b7a';

export interface InterestSectionProps {
  past: boolean;
  interest: InterestState | null;
  saving: boolean;
  onToggleInterest(): void;
  onSetReminder(minutes: number | null): void;
}

export default function InterestSection({
  past,
  interest,
  saving,
  onToggleInterest,
  onSetReminder,
}: InterestSectionProps) {
  if (past) {
    return null;
  }
  const interested = Boolean(interest);
  const reminder = interest?.remindBeforeMinutes ?? null;

  return (
    <View style={styles.container}>
      <Pressable
        accessibilityRole="button"
        disabled={saving}
        accessibilityState={{ selected: interested, busy: saving }}
        onPress={onToggleInterest}
        style={[styles.primary, interested && styles.primaryOn, saving && styles.dim]}
      >
        <Heart
          size={18}
          color={interested ? '#e0245e' : '#fff'}
          fill={interested ? '#e0245e' : 'transparent'}
        />
        <Text style={[styles.primaryText, interested && styles.primaryTextOn]}>
          {interested ? 'Interested · tap to remove' : 'Mark as interested'}
        </Text>
      </Pressable>

      {interested && (
        <View style={styles.reminderBox}>
          <View style={styles.row}>
            <Bell size={16} color={BLUE} />
            <Text style={styles.reminderTitle}>Remind me</Text>
          </View>
          <View style={styles.chips}>
            {REMINDER_OPTIONS.map(option => {
              const selected = option.minutes === reminder;
              return (
                <Pressable
                  key={option.label}
                  accessibilityRole="radio"
                  accessibilityState={{ selected, disabled: saving }}
                  disabled={saving || selected}
                  onPress={() => onSetReminder(option.minutes)}
                  style={[styles.chip, selected && styles.chipOn]}
                >
                  <Text style={[styles.chipText, selected && styles.chipTextOn]}>
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          <Text style={styles.hint}>
            Reminders appear inside WalkAround while the app is open.
          </Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  primary: {
    minHeight: 46,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: BLUE,
    borderRadius: 14,
    paddingVertical: 13,
    paddingHorizontal: 16,
  },
  primaryOn: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#f3b6c8' },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  primaryTextOn: { color: '#b0154a' },
  dim: { opacity: 0.6 },
  reminderBox: {
    backgroundColor: '#fff',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#e3e8ef',
    padding: 12,
    gap: 10,
  },
  row: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  reminderTitle: { color: INK, fontWeight: '700' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    minHeight: 38,
    justifyContent: 'center',
    paddingVertical: 7,
    paddingHorizontal: 12,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#d5dce6',
  },
  chipOn: { backgroundColor: '#eef4ff', borderColor: BLUE },
  chipText: { color: MUTED, fontWeight: '600' },
  chipTextOn: { color: BLUE },
  hint: { color: '#8c96a4', fontSize: 12 },
});
