import { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { CalendarDays, ChevronLeft, ChevronRight, X } from 'lucide-react-native';

const BLUE = '#0065EB';
const WEEKDAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

function dateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export default function CalendarPicker({
  label, value, visible, minimumDate, description, onSelect, onClose,
}: {
  label: string;
  value: string;
  visible: boolean;
  minimumDate?: string;
  description?: string;
  onSelect(date: string): void;
  onClose(): void;
}) {
  const today = new Date();
  const todayKey = dateKey(today.getFullYear(), today.getMonth(), today.getDate());
  const earliest = minimumDate && minimumDate > todayKey ? minimumDate : todayKey;
  const [displayedMonth, setDisplayedMonth] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1));

  useEffect(() => {
    if (!visible) return;
    const initial = /^\d{4}-\d{2}-\d{2}$/.test(value) && value >= earliest ? value : earliest;
    const [year, month] = initial.split('-').map(Number);
    setDisplayedMonth(new Date(year, month - 1, 1));
  }, [visible, value, earliest]);

  const year = displayedMonth.getFullYear();
  const month = displayedMonth.getMonth();
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells = Array.from({ length: firstWeekday + daysInMonth }, (_, index) => index - firstWeekday + 1);
  const previousDisabled = dateKey(year, month, 1) <= earliest.slice(0, 7) + '-01';

  function changeMonth(offset: number) {
    setDisplayedMonth(new Date(year, month + offset, 1));
  }

  return <Modal animationType="fade" transparent visible={visible} onRequestClose={onClose}>
    <View style={styles.overlay}>
      <Pressable accessibilityLabel="Close calendar" onPress={onClose} style={styles.backdrop} />
      <View style={styles.card}>
        <View style={styles.header}>
          <View style={styles.titleRow}>
            <View style={styles.icon}><CalendarDays size={20} color={BLUE} /></View>
            <View>
              <Text style={styles.title}>Choose {label.toLowerCase()} date</Text>
              <Text style={styles.subtitle}>{description || 'Select a day for your departure window'}</Text>
            </View>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel="Close calendar" onPress={onClose} style={styles.close}>
            <X size={19} color="#667285" />
          </Pressable>
        </View>

        <View style={styles.monthRow}>
          <Pressable accessibilityRole="button" accessibilityLabel="Previous month" accessibilityState={{ disabled: previousDisabled }}
            disabled={previousDisabled} onPress={() => changeMonth(-1)} style={[styles.monthButton, previousDisabled && styles.disabled]}>
            <ChevronLeft size={20} color="#202632" />
          </Pressable>
          <Text style={styles.monthTitle}>{displayedMonth.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Next month" onPress={() => changeMonth(1)} style={styles.monthButton}>
            <ChevronRight size={20} color="#202632" />
          </Pressable>
        </View>

        <View style={styles.grid}>
          {WEEKDAYS.map((day, index) => <View key={index} style={styles.dayCell}>
            <Text style={styles.weekday}>{day}</Text>
          </View>)}
          {cells.map((day, index) => {
            if (day < 1) return <View key={`empty-${index}`} style={styles.dayCell} />;
            const key = dateKey(year, month, day);
            const disabled = key < earliest;
            const selected = key === value;

            return <View key={key} style={styles.dayCell}>
              <Pressable accessibilityRole="button" accessibilityLabel={new Date(year, month, day).toDateString()}
                accessibilityState={{ selected, disabled }} disabled={disabled} onPress={() => { onSelect(key); onClose(); }}
                style={[styles.dayButton, selected && styles.selectedDay]}>
                <Text style={[styles.dayText, disabled && styles.disabledDay, selected && styles.selectedDayText]}>{day}</Text>
              </Pressable>
            </View>;
          })}
        </View>
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16 },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(25, 33, 50, 0.45)' },
  card: { width: '100%', maxWidth: 380, backgroundColor: '#fff', borderRadius: 24, padding: 20, gap: 22 },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1 },
  icon: { width: 40, height: 40, borderRadius: 13, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  title: { color: '#202632', fontSize: 17, fontWeight: '800' },
  subtitle: { color: '#7A8595', fontSize: 12, marginTop: 2 },
  close: { padding: 6 },
  monthRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  monthButton: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#F4F7FB', alignItems: 'center', justifyContent: 'center' },
  disabled: { opacity: 0.35 },
  monthTitle: { color: '#202632', fontSize: 17, fontWeight: '800' },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  dayCell: { width: '14.2857%', height: 44, alignItems: 'center', justifyContent: 'center' },
  weekday: { color: '#7A8595', fontSize: 12, fontWeight: '800' },
  dayButton: { width: 38, height: 38, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  selectedDay: { backgroundColor: BLUE },
  dayText: { color: '#202632', fontSize: 14, fontWeight: '700' },
  disabledDay: { color: '#C7CFD9' },
  selectedDayText: { color: '#fff' },
});
