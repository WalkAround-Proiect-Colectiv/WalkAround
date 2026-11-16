package services.applicationapi.tripplan.service.trainsearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.tripplan.configurations.TrainSearchProperties;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.dto.TrainItineraryResponse;
import services.applicationapi.tripplan.dto.TrainJourneyResponse;
import services.applicationapi.tripplan.dto.TrainSearchRequest;
import services.applicationapi.tripplan.dto.TrainStopResponse;
import services.applicationapi.tripplan.dto.TrainTransferResponse;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidTrainSearchException;
import services.applicationapi.tripplan.service.exceptions.TrainSearchUnavailableException;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;
import services.applicationapi.tripplan.service.trainsearch.TrainSearchProvider.ReturnOffer;
import services.applicationapi.tripplan.service.trainsearch.TrainSearchProvider.SearchPage;
import services.applicationapi.tripplan.service.trainsearch.TrainSearchProvider.TrainOffer;

@Service
public class TrainSearchServiceImpl implements TrainSearchService {

    private static final Logger log = LoggerFactory.getLogger(TrainSearchServiceImpl.class);
    private static final String CURRENCY = "EUR";

    private final CountryRepository countries;
    private final DestinationCityValidator cities;
    private final TrainSearchProvider provider;
    private final RomaniaTrainSearchService romaniaSearch;
    private final TrainSearchProperties properties;

    public TrainSearchServiceImpl(CountryRepository countries, DestinationCityValidator cities,
                                  TrainSearchProvider provider, RomaniaTrainSearchService romaniaSearch,
                                  TrainSearchProperties properties) {
        this.countries = countries;
        this.cities = cities;
        this.provider = provider;
        this.romaniaSearch = romaniaSearch;
        this.properties = properties;
    }

    @Override
    public List<TrainItineraryResponse> search(TrainSearchRequest request) {
        validateInterval(request);
        String originCountry = countryCode(request.originCountryCode());
        String destinationCountry = countryCode(request.countryCode());
        String origin = validateCity(request.originCity(), originCountry);
        String destination = validateCity(request.destinationCity(), destinationCountry);

        boolean romaniaRoute = "RO".equals(originCountry) && "RO".equals(destinationCountry);
        boolean romaniaSupported = romaniaRoute && romaniaSearch.supports(request.ticketOption());
        log.info("Train search routing: romaniaRoute={}, ticketOption={}, romaniaSupported={}",
                romaniaRoute, request.ticketOption(), romaniaSupported);
        if (romaniaRoute && !romaniaSupported) {
            log.debug("Romania train search is unavailable for ticket option {}",
                    request.ticketOption());
        }
        List<TrainItineraryResponse> combined = new ArrayList<>();
        boolean romaniaFailed = false;
        if (romaniaSupported) {
            try {
                combined.addAll(romaniaSearch.search(request, request.originCity().trim(),
                        request.destinationCity().trim()));
            } catch (TrainSearchUnavailableException exception) {
                romaniaFailed = true;
                log.warn("Romania train search unavailable: {}", exception.getClass().getSimpleName());
            }
        }

        boolean omioFailed = false;
        try {
            combined.addAll(searchOmio(request, origin, originCountry, destination,
                    destinationCountry));
        } catch (InvalidTrainSearchException exception) {
            if (!romaniaSupported) throw exception;
            omioFailed = true;
            log.info("Omio does not have the selected Romanian city pair");
        } catch (TrainSearchUnavailableException exception) {
            omioFailed = true;
            log.warn("Omio train search unavailable: {}", exception.getClass().getSimpleName());
        }

        if (omioFailed && (!romaniaSupported || romaniaFailed)) {
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable");
        }
        return combined.stream()
                .sorted(Comparator.comparing(TrainItineraryResponse::totalPrice))
                .toList();
    }

    private List<TrainItineraryResponse> searchOmio(TrainSearchRequest request, String origin,
                                                    String originCountry, String destination,
                                                    String destinationCountry) {
        long originId = provider.findCityId(origin, originCountry);
        long destinationId = provider.findCityId(destination, destinationCountry);
        long budgetMinor = request.budget().movePointRight(2).longValueExact();
        List<TrainItineraryResponse> results = new ArrayList<>();

        if (request.ticketOption() == TicketOption.ROUND_TRIP) {
            searchRoundTrips(request, originId, destinationId, budgetMinor, results);
        } else {
            searchSeparateTickets(request, originId, destinationId, budgetMinor, results);
        }

        List<TrainItineraryResponse> selected = results.stream()
                .sorted(Comparator.comparing(TrainItineraryResponse::totalPrice))
                .limit(properties.trainSearchMaxResults())
                .toList();
        Map<Long, Optional<TrainStopResponse>> stops = new HashMap<>();
        log.debug("Found {} affordable Omio train itineraries; returning {}", results.size(),
                selected.size());
        return selected.stream().map(result -> enrich(result, stops)).toList();
    }

