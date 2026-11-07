import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, CloudSun, LocateFixed } from 'lucide-react-native';
import {
  useRecommendationsScreen,
  RADIUS_OPTIONS,
} from '../../hooks/useRecommendationsScreen';
import WeatherSummaryBanner from '../components/WeatherSummaryBanner';
import RecommendationList from '../components/RecommendationList';
import {
  EmptyState,
  ErrorState,
  LoadingSkeleton,
  LocationUnavailableState,
  OfflineBanner,
  PermissionDeniedState,
} from '../components/RecommendationStates';

const BLUE = '#0065EB';
const INK = '#202632';

export type RecommendationsScreenViewModel = ReturnType<typeof useRecommendationsScreen>;

export interface RecommendationsScreenProps {
  viewModel?: RecommendationsScreenViewModel;
}

function formatRadiusLabel(radiusMeters: number): string {
  if (radiusMeters < 1000) {
    return `${radiusMeters} m`;
  }
  const km = radiusMeters / 1000;
  return `${Number.isInteger(km) ? km : km.toFixed(1)} km`;
}

export function RecommendationsView({ vm }: { vm: RecommendationsScreenViewModel }) {
  const insets = useSafeAreaInsets();
  const radiusOptions = vm.radiusOptions ?? RADIUS_OPTIONS;

  const weatherBanner = vm.data ? (
    <WeatherSummaryBanner
      weatherBucket={vm.data.weatherBucket}
      temperatureC={vm.data.temperatureC}
      weatherSummary={vm.data.weatherSummary}
      degraded={vm.data.degraded}
      isStale={vm.isStale}
    />
  ) : null;

  return (
    <View style={[styles.screen, { paddingTop: Math.max(insets.top, 12) }]}>
      <View style={styles.header}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back to map"
          onPress={vm.navigateBackToMap}
          style={styles.backButton}
        >
          <ArrowLeft size={20} color={BLUE} />
        </Pressable>
        <View style={styles.headerTitleGroup}>
          <View style={styles.titleRow}>
            <CloudSun size={22} color={BLUE} />
            <Text style={styles.title}>Weather Activities</Text>
          </View>
          <Text style={styles.subtitle}>
            {vm.locationLabel} · {formatRadiusLabel(vm.radiusMeters)} radius
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Use current location"
          onPress={vm.refreshLocation}
          style={styles.locateButton}
        >
          <LocateFixed size={19} color={BLUE} />
        </Pressable>
      </View>

      <View style={styles.controlsBar}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.controlsContent}
        >
          {radiusOptions.map((option: (typeof RADIUS_OPTIONS)[number]) => {
            const selected = option === vm.radiusMeters;
            return (
              <Pressable
                key={option}
                accessibilityRole="button"
                accessibilityLabel={`Radius ${option} meters`}
                accessibilityState={{ selected }}
                onPress={() => vm.setRadiusMeters(option)}
                style={[styles.radiusChip, selected && styles.radiusChipSelected]}
              >
                <Text style={[styles.radiusChipText, selected && styles.radiusChipTextSelected]}>
                  {formatRadiusLabel(option)}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </View>

      {vm.isOffline && <OfflineBanner />}

      <View style={styles.body}>
        {vm.locationStatus === 'loading' && <LoadingSkeleton />}

        {vm.locationStatus === 'denied' && (
          <PermissionDeniedState
            onOpenSettings={vm.openSettings}
            onRetry={vm.refreshLocation}
          />
        )}

        {vm.locationStatus === 'unavailable' && (
          <LocationUnavailableState
            onRetry={vm.refreshLocation}
          />
        )}

        {vm.locationStatus === 'granted' && (
          <>
            {vm.fetchStatus === 'loading' && <LoadingSkeleton />}

            {vm.fetchStatus === 'error' && (
              <View style={styles.stateContainer}>
                {weatherBanner && <View style={styles.bannerPadding}>{weatherBanner}</View>}
                <ErrorState
                  message={vm.error ?? 'Could not load activity recommendations.'}
                  onRetry={vm.refetch}
                />
              </View>
            )}

            {vm.fetchStatus === 'success' && vm.data && (
              <>
                {vm.data.recommendations.length === 0 ? (
                  <View style={styles.stateContainer}>
                    {weatherBanner && <View style={styles.bannerPadding}>{weatherBanner}</View>}
                    <EmptyState
                      radiusMeters={vm.radiusMeters}
                      canExpandRadius={vm.canExpandRadius}
                      onExpandRadius={vm.expandRadius}
                      onRetry={vm.refetch}
                    />
                  </View>
                ) : (
                  <RecommendationList
                    items={vm.data.recommendations}
                    refreshing={vm.isRefreshing}
                    onRefresh={vm.refetch}
                    header={weatherBanner}
                  />
                )}
              </>
            )}
          </>
        )}
      </View>
    </View>
  );
}

export default function RecommendationsScreen({ viewModel }: RecommendationsScreenProps = {}) {
  const defaultVm = useRecommendationsScreen();
  return <RecommendationsView vm={viewModel ?? defaultVm} />;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#F6F9FC',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 18,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderColor: '#E3EAF2',
    backgroundColor: '#ffffff',
  },
  backButton: {
    width: 42,
    height: 42,
    borderRadius: 13,
    backgroundColor: '#EAF3FF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitleGroup: {
    flex: 1,
    gap: 2,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  title: {
    color: INK,
    fontSize: 19,
    fontWeight: '800',
  },
  subtitle: {
    color: '#566276',
    fontSize: 12,
    fontWeight: '600',
  },
  locateButton: {
    width: 42,
    height: 42,
    borderRadius: 13,
    borderWidth: 1,
    borderColor: '#DCE5F2',
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  controlsBar: {
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderColor: '#E3EAF2',
    marginBottom: 12,
  },
  controlsContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  radiusChip: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
    backgroundColor: '#EEF3F9',
  },
  radiusChipSelected: {
    backgroundColor: BLUE,
  },
  radiusChipText: {
    color: '#363d47',
    fontSize: 12,
    fontWeight: '700',
  },
  radiusChipTextSelected: {
    color: '#ffffff',
  },
  body: {
    flex: 1,
  },
  stateContainer: {
    gap: 14,
  },
  bannerPadding: {
    paddingHorizontal: 18,
  },
});
