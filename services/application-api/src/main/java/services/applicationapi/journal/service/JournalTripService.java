package services.applicationapi.journal.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.journal.dto.*;
import services.applicationapi.journal.repository.JournalTripRepository;
import services.applicationapi.tripplan.dto.TripPlanResponse;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.exceptions.TripPlanNotFoundException;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;
import services.applicationapi.tripplan.service.savedplan.SavedTripPlanService;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class JournalTripService {
    public record Page(List<JournalTrip> items, Integer nextPage) {}
    private final JournalTripRepository trips;
    private final SavedTripPlanService plans;
    private final CountryRepository countries;
    private final DestinationCityValidator cities;
    private final Clock clock;
    private final ObjectMapper json;

    public JournalTripService(JournalTripRepository trips, SavedTripPlanService plans, CountryRepository countries,
                              DestinationCityValidator cities, @Qualifier("journalClock") Clock clock, ObjectMapper json) {
        this.trips = trips; this.plans = plans; this.countries = countries; this.cities = cities; this.clock = clock; this.json = json;
    }

    public LocalDate today() { return LocalDate.now(clock); }

    @Transactional(readOnly = true)
    public Page list(UUID owner, int page) {
        if (page < 0 || page > 100000) throw failure(HttpStatus.BAD_REQUEST, "Invalid journal page.");
        var rows = trips.list(owner, page);
        return new Page(rows.stream().limit(20).toList(), rows.size() > 20 ? page + 1 : null);
    }

    @Transactional(readOnly = true)
    public JournalTrip get(UUID owner, UUID id) { return required(owner, id).trip(); }

    public JournalTrip create(UUID owner, JournalTripRequest request) {
        if (!trips.lockOwner(owner)) throw failure(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        String fingerprint = fingerprint(request);
        var existing = trips.find(owner, request.requestId());
        if (existing.isPresent()) {
            if (!existing.get().fingerprint().equals(fingerprint)) throw failure(HttpStatus.CONFLICT, "This save request was already used. Please try saving again.");
            return existing.get().trip();
        }
        TripPlanResponse source = null;
        if (request.sourcePlanId() != null) {
            try { source = plans.get(owner, request.sourcePlanId()); }
            catch (TripPlanNotFoundException exception) { throw failure(HttpStatus.NOT_FOUND, "Personal saved plan not found."); }
            if (source.groupId() != null) throw failure(HttpStatus.NOT_FOUND, "Personal saved plan not found.");
        }
        var prepared = prepare(request, source, null);
        try { return trips.create(owner, prepared, fingerprint); }
        catch (DataIntegrityViolationException exception) { throw failure(HttpStatus.CONFLICT, "The trip could not be saved. Refresh and try again."); }
    }

    public JournalTrip update(UUID owner, UUID id, JournalTripRequest request) {
        var current = required(owner, id).trip();
        if (!id.equals(request.requestId()) || request.sourcePlanId() != null || request.startMode() != null || request.keepNights() != null) {
            throw failure(HttpStatus.BAD_REQUEST, "Edit this journal trip independently of its source plan.");
        }
        var prepared = prepare(request, null, current);
        return trips.update(owner, prepared);
    }

    public void delete(UUID owner, UUID id) {
        if (trips.delete(owner, id) == 0) throw failure(HttpStatus.NOT_FOUND, "Journal trip not found.");
    }

    private JournalTrip prepare(JournalTripRequest request, TripPlanResponse source, JournalTrip current) {
        String title = text(request.title(), 120, false, "Trip name");
        String description = text(request.description(), 2000, true, "Description");
        String code = request.countryCode().toUpperCase(Locale.ROOT);
        var country = countries.findByCode(code).orElseThrow(() -> failure(HttpStatus.BAD_REQUEST, "Choose a destination from the planner's supported EU countries."));
        String city = CityNameNormalizer.toLookupName(text(request.destinationCity(), 100, false, "Destination"));
        if (source != null && code.equals(source.countryCode()) && sameCity(city, source.destinationCity())) {
            city = source.destinationCity();
        } else if (current != null && code.equals(current.countryCode()) && sameCity(city, current.destinationCity())) {
            city = current.destinationCity();
        } else {
            try { city = cities.validate(city, code).name(); }
            catch (InvalidFlightSearchException exception) { throw failure(HttpStatus.BAD_REQUEST, "The destination city was not found in the selected country."); }
        }
        var window = source == null ? null : new JournalDates.PlanWindow(source.departureFrom().toLocalDate(), source.departureUntil().toLocalDate(), source.stayingNights());
        var dates = JournalDates.resolve(today(), request.startDate(), request.endDate(), window, request.startMode(), request.keepNights());
        return new JournalTrip(request.requestId(), title, description, city, code, country.getName(), dates.start(), dates.end(),
                current != null ? current.sourcePlanId() : request.sourcePlanId(), null, null);
    }

    private JournalTripRepository.Stored required(UUID owner, UUID id) {
        return trips.find(owner, id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Journal trip not found."));
    }

    private boolean sameCity(String left, String right) {
        return left.equalsIgnoreCase(CityNameNormalizer.toLookupName(right));
    }

    private String text(String value, int limit, boolean multiline, String field) {
        if (value == null || value.isBlank() || value.strip().length() > limit
                || value.codePoints().anyMatch(character -> Character.isISOControl(character)
                    && !(multiline && (character == '\n' || character == '\r' || character == '\t')))) {
            throw failure(HttpStatus.BAD_REQUEST, field + " must contain 1 to " + limit + " valid characters.");
        }
        return value.strip();
    }

    private String fingerprint(JournalTripRequest request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static ResponseStatusException failure(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}