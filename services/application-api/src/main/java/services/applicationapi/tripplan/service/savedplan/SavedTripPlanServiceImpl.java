package services.applicationapi.tripplan.service.savedplan;

import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.tripplan.configurations.BusSearchProperties;
import services.applicationapi.tripplan.configurations.FlightSearchProperties;
import services.applicationapi.tripplan.configurations.TrainSearchProperties;
import services.applicationapi.tripplan.dto.SaveTripPlanRequest;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.dto.TransportMode;
import services.applicationapi.tripplan.dto.TripPlanResponse;
import services.applicationapi.tripplan.model.TripPlan;
import services.applicationapi.tripplan.repository.TripPlanRepository;
import services.applicationapi.tripplan.service.cityairport.CityAirportService;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidTripPlanException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.tripplan.service.exceptions.TripPlanNotFoundException;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.UserRepository;

@Service
public class SavedTripPlanServiceImpl implements SavedTripPlanService {

    private static final Logger log = LoggerFactory.getLogger(SavedTripPlanServiceImpl.class);

    private final TripPlanRepository tripPlans;
    private final UserRepository users;
    private final CityAirportService cityAirports;
    private final CountryRepository countries;
    private final DestinationCityValidator cityValidator;
    private final FlightSearchProperties flightProperties;
    private final BusSearchProperties busProperties;
    private final TrainSearchProperties trainProperties;

    public SavedTripPlanServiceImpl(
            TripPlanRepository tripPlans,
            UserRepository users,
            CityAirportService cityAirports,
            CountryRepository countries,
            DestinationCityValidator cityValidator,
            FlightSearchProperties flightProperties,
            BusSearchProperties busProperties,
            TrainSearchProperties trainProperties
    ) {
        this.tripPlans = tripPlans;
        this.users = users;
        this.cityAirports = cityAirports;
        this.countries = countries;
        this.cityValidator = cityValidator;
        this.flightProperties = flightProperties;
        this.busProperties = busProperties;
        this.trainProperties = trainProperties;
    }

    @Override
    public TripPlanResponse create(UUID userId, SaveTripPlanRequest request) {
        User user = users.findById(userId)
                .orElseThrow(TripPlanAuthenticationException::new);

        validateDepartureInterval(request);

        String requestedTitle = request.title();
        if (requestedTitle.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidTripPlanException("Title is invalid");
        }

        String title = requestedTitle.strip();
        if (title.isBlank()) {
            throw new InvalidTripPlanException("Title is invalid");
        }

        String description = normalizeDescription(request.description());
        String originAirport = request.originAirport() == null
                ? null : request.originAirport().toUpperCase(Locale.ROOT);

        String departureCountryCode = request.departureCountryCode().toUpperCase(Locale.ROOT);
        if (countries.findByCode(departureCountryCode).isEmpty()) {
            throw new InvalidTripPlanException("Departure country code must identify a supported EU country");
        }

        String departureCity = validateCity(request.departureCity(), departureCountryCode, "Departure");
        if (request.transportMode() == TransportMode.PLANE) {
            if (originAirport == null) {
                throw new InvalidTripPlanException("Origin airport is required for a plane trip");
            }
            String selectedAirport = originAirport;
            boolean airportBelongsToCity = cityAirports.findAirports(departureCity, departureCountryCode)
                    .stream()
                    .anyMatch(airport -> selectedAirport.equalsIgnoreCase(airport.iataCode()));
            if (!airportBelongsToCity) {
                log.debug("Rejected origin airport {} outside selected departure city", originAirport);
                throw new InvalidTripPlanException("Origin airport was not found in the selected departure city");
            }
        } else if (originAirport != null) {
            throw new InvalidTripPlanException("Origin airport is only valid for a plane trip");
        }

        String countryCode = request.countryCode().toUpperCase(Locale.ROOT);
        if (countries.findByCode(countryCode).isEmpty()) {
            throw new InvalidTripPlanException("Country code must identify a supported EU country");
        }

        String destinationCity = validateCity(request.destinationCity(), countryCode, "Destination");
        TripPlan plan = new TripPlan(
                user,
                title,
                description,
                request.transportMode().name(),
                originAirport,
                departureCity,
                departureCountryCode,
                destinationCity,
                countryCode,
                request.departureFrom(),
                request.departureUntil(),
                request.stayingNights(),
                request.ticketOption().name(),
                request.transportBudget(),
                request.accommodationBudget()
        );

        TripPlan saved = tripPlans.save(plan);
        log.info("Saved {} trip plan {} for user {}", request.transportMode(), saved.getId(), userId);
        return toResponse(saved);
    }

    @Override
    public TripPlanResponse get(UUID userId, UUID planId) {
        TripPlan plan = tripPlans.findByIdAndUser_Id(planId, userId)
                .orElseThrow(TripPlanNotFoundException::new);

        return toResponse(plan);
    }

    private String validateCity(String requestedCity, String countryCode, String label) {
        if (requestedCity.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidTripPlanException(label + " city name is invalid");
        }

        String city = CityNameNormalizer.toLookupName(requestedCity);
        if (city.isEmpty()
                || city.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidTripPlanException(label + " city name is invalid");
        }

        DestinationCityValidator.ValidatedCity validatedCity;
        try {
            validatedCity = cityValidator.validate(city, countryCode);
        } catch (InvalidFlightSearchException exception) {
            throw new InvalidTripPlanException(label + " city was not found in the selected country");
        }

        if (validatedCity.name().length() > 100) {
            throw new InvalidTripPlanException(label + " city name is too long");
        }

        return validatedCity.name();
    }

    private void validateDepartureInterval(SaveTripPlanRequest request) {
        if (request.departureUntil().isBefore(request.departureFrom())) {
            throw new InvalidTripPlanException("departureUntil must be after departureFrom");
        }

        long days = ChronoUnit.DAYS.between(
                request.departureFrom().toLocalDate(),
                request.departureUntil().toLocalDate()
        ) + 1;

        int maxDays = switch (request.transportMode()) {
            case PLANE -> flightProperties.flightSearchMaxDateRangeDays();
            case BUS -> busProperties.busSearchMaxDateRangeDays();
            case TRAIN -> trainProperties.trainSearchMaxDateRangeDays();
        };
        if (days > maxDays) {
            throw new InvalidTripPlanException("Departure interval covers too many dates");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String normalized = description.strip();
        if (normalized.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')) {
            throw new InvalidTripPlanException("Description contains invalid characters");
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private TripPlanResponse toResponse(TripPlan plan) {
        return new TripPlanResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getDescription(),
                TransportMode.valueOf(plan.getTransportMode()),
                plan.getOriginAirport(),
                plan.getDepartureCity(),
                plan.getDepartureCountryCode(),
                plan.getDestinationCity(),
                plan.getCountryCode(),
                plan.getDepartureFrom(),
                plan.getDepartureUntil(),
                plan.getStayingNights(),
                TicketOption.valueOf(plan.getTicketOption()),
                plan.getTransportBudget(),
                plan.getAccommodationBudget(),
                plan.getCreatedAt()
        );
    }
}