    private void validateInterval(TrainSearchRequest request) {
        if (request.departureUntil().isBefore(request.departureFrom())) {
            throw new InvalidTrainSearchException("departureUntil must be after departureFrom");
        }
        long days = ChronoUnit.DAYS.between(request.departureFrom().toLocalDate(),
                request.departureUntil().toLocalDate()) + 1;
        if (days > properties.trainSearchMaxDateRangeDays()) {
            throw new InvalidTrainSearchException("Departure interval covers too many dates");
        }
        LocalDate today = LocalDate.now();
        if (request.departureFrom().toLocalDate().isBefore(today)
                || request.departureUntil().toLocalDate().isAfter(today.plusDays(364))) {
            throw new InvalidTrainSearchException("Train departure dates must be within the next 364 days");
        }
    }

    private String countryCode(String value) {
        String code = value.toUpperCase(Locale.ROOT);
        if (countries.findByCode(code).isEmpty()) {
            throw new InvalidTrainSearchException("Country code must identify a supported EU country");
        }
        return code;
    }

    private String validateCity(String value, String countryCode) {
        String city = value.trim();
        if (city.codePoints().anyMatch(Character::isISOControl)
                || city.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidTrainSearchException("City name is invalid");
        }
        try {
            return cities.validate(city, countryCode).name();
        } catch (InvalidFlightSearchException exception) {
            throw new InvalidTrainSearchException("City was not found in the selected country");
        } catch (FlightSearchUnavailableException exception) {
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable", exception);
        }
    }

