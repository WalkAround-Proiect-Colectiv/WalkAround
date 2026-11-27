package services.applicationapi.journal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.country.model.Country;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.journal.dto.*;
import services.applicationapi.journal.repository.JournalTripRepository;
import services.applicationapi.journal.service.*;
import services.applicationapi.tripplan.dto.TripPlanResponse;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;
import services.applicationapi.tripplan.service.savedplan.SavedTripPlanService;
import tools.jackson.databind.json.JsonMapper;

class JournalTripServiceTest {
    private final JournalTripRepository trips = mock(JournalTripRepository.class);
    private final SavedTripPlanService plans = mock(SavedTripPlanService.class);
    private final CountryRepository countries = mock(CountryRepository.class);
    private final DestinationCityValidator cities = mock(DestinationCityValidator.class);
    private final UUID owner = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2028, 2, 28);
    private JournalTripService service;

    @BeforeEach
    void setup() {
        service = new JournalTripService(trips, plans, countries, cities,
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), JsonMapper.builder().build());
        when(trips.lockOwner(owner)).thenReturn(true);
        when(trips.find(any(), any())).thenReturn(Optional.empty());
        when(trips.create(eq(owner), any(), any())).thenAnswer(call -> call.getArgument(1));
        when(trips.update(eq(owner), any())).thenAnswer(call -> call.getArgument(1));
        Country country = mock(Country.class);
        when(country.getName()).thenReturn("Romania");
        when(countries.findByCode("RO")).thenReturn(Optional.of(country));
        when(cities.validate(anyString(), eq("RO"))).thenReturn(new DestinationCityValidator.ValidatedCity("Cluj-Napoca", "Romania"));
    }

    @Test
    void manualCreationReusesCatalogAndCityValidationAndAllowsSameDay() {
        var saved = service.create(owner, input(id, "  Weekend  ", "  A private trip  ", null, null, null));
        assertThat(saved.title()).isEqualTo("Weekend");
        assertThat(saved.description()).isEqualTo("A private trip");
        assertThat(saved.startDate()).isEqualTo(saved.endDate());
        verify(countries).findByCode("RO");
        verify(cities).validate("Cluj-Napoca", "RO");
    }

    @Test
    void descriptionIsMandatoryAndUnsupportedDestinationsAreRejected() {
        status(() -> service.create(owner, input(id, "Weekend", " ", null, null, null)), 400);
        when(countries.findByCode("RO")).thenReturn(Optional.empty());
        status(() -> service.create(owner, input(id, "Weekend", "Private", null, null, null)), 400);
        verify(trips, never()).create(any(), any(), any());
    }

    @Test
    void expiredPersonalPlansCanBeImportedWithEditableFieldsAndSavedNights() {
        var plan = plan();
        when(plans.get(owner, sourceId)).thenReturn(plan);
        var saved = service.create(owner, input(id, "My own name", "New description", sourceId, JournalDates.StartMode.CUSTOM, true));
        assertThat(saved.title()).isEqualTo("My own name");
        assertThat(saved.description()).isEqualTo("New description");
        assertThat(saved.endDate()).isEqualTo(today.plusDays(4));
        assertThat(saved.sourcePlanId()).isEqualTo(sourceId);
        verify(cities, never()).validate(any(), any());
        status(() -> service.create(owner, input(UUID.randomUUID(), "Trip", "Description", sourceId, JournalDates.StartMode.PLANNED, true)), 400);
        status(() -> service.create(owner, input(UUID.randomUUID(), "Trip", "Description", sourceId, JournalDates.StartMode.CUSTOM, null)), 400);
    }

    @Test
    void groupPlansCannotBeImportedEvenIfReturnedByADifferentServiceImplementation() {
        var plan = plan();
        when(plan.groupId()).thenReturn(UUID.randomUUID());
        when(plans.get(owner, sourceId)).thenReturn(plan);
        status(() -> service.create(owner, input(id, "Trip", "Description", sourceId, JournalDates.StartMode.CUSTOM, true)), 404);
        verify(trips, never()).create(any(), any(), any());
    }

    @Test
    void identicalCreateRetriesReturnExistingTripAndChangedRequestsConflict() {
        var request = input(id, "Trip", "Description", null, null, null);
        var saved = service.create(owner, request);
        var fingerprint = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(trips).create(eq(owner), any(), fingerprint.capture());
        when(trips.find(owner, id)).thenReturn(Optional.of(new JournalTripRepository.Stored(saved, fingerprint.getValue())));
        assertThat(service.create(owner, request)).isEqualTo(saved);
        status(() -> service.create(owner, input(id, "Different name", "Description", null, null, null)), 409);
        verify(trips, times(1)).create(any(), any(), any());
        service.create(owner, input(UUID.randomUUID(), "Trip", "Description", null, null, null));
        verify(trips, times(2)).create(any(), any(), any());
    }

    @Test
    void readingEditingAndDeletingCannotAccessAnotherOwnersTrip() {
        UUID other = UUID.randomUUID();
        status(() -> service.get(other, id), 404);
        status(() -> service.update(other, id, input(id, "Trip", "Description", null, null, null)), 404);
        status(() -> service.delete(other, id), 404);
        verify(trips, never()).update(any(), any());
    }

    @Test
    void importedTripsAreEditedIndependentlyAndRetainTheirSourceReference() {
        var old = new JournalTrip(id, "Old", "Description", "Cluj-Napoca", "RO", "Romania", today, today, sourceId, null, null);
        when(trips.find(owner, id)).thenReturn(Optional.of(new JournalTripRepository.Stored(old, "fingerprint")));
        var edited = service.update(owner, id, input(id, "Edited", "Edited description", null, null, null));
        assertThat(edited.title()).isEqualTo("Edited");
        assertThat(edited.sourcePlanId()).isEqualTo(sourceId);
        verify(plans, never()).get(any(), any());
        status(() -> service.update(owner, id, input(id, "Edited", "Description", sourceId, JournalDates.StartMode.CUSTOM, true)), 400);
    }

    private TripPlanResponse plan() {
        var plan = mock(TripPlanResponse.class);
        when(plan.destinationCity()).thenReturn("Cluj-Napoca");
        when(plan.countryCode()).thenReturn("RO");
        when(plan.departureFrom()).thenReturn(today.minusYears(1).atStartOfDay());
        when(plan.departureUntil()).thenReturn(today.minusYears(1).plusDays(3).atStartOfDay());
        when(plan.stayingNights()).thenReturn(4);
        return plan;
    }

    private JournalTripRequest input(UUID request, String title, String description, UUID source, JournalDates.StartMode mode, Boolean keep) {
        return new JournalTripRequest(request, title, description, "Cluj-Napoca", "RO", today, today, source, mode, keep);
    }

    private void status(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value()).isEqualTo(expected));
    }
}