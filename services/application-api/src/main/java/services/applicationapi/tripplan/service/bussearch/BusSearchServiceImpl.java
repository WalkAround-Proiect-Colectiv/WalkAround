package services.applicationapi.tripplan.service.bussearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import services.applicationapi.tripplan.configurations.BusSearchProperties;
import services.applicationapi.tripplan.dto.BusItineraryResponse;
import services.applicationapi.tripplan.dto.BusJourneyResponse;
import services.applicationapi.tripplan.dto.BusSearchRequest;
import services.applicationapi.tripplan.dto.BusStopResponse;
import services.applicationapi.tripplan.dto.BusTransferResponse;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.service.bussearch.BusSearchProvider.BusOffer;
import services.applicationapi.tripplan.service.bussearch.BusSearchProvider.ReturnOffer;
import services.applicationapi.tripplan.service.bussearch.BusSearchProvider.SearchPage;
import services.applicationapi.tripplan.service.exceptions.BusSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidBusSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;

@Service
public class BusSearchServiceImpl implements BusSearchService {

    private static final Logger log = LoggerFactory.getLogger(BusSearchServiceImpl.class);
    private static final String CURRENCY = "EUR";

    private final CountryRepository countries;
    private final DestinationCityValidator cities;
    private final BusSearchProvider provider;
    private final BusSearchProperties properties;

    public BusSearchServiceImpl(CountryRepository countries, DestinationCityValidator cities,
                                BusSearchProvider provider, BusSearchProperties properties) {
        this.countries = countries;
        this.cities = cities;
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public List<BusItineraryResponse> search(BusSearchRequest request) {
        validateInterval(request);
        String originCountry = countryCode(request.originCountryCode());
        String destinationCountry = countryCode(request.countryCode());
        String originName = validateCity(request.originCity(), originCountry);
        String destinationName = validateCity(request.destinationCity(), destinationCountry);

        long originId = provider.findCityId(originName, originCountry);
        long destinationId = provider.findCityId(destinationName, destinationCountry);
        long budgetMinor = request.budget().movePointRight(2).longValueExact();
        List<BusItineraryResponse> results = new ArrayList<>();

        if (request.ticketOption() == TicketOption.ROUND_TRIP) {
            searchRoundTrips(request, originId, destinationId, budgetMinor, results);
        } else {
            searchSeparateTickets(request, originId, destinationId, budgetMinor, results);
        }

        List<BusItineraryResponse> selected = results.stream()
                .sorted(Comparator.comparing(BusItineraryResponse::totalPrice))
                .limit(properties.busSearchMaxResults())
                .toList();

        Map<Long, Optional<BusStopResponse>> resolvedStops = new HashMap<>();
        return selected.stream()
                .map(result -> enrich(result, resolvedStops))
                .toList();
    }

    private void validateInterval(BusSearchRequest request) {
        if (request.departureUntil().isBefore(request.departureFrom())) {
            throw new InvalidBusSearchException("departureUntil must be after departureFrom");
        }
        long dates = ChronoUnit.DAYS.between(request.departureFrom().toLocalDate(),
                request.departureUntil().toLocalDate()) + 1;
        if (dates > properties.busSearchMaxDateRangeDays()) {
            throw new InvalidBusSearchException("Departure interval covers too many dates");
        }
        LocalDate today = LocalDate.now();
        if (request.departureFrom().toLocalDate().isBefore(today)
                || request.departureUntil().toLocalDate().isAfter(today.plusDays(364))) {
            throw new InvalidBusSearchException("Bus departure dates must be within the next 364 days");
        }
    }

    private String countryCode(String value) {
        String code = value.toUpperCase(Locale.ROOT);
        if (countries.findByCode(code).isEmpty()) {
            throw new InvalidBusSearchException("Country code must identify a supported EU country");
        }
        return code;
    }

    private String validateCity(String value, String countryCode) {
        String city = value.trim();
        if (city.codePoints().anyMatch(Character::isISOControl)
                || city.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidBusSearchException("City name is invalid");
        }
        try {
            return cities.validate(city, countryCode).name();
        } catch (InvalidFlightSearchException exception) {
            throw new InvalidBusSearchException("City was not found in the selected country");
        } catch (FlightSearchUnavailableException exception) {
            throw new BusSearchUnavailableException("Bus search is temporarily unavailable", exception);
        }
    }

    private void searchRoundTrips(BusSearchRequest request, long originId, long destinationId,
                                  long budgetMinor, List<BusItineraryResponse> results) {
        List<RoundTripCandidate> candidates = new ArrayList<>();
        LocalDate lastSupportedDate = LocalDate.now().plusDays(364);

        for (LocalDate date = request.departureFrom().toLocalDate();
                !date.isAfter(request.departureUntil().toLocalDate()); date = date.plusDays(1)) {
            for (int offset = 0; offset <= properties.busSearchMaxArrivalOffsetDays(); offset++) {
                LocalDate returnDate = date.plusDays(request.stayingNights() + offset);
                if (returnDate.isAfter(lastSupportedDate)) continue;

                for (SearchPage page : pages(originId, destinationId, date, returnDate, request.adults())) {
                    for (BusOffer offer : page.offers()) {
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
            if (results.size() >= properties.busSearchMaxResults()
                    || detailLookups >= properties.busSearchMaxResults()) break;

            detailLookups++;
            for (ReturnOffer returning : provider.returns(candidate.searchId(), candidate.offer().tripId())) {
                LocalDate returnDate = localDate(returning.journey().departureTime());
                if (returnDate != null && returnDate.equals(candidate.returnDate())
                        && returning.totalPriceMinor() <= budgetMinor) {
                    results.add(new BusItineraryResponse(TicketOption.ROUND_TRIP, CURRENCY,
                            amount(returning.totalPriceMinor()), candidate.offer().journey(),
                            returning.journey()));
                }
            }
        }
        log.debug("Found {} bus round-trip candidates with {} detail lookups",
                candidates.size(), detailLookups);
    }

    private void searchSeparateTickets(BusSearchRequest request, long originId, long destinationId,
                                       long budgetMinor, List<BusItineraryResponse> results) {
        Map<LocalDate, List<BusOffer>> returnsByDate = new HashMap<>();
        LocalDate lastSupportedDate = LocalDate.now().plusDays(364);

        for (LocalDate date = request.departureFrom().toLocalDate();
                !date.isAfter(request.departureUntil().toLocalDate()); date = date.plusDays(1)) {
            for (SearchPage page : pages(originId, destinationId, date, null, request.adults())) {
                for (BusOffer outbound : page.offers()) {
                    if (outbound.priceMinor() > budgetMinor || !inDepartureWindow(outbound, request)) {
                        continue;
                    }
                    LocalDate returnDate = outbound.arrival().toLocalDate()
                            .plusDays(request.stayingNights());
                    if (returnDate.isAfter(lastSupportedDate)) continue;

                    List<BusOffer> returningOffers = returnsByDate.computeIfAbsent(returnDate,
                            day -> pages(destinationId, originId, day, null, request.adults()).stream()
                                    .flatMap(returnPage -> returnPage.offers().stream())
                                    .toList());

                    for (BusOffer returning : returningOffers) {
                        long totalMinor = outbound.priceMinor() + returning.priceMinor();
                        if (returning.departure().toLocalDate().equals(returnDate)
                                && totalMinor <= budgetMinor) {
                            results.add(new BusItineraryResponse(TicketOption.TWO_ONE_WAY, CURRENCY,
                                    amount(totalMinor), outbound.journey(), returning.journey()));
                        }
                    }
                }
            }
        }
    }

    private List<SearchPage> pages(long originId, long destinationId, LocalDate date,
                                   LocalDate returnDate, int adults) {
        List<SearchPage> found = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= properties.busSearchMaxPagesPerDate(); pageNumber++) {
            SearchPage page = provider.search(originId, destinationId, date, returnDate, pageNumber, adults);
            found.add(page);
            if (!page.hasMore()) break;
        }
        return found;
    }

    private boolean inDepartureWindow(BusOffer offer, BusSearchRequest request) {
        return !offer.departure().isBefore(request.departureFrom())
                && !offer.departure().isAfter(request.departureUntil());
    }

    private LocalDate localDate(String value) {
        if (value == null) return null;
        try {
            return java.time.OffsetDateTime.parse(value).toLocalDate();
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal amount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private BusItineraryResponse enrich(BusItineraryResponse result,
                                        Map<Long, Optional<BusStopResponse>> resolvedStops) {
        return new BusItineraryResponse(result.ticketOption(), result.currency(),
                result.totalPrice(), enrich(result.outbound(), resolvedStops),
                enrich(result.returning(), resolvedStops));
    }

    private BusJourneyResponse enrich(BusJourneyResponse journey,
                                      Map<Long, Optional<BusStopResponse>> resolvedStops) {
        List<BusTransferResponse> transfers = journey.transfers().stream()
                .map(transfer -> new BusTransferResponse(transfer.type(), transfer.afterLeg(),
                        transfer.durationMinutes(), resolve(transfer.fromStop(), resolvedStops),
                        resolve(transfer.toStop(), resolvedStops)))
                .toList();

        return new BusJourneyResponse(journey.departureStation(), journey.arrivalStation(),
                journey.departureCity(), journey.arrivalCity(),
                journey.departureCountryCode(), journey.arrivalCountryCode(),
                journey.departureTime(), journey.arrivalTime(), journey.carrier(),
                journey.carrierLogoUrl(), journey.durationMinutes(), journey.stops(),
                journey.ticketsLeft(), journey.splitTicket(), journey.legs(), transfers);
    }

    private BusStopResponse resolve(BusStopResponse stop,
                                    Map<Long, Optional<BusStopResponse>> resolvedStops) {
        if (stop == null || stop.positionId() == null) return stop;
        return resolvedStops.computeIfAbsent(stop.positionId(), id -> {
            try {
                return Optional.ofNullable(provider.findStop(id));
            } catch (BusSearchUnavailableException exception) {
                log.warn("Could not resolve bus transfer position {}: {}",
                        id, exception.getClass().getSimpleName());
                return Optional.empty();
            }
        }).orElse(stop);
    }

    private record RoundTripCandidate(String searchId, BusOffer offer, LocalDate returnDate) {
    }
}