    private void searchRoundTrips(TrainSearchRequest request, long originId, long destinationId,
                                  long budgetMinor, List<TrainItineraryResponse> results) {
        List<RoundTripCandidate> candidates = new ArrayList<>();
        LocalDate lastSupportedDate = LocalDate.now().plusDays(364);

        for (LocalDate date = request.departureFrom().toLocalDate();
                !date.isAfter(request.departureUntil().toLocalDate()); date = date.plusDays(1)) {
            for (int offset = 0; offset <= properties.trainSearchMaxArrivalOffsetDays(); offset++) {
                LocalDate returnDate = date.plusDays(request.stayingNights() + offset);
                if (returnDate.isAfter(lastSupportedDate)) continue;

                for (SearchPage page : pages(originId, destinationId, date, returnDate)) {
                    for (TrainOffer offer : page.offers()) {
                        if (offer.priceMinor() <= budgetMinor
                                && inDepartureWindow(offer, request)
                                && offer.arrival().toLocalDate().plusDays(request.stayingNights())
                                        .equals(returnDate)
                                && offer.tripId() != null && page.searchId() != null) {
                            candidates.add(new RoundTripCandidate(page.searchId(), offer, returnDate));
                        }
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingLong(candidate -> candidate.offer().priceMinor()));
        int detailLookups = 0;
        for (RoundTripCandidate candidate : candidates) {
            if (results.size() >= properties.trainSearchMaxResults()
                    || detailLookups >= properties.trainSearchMaxResults()) break;

            detailLookups++;
            for (ReturnOffer returning : provider.returns(candidate.searchId(), candidate.offer().tripId())) {
                LocalDate returnDate = localDate(returning.journey().departureTime());
                if (candidate.returnDate().equals(returnDate)
                        && returning.totalPriceMinor() <= budgetMinor) {
                    results.add(new TrainItineraryResponse(TicketOption.ROUND_TRIP, CURRENCY,
                            amount(returning.totalPriceMinor()), candidate.offer().journey(),
                            returning.journey(), "OMIO", null, null, null, null, null, null));
                }
            }
        }
        log.debug("Found {} train round-trip candidates with {} detail lookups",
                candidates.size(), detailLookups);
    }

    private void searchSeparateTickets(TrainSearchRequest request, long originId, long destinationId,
                                       long budgetMinor, List<TrainItineraryResponse> results) {
        Map<LocalDate, List<TrainOffer>> returnsByDate = new HashMap<>();
        LocalDate lastSupportedDate = LocalDate.now().plusDays(364);

        for (LocalDate date = request.departureFrom().toLocalDate();
                !date.isAfter(request.departureUntil().toLocalDate()); date = date.plusDays(1)) {
            for (SearchPage page : pages(originId, destinationId, date, null)) {
                for (TrainOffer outbound : page.offers()) {
                    if (outbound.priceMinor() > budgetMinor || !inDepartureWindow(outbound, request)) continue;
                    LocalDate returnDate = outbound.arrival().toLocalDate()
                            .plusDays(request.stayingNights());
                    if (returnDate.isAfter(lastSupportedDate)) continue;

                    List<TrainOffer> returnOffers = returnsByDate.computeIfAbsent(returnDate,
                            day -> pages(destinationId, originId, day, null).stream()
                                    .flatMap(returnPage -> returnPage.offers().stream())
                                    .toList());
                    for (TrainOffer returning : returnOffers) {
                        long totalMinor = outbound.priceMinor() + returning.priceMinor();
                        if (returning.departure().toLocalDate().equals(returnDate)
                                && totalMinor <= budgetMinor) {
                            results.add(new TrainItineraryResponse(TicketOption.TWO_ONE_WAY, CURRENCY,
                                    amount(totalMinor), outbound.journey(), returning.journey(),
                                    "OMIO", null, null, null, null, null, null));
                        }
                    }
                }
            }
        }
    }

    private List<SearchPage> pages(long originId, long destinationId, LocalDate date,
                                   LocalDate returnDate) {
        List<SearchPage> found = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= properties.trainSearchMaxPagesPerDate(); pageNumber++) {
            SearchPage page = provider.search(originId, destinationId, date, returnDate, pageNumber);
            found.add(page);
            if (!page.hasMore()) break;
        }
        return found;
    }

    private boolean inDepartureWindow(TrainOffer offer, TrainSearchRequest request) {
        return !offer.departure().isBefore(request.departureFrom())
                && !offer.departure().isAfter(request.departureUntil());
    }

    private LocalDate localDate(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal amount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private TrainItineraryResponse enrich(TrainItineraryResponse result,
                                          Map<Long, Optional<TrainStopResponse>> stops) {
        return new TrainItineraryResponse(result.ticketOption(), result.currency(), result.totalPrice(),
                enrich(result.outbound(), stops), enrich(result.returning(), stops), result.source(),
                result.originalTotalPrice(), result.originalCurrency(), result.estimatedFare(),
                result.partialCatalog(), result.outboundUrl(),
                result.returnUrl());
    }

    private TrainJourneyResponse enrich(TrainJourneyResponse journey,
                                        Map<Long, Optional<TrainStopResponse>> stops) {
        List<TrainTransferResponse> transfers = journey.transfers().stream()
                .map(transfer -> new TrainTransferResponse(transfer.type(), transfer.afterLeg(),
                        transfer.durationMinutes(), resolve(transfer.fromStop(), stops),
                        resolve(transfer.toStop(), stops)))
                .toList();
        return new TrainJourneyResponse(journey.departureStation(), journey.arrivalStation(),
                journey.departureCity(), journey.arrivalCity(), journey.departureCountryCode(),
                journey.arrivalCountryCode(), journey.departureTime(), journey.arrivalTime(),
                journey.carrier(), journey.carrierLogoUrl(), journey.durationMinutes(),
                journey.stops(), journey.ticketsLeft(), journey.splitTicket(),
                journey.legs(), transfers);
    }

    private TrainStopResponse resolve(TrainStopResponse stop,
                                      Map<Long, Optional<TrainStopResponse>> stops) {
        if (stop == null || stop.positionId() == null) return stop;
        return stops.computeIfAbsent(stop.positionId(), id -> {
            try {
                return Optional.ofNullable(provider.findStop(id));
            } catch (TrainSearchUnavailableException exception) {
                log.warn("Could not resolve train transfer position {}: {}",
                        id, exception.getClass().getSimpleName());
                return Optional.empty();
            }
        }).orElse(stop);
    }

    private record RoundTripCandidate(String searchId, TrainOffer offer, LocalDate returnDate) {
    }
}
