import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Redirect } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft } from 'lucide-react-native';
import { useEventDetailsScreen } from '../../hooks/useEventDetailsScreen';
import AttributionFooter from '../components/details/AttributionFooter';
import EnrichmentNotice from '../components/details/EnrichmentNotice';
import EventDescription from '../components/details/EventDescription';
import {
  ErrorState,
  IncompleteLinkState,
  LoadingSkeleton,
  NotFoundState,
} from '../components/details/EventDetailsStates';
import EventHero from '../components/details/EventHero';
import EventMeta from '../components/details/EventMeta';
import InterestSection from '../components/details/InterestSection';
import TicketLinks from '../components/details/TicketLinks';
import VenueCard from '../components/details/VenueCard';

const BLUE = '#0065EB';
const INK = '#202632';

export type EventDetailsScreenViewModel = ReturnType<typeof useEventDetailsScreen>;

export function EventDetailsView({ vm }: { vm: EventDetailsScreenViewModel }) {
  const insets = useSafeAreaInsets();
  const event = vm.event;

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back"
          onPress={vm.navigateBack}
          style={styles.back}
        >
          <ArrowLeft size={22} color={BLUE} />
        </Pressable>
        <Text accessibilityRole="header" style={styles.heading}>
          Event
        </Text>
      </View>

      {vm.status === 'incomplete-link' && <IncompleteLinkState onBack={vm.navigateBack} />}

      {vm.status === 'loading' && <LoadingSkeleton />}

      {vm.status === 'not-found' && <NotFoundState onBack={vm.navigateBack} />}

      {vm.status === 'error' && (
        <ErrorState
          message={vm.error || 'Could not load event details. Please try again.'}
          onRetry={vm.reload}
        />
      )}

      {vm.status === 'ready' && event && (
        <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
          <EnrichmentNotice enrichment={vm.enrichment} />

          <EventHero
            title={event.title}
            imageUrl={event.imageUrl}
            past={event.past}
          />

          <EventMeta
            startsAt={event.startsAt}
            allDay={event.allDay}
            whenText={event.whenText}
            venueName={event.venue?.name ?? null}
            address={event.address}
          />

          <EventDescription
            description={event.description}
            expanded={vm.descriptionExpanded}
            onToggleExpanded={vm.toggleDescriptionExpanded}
          />

          <VenueCard
            venue={event.venue}
            onOpenLink={url => void vm.openLink(url)}
          />

          <TicketLinks
            ticketGroups={vm.ticketGroups}
            eventUrl={event.eventUrl}
            venueUrl={event.venue?.url ?? null}
            onOpenLink={url => void vm.openLink(url)}
          />

          {vm.actionError && (
            <Text accessibilityRole="alert" style={styles.inlineError}>
              {vm.actionError}
            </Text>
          )}

          <InterestSection
            past={event.past}
            interest={vm.interest}
            saving={vm.saving}
            onToggleInterest={vm.toggleInterest}
            onSetReminder={vm.setReminder}
          />

          <AttributionFooter attribution={vm.attribution} />
        </ScrollView>
      )}
    </View>
  );
}

/**
 * Details for one event: `/events/details?localityKey=...&eventKey=...`.
 * Also the screen to open from an event on the map.
 */
export default function EventDetailsScreen() {
  const vm = useEventDetailsScreen();

  if (!vm.ready) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={BLUE} />
      </View>
    );
  }
  if (!vm.session) {
    return <Redirect href="/login" />;
  }

  return <EventDetailsView vm={vm} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f7fb' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e3e8ef',
  },
  back: { padding: 6, borderRadius: 12 },
  heading: { fontSize: 20, fontWeight: '800', color: INK },
  content: { padding: 16, gap: 12, maxWidth: 720, width: '100%', alignSelf: 'center' },
  inlineError: {
    color: '#b42318',
    backgroundColor: '#fdecea',
    padding: 10,
    borderRadius: 10,
  },
});
