import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AlertTriangle, ArrowLeft, CalendarX, Link2Off, RotateCcw } from 'lucide-react-native';

const BLUE = '#0065EB';
const INK = '#202632';

export function LoadingSkeleton() {
  return (
    <View accessibilityLabel="Loading event details" style={styles.skeletonWrapper}>
      <View style={styles.skeletonHero} />
      <View style={styles.skeletonTitle} />
      <View style={styles.skeletonCard}>
        <View style={styles.skeletonLine} />
        <View style={styles.skeletonLineShort} />
      </View>
      <View style={styles.skeletonCard}>
        <View style={styles.skeletonLine} />
        <View style={styles.skeletonLine} />
        <View style={styles.skeletonLineShort} />
      </View>
    </View>
  );
}

export function IncompleteLinkState({ onBack }: { onBack(): void }) {
  return (
    <View style={styles.stateCard}>
      <View style={styles.iconCircle}>
        <Link2Off size={26} color={BLUE} />
      </View>
      <Text style={styles.stateTitle}>This event link is incomplete</Text>
      <Text style={styles.stateBody}>
        The link is missing the town or event identifier needed to open the event.
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Back to events"
        onPress={onBack}
        style={styles.primaryButton}
      >
        <ArrowLeft size={16} color="#fff" />
        <Text style={styles.primaryButtonText}>Back to events</Text>
      </Pressable>
    </View>
  );
}

export function NotFoundState({ onBack }: { onBack(): void }) {
  return (
    <View style={styles.stateCard}>
      <View style={styles.iconCircle}>
        <CalendarX size={26} color={BLUE} />
      </View>
      <Text style={styles.stateTitle}>This event is no longer available</Text>
      <Text style={styles.stateBody}>
        It may have already ended or been removed from the upcoming events list.
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Back to events"
        onPress={onBack}
        style={styles.primaryButton}
      >
        <ArrowLeft size={16} color="#fff" />
        <Text style={styles.primaryButtonText}>Back to events</Text>
      </Pressable>
    </View>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry(): void;
}) {
  return (
    <View accessibilityRole="alert" style={styles.stateCard}>
      <View style={[styles.iconCircle, styles.errorIconCircle]}>
        <AlertTriangle size={26} color="#C51B2D" />
      </View>
      <Text style={styles.stateTitle}>Could not load event details</Text>
      <Text style={styles.stateBody}>{message}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Retry loading event details"
        onPress={onRetry}
        style={styles.primaryButton}
      >
        <RotateCcw size={16} color="#fff" />
        <Text style={styles.primaryButtonText}>Try again</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  skeletonWrapper: {
    padding: 16,
    gap: 12,
    maxWidth: 720,
    width: '100%',
    alignSelf: 'center',
  },
  skeletonHero: {
    width: '100%',
    height: 190,
    borderRadius: 16,
    backgroundColor: '#e6ecf5',
  },
  skeletonTitle: {
    width: '72%',
    height: 26,
    borderRadius: 8,
    backgroundColor: '#e6ecf5',
  },
  skeletonCard: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e3e8ef',
    borderRadius: 14,
    padding: 14,
    gap: 10,
  },
  skeletonLine: {
    width: '92%',
    height: 16,
    borderRadius: 6,
    backgroundColor: '#eef2f7',
  },
  skeletonLineShort: {
    width: '58%',
    height: 16,
    borderRadius: 6,
    backgroundColor: '#eef2f7',
  },
  stateCard: {
    margin: 18,
    padding: 22,
    borderRadius: 20,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e3e8ef',
    alignItems: 'center',
    gap: 12,
    maxWidth: 520,
    alignSelf: 'center',
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: '#eaf3ff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  errorIconCircle: {
    backgroundColor: '#fdecee',
  },
  stateTitle: {
    color: INK,
    fontSize: 18,
    fontWeight: '800',
    textAlign: 'center',
  },
  stateBody: {
    color: '#5f6b7a',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  primaryButton: {
    minHeight: 44,
    paddingHorizontal: 18,
    paddingVertical: 11,
    borderRadius: 13,
    backgroundColor: BLUE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 4,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '800',
  },
});
