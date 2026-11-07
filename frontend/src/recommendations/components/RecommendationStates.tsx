import { Pressable, StyleSheet, Text, View } from 'react-native';
import {
  AlertTriangle,
  Compass,
  MapPinOff,
  RotateCcw,
  Settings,
  WifiOff,
} from 'lucide-react-native';

const BLUE = '#0065EB';
const INK = '#202632';

export function OfflineBanner() {
  return (
    <View accessibilityRole="alert" style={styles.offlineBanner}>
      <WifiOff size={16} color="#9A5B00" />
      <Text style={styles.offlineBannerText}>
        You Appear to Be Offline · Showing cached recommendations if available
      </Text>
    </View>
  );
}

export function LoadingSkeleton() {
  return (
    <View accessibilityLabel="Loading activity recommendations" style={styles.skeletonList}>
      {[1, 2, 3].map(index => (
        <View key={index} style={styles.skeletonCard}>
          <View style={styles.skeletonBadgeRow}>
            <View style={styles.skeletonChip} />
            <View style={styles.skeletonChipSmall} />
          </View>
          <View style={styles.skeletonTitle} />
          <View style={styles.skeletonBody} />
        </View>
      ))}
    </View>
  );
}

export interface EmptyStateProps {
  radiusMeters: number;
  canExpandRadius: boolean;
  onExpandRadius(): void;
  onRetry(): void;
}

export function EmptyState({
  radiusMeters,
  canExpandRadius,
  onExpandRadius,
  onRetry,
}: EmptyStateProps) {
  return (
    <View style={styles.stateCard}>
      <View style={styles.iconCircle}>
        <Compass size={26} color={BLUE} />
      </View>
      <Text style={styles.stateTitle}>No activities found nearby</Text>
      <Text style={styles.stateBody}>
        We could not find matching points of interest within {radiusMeters} m of your location.
        Try expanding the search radius.
      </Text>
      <View style={styles.buttonRow}>
        {canExpandRadius && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Expand search radius"
            onPress={onExpandRadius}
            style={styles.primaryButton}
          >
            <Text style={styles.primaryButtonText}>Try a larger radius</Text>
          </Pressable>
        )}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Refresh recommendations"
          onPress={onRetry}
          style={styles.secondaryButton}
        >
          <RotateCcw size={16} color={BLUE} />
          <Text style={styles.secondaryButtonText}>Refresh</Text>
        </Pressable>
      </View>
    </View>
  );
}

export interface ErrorStateProps {
  message: string;
  onRetry(): void;
}

export function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <View accessibilityRole="alert" style={styles.stateCard}>
      <View style={[styles.iconCircle, styles.errorIconCircle]}>
        <AlertTriangle size={26} color="#C51B2D" />
      </View>
      <Text style={styles.stateTitle}>Could not load recommendations</Text>
      <Text style={styles.stateBody}>{message}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Retry loading recommendations"
        onPress={onRetry}
        style={styles.primaryButton}
      >
        <RotateCcw size={16} color="#ffffff" />
        <Text style={styles.primaryButtonText}>Try again</Text>
      </Pressable>
    </View>
  );
}

export interface PermissionDeniedStateProps {
  onOpenSettings(): void;
  onRetry(): void;
}

export function PermissionDeniedState({
  onOpenSettings,
  onRetry,
}: PermissionDeniedStateProps) {
  return (
    <View style={styles.stateCard}>
      <View style={styles.iconCircle}>
        <MapPinOff size={26} color={BLUE} />
      </View>
      <Text style={styles.stateTitle}>Location access is needed</Text>
      <Text style={styles.stateBody}>
        WalkAround uses your foreground location to check local weather and recommend nearby
        activities. Please grant location access in your settings.
      </Text>
      <View style={styles.buttonRow}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Open device settings"
          onPress={onOpenSettings}
          style={styles.primaryButton}
        >
          <Settings size={16} color="#ffffff" />
          <Text style={styles.primaryButtonText}>Open settings</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Retry location permission"
          onPress={onRetry}
          style={styles.secondaryButton}
        >
          <RotateCcw size={16} color={BLUE} />
          <Text style={styles.secondaryButtonText}>Retry</Text>
        </Pressable>
      </View>
    </View>
  );
}

export interface LocationUnavailableStateProps {
  onRetry(): void;
}

export function LocationUnavailableState({
  onRetry,
}: LocationUnavailableStateProps) {
  return (
    <View style={styles.stateCard}>
      <View style={styles.iconCircle}>
        <MapPinOff size={26} color={BLUE} />
      </View>
      <Text style={styles.stateTitle}>Location is currently unavailable</Text>
      <Text style={styles.stateBody}>
        We could not determine your current position in time. Check that location services are
        enabled and try again.
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Retry finding location"
        onPress={onRetry}
        style={styles.primaryButton}
      >
        <RotateCcw size={16} color="#ffffff" />
        <Text style={styles.primaryButtonText}>Retry location</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  offlineBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginHorizontal: 18,
    marginBottom: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: '#FFF6E5',
    borderWidth: 1,
    borderColor: '#F5D491',
  },
  offlineBannerText: {
    flex: 1,
    color: '#804B00',
    fontSize: 12,
    fontWeight: '700',
  },
  skeletonList: {
    gap: 12,
    paddingHorizontal: 18,
  },
  skeletonCard: {
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#E3EAF2',
    borderRadius: 20,
    padding: 16,
    gap: 10,
  },
  skeletonBadgeRow: {
    flexDirection: 'row',
    gap: 8,
  },
  skeletonChip: {
    width: 90,
    height: 20,
    borderRadius: 8,
    backgroundColor: '#EAF3FF',
  },
  skeletonChipSmall: {
    width: 64,
    height: 20,
    borderRadius: 8,
    backgroundColor: '#EEF2F7',
  },
  skeletonTitle: {
    width: '65%',
    height: 22,
    borderRadius: 8,
    backgroundColor: '#E6ECF5',
  },
  skeletonBody: {
    width: '90%',
    height: 16,
    borderRadius: 6,
    backgroundColor: '#EEF2F7',
  },
  stateCard: {
    marginHorizontal: 18,
    padding: 22,
    borderRadius: 22,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#E3EAF2',
    alignItems: 'center',
    gap: 12,
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: '#EAF3FF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  errorIconCircle: {
    backgroundColor: '#FDECEE',
  },
  stateTitle: {
    color: INK,
    fontSize: 18,
    fontWeight: '800',
    textAlign: 'center',
  },
  stateBody: {
    color: '#566276',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  buttonRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 10,
    marginTop: 4,
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
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '800',
  },
  secondaryButton: {
    minHeight: 44,
    paddingHorizontal: 16,
    paddingVertical: 11,
    borderRadius: 13,
    borderWidth: 1,
    borderColor: '#B5D1FA',
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  secondaryButtonText: {
    color: BLUE,
    fontSize: 14,
    fontWeight: '800',
  },
});
