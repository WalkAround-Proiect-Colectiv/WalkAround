package services.applicationapi.tripplan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.airport.repository.AirportRepository;
import services.applicationapi.country.model.Country;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.tripplan.configurations.FlightSearchProperties;
import services.applicationapi.tripplan.dto.FlightItineraryResponse;
import services.applicationapi.tripplan.dto.FlightSearchRequest;
import services.applicationapi.tripplan.dto.FlightSegmentResponse;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.service.FlightSearchProvider.FlightOffer;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;

@Service
public class FlightSearchServiceImpl implements FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchServiceImpl.class);
    private static final DateTimeFormatter FLIGHT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String CURRENCY = "EUR";

    private final AirportRepository airportRepository;
    private final CountryRepository countryRepository;
    private final DestinationCityValidator destinationCityValidator;
    private final FlightCityProvider flightCityProvider;
    private final FlightSearchProvider provider;
    private final FlightSearchProperties properties;

    public FlightSearchServiceImpl(
            AirportRepository airportRepository,
            CountryRepository countryRepository,
            DestinationCityValidator destinationCityValidator,
            FlightCityProvider flightCityProvider,
            FlightSearchProvider provider,
            FlightSearchProperties properties
    ) {
        this.airportRepository = airportRepository;
        this.countryRepository = countryRepository;
        this.destinationCityValidator = destinationCityValidator;
        this.flightCityProvider = flightCityProvider;
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public List<FlightItineraryResponse> search(FlightSearchRequest request) {
        validateInterval(request);

        String origin = request.originAirport().toUpperCase(Locale.ROOT);
        if (!airportRepository.existsByIataCodeIgnoreCase(origin)) {
            log.debug("Rejected unknown origin airport {}", origin);
            throw new InvalidFlightSearchException("Origin airport was not found");
        }

        String countryCode = request.countryCode().toUpperCase(Locale.ROOT);
        Country country = countryRepository.findByCode(countryCode)
                .orElseThrow(() -> {
                    log.debug("Rejected unknown destination country {}", countryCode);
                    return new InvalidFlightSearchException("Country code must identify a supported EU country");
                });

        String city = request.destinationCity().trim();
        if (city.isEmpty() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)
                || city.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidFlightSearchException("Destination city name is invalid");
        }

        DestinationCityValidator.ValidatedCity validatedCity = destinationCityValidator.validate(city, countryCode);
        if (request.budget().compareTo(BigDecimal.ONE) < 0) {
            return List.of();
        }

        String cityCountryName = validatedCity.countryName() == null
                || validatedCity.countryName().isBlank()
                ? country.getName()
                : validatedCity.countryName();
        String cityId = flightCityProvider.findCityId(validatedCity.name(), cityCountryName);
        List<String> destinations = List.of(cityId);

        List<FlightItineraryResponse> results = new ArrayList<>();
        List<RoundTripCandidate> roundTripCandidates = new ArrayList<>();

        for (LocalDate date = request.departureFrom().toLocalDate();
                !date.isAfter(request.departureUntil().toLocalDate());
                date = date.plusDays(1)) {
            LocalDate returnDate = date.plusDays(request.stayingNights());

            if (request.ticketOption() == TicketOption.ROUND_TRIP) {
                roundTripCandidates.addAll(findRoundTripCandidates(
                        request, origin, destinations, date, returnDate));
            } else {
                searchSeparateTickets(request, origin, destinations,
                        date, returnDate, results);
            }
        }

        if (request.ticketOption() == TicketOption.ROUND_TRIP) {
            resolveRoundTrips(request, origin, destinations,
                    roundTripCandidates, results);
        }

        List<FlightItineraryResponse> cheapest = results.stream()
                .sorted(Comparator.comparing(FlightItineraryResponse::totalPrice))
                .limit(properties.flightSearchMaxResults())
                .toList();

        log.debug("Found {} affordable itineraries, returning {}", results.size(), cheapest.size());
        return cheapest;
    }

    private void validateInterval(FlightSearchRequest request) {
        if (request.departureUntil().isBefore(request.departureFrom())) {
            throw new InvalidFlightSearchException("departureUntil must be after departureFrom");
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(
                request.departureFrom().toLocalDate(),
                request.departureUntil().toLocalDate()
        ) + 1;

        if (days > properties.flightSearchMaxDateRangeDays()) {
            throw new InvalidFlightSearchException("Departure interval covers too many dates");
        }
    }

    private List<RoundTripCandidate> findRoundTripCandidates(
            FlightSearchRequest request,
            String origin,
            List<String> destinations,
            LocalDate date,
            LocalDate returnDate
    ) {
        List<FlightOffer> outboundOffers = provider.searchRoundTrip(
                origin, destinations, date, returnDate, request.budget(),
                earliestDeparture(request, date), latestDeparture(request, date));
        List<RoundTripCandidate> candidates = new ArrayList<>();

        for (FlightOffer outbound : outboundOffers) {
            if (isOutbound(outbound, origin, request)
                    && outbound.price().compareTo(request.budget()) <= 0
                    && outbound.departureToken() != null) {
                candidates.add(new RoundTripCandidate(outbound, date, returnDate));
            }
        }

        return candidates;
    }

    private void resolveRoundTrips(
            FlightSearchRequest request,
            String origin,
            List<String> destinations,
            List<RoundTripCandidate> candidates,
            List<FlightItineraryResponse> results
    ) {
        candidates.sort(Comparator.comparing(candidate -> candidate.outbound().price()));
        int returnLookups = 0;
        Set<String> searchedTokens = new HashSet<>();

        for (RoundTripCandidate candidate : candidates) {
            if (results.size() >= properties.flightSearchMaxResults()
                    || returnLookups >= properties.flightSearchMaxResults()) {
                break;
            }

            if (!searchedTokens.add(candidate.outbound().departureToken())) {
                continue;
            }

            returnLookups++;

            List<FlightOffer> returningOffers = provider.searchReturns(
                    origin, destinations, candidate.outboundDate(), candidate.returnDate(),
                    request.budget(), candidate.outbound().departureToken());

            for (FlightOffer returning : returningOffers) {
                if (isReturn(returning, origin)
                        && returning.price().compareTo(request.budget()) <= 0) {
                    results.add(new FlightItineraryResponse(
                            TicketOption.ROUND_TRIP,
                            CURRENCY,
                            returning.price(),
                            candidate.outbound().segments(),
                            returning.segments(),
                            viewUrls(candidate.outbound().viewUrl())
                    ));
                }
            }
        }

        log.debug("Resolved {} round-trip candidates with {} return lookups",
                candidates.size(), returnLookups);
    }

    private void searchSeparateTickets(
            FlightSearchRequest request,
            String origin,
            List<String> destinations,
            LocalDate date,
            LocalDate returnDate,
            List<FlightItineraryResponse> results
    ) {
        List<FlightOffer> outboundOffers = provider.searchOneWay(
                List.of(origin), destinations, date, request.budget(),
                earliestDeparture(request, date), latestDeparture(request, date))
                .stream()
                .filter(offer -> isOutbound(offer, origin, request))
                .filter(offer -> offer.price().compareTo(request.budget()) <= 0)
                .toList();

        if (outboundOffers.isEmpty()) {
            return;
        }

        List<FlightOffer> returningOffers = provider.searchOneWay(
                destinations, List.of(origin), returnDate, request.budget(), null, null);

        for (FlightOffer outbound : outboundOffers) {
            for (FlightOffer returning : returningOffers) {
                BigDecimal total = outbound.price().add(returning.price());

                if (isReturn(returning, origin)
                        && total.compareTo(request.budget()) <= 0) {
                    results.add(new FlightItineraryResponse(
                            TicketOption.TWO_ONE_WAY,
                            CURRENCY,
                            total,
                            outbound.segments(),
                            returning.segments(),
                            viewUrls(outbound.viewUrl(), returning.viewUrl())
                    ));
                }
            }
        }
    }

    private boolean isOutbound(
            FlightOffer offer,
            String origin,
            FlightSearchRequest request
    ) {
        List<FlightSegmentResponse> segments = offer.segments();
        FlightSegmentResponse first = segments.getFirst();
        FlightSegmentResponse last = segments.getLast();

        if (!origin.equals(first.departureAirport()) || last.arrivalAirport() == null) {
            return false;
        }

        try {
            LocalDateTime departure = LocalDateTime.parse(first.departureTime(), FLIGHT_TIME);
            return !departure.isBefore(request.departureFrom())
                    && !departure.isAfter(request.departureUntil());
        } catch (DateTimeParseException | NullPointerException exception) {
            log.debug("SerpApi returned an unparseable flight departure time");
            return false;
        }
    }

    private boolean isReturn(FlightOffer offer, String origin) {
        List<FlightSegmentResponse> segments = offer.segments();
        return segments.getFirst().departureAirport() != null
                && origin.equals(segments.getLast().arrivalAirport());
    }

    private LocalTime earliestDeparture(FlightSearchRequest request, LocalDate date) {
        return date.equals(request.departureFrom().toLocalDate())
                ? request.departureFrom().toLocalTime()
                : LocalTime.MIN;
    }

    private LocalTime latestDeparture(FlightSearchRequest request, LocalDate date) {
        return date.equals(request.departureUntil().toLocalDate())
                ? request.departureUntil().toLocalTime()
                : LocalTime.MAX;
    }

    private List<String> viewUrls(String... urls) {
        return Stream.of(urls)
                .filter(Objects::nonNull)
                .toList();
    }

    private record RoundTripCandidate(
            FlightOffer outbound,
            LocalDate outboundDate,
            LocalDate returnDate
    ) {
    }
}
