package services.applicationapi.tripplan.service.savedplan;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.tripplan.configurations.BusSearchProperties;
import services.applicationapi.tripplan.configurations.FlightSearchProperties;
import services.applicationapi.tripplan.configurations.TrainSearchProperties;
import services.applicationapi.tripplan.dto.SaveTripPlanRequest;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.dto.TransportMode;
import services.applicationapi.tripplan.dto.TripPlanResponse;
import services.applicationapi.tripplan.model.TripPlan;
import services.applicationapi.tripplan.model.TripPlanGroup;
import services.applicationapi.tripplan.model.TripPlanGroupMember;
import services.applicationapi.tripplan.repository.TripPlanGroupMemberRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupRepository;
import services.applicationapi.tripplan.repository.TripPlanRepository;
import services.applicationapi.tripplan.service.cityairport.CityAirportService;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidTripPlanException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.tripplan.service.exceptions.TripPlanNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripGroupForbiddenException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.UserRepository;

@Service
public class SavedTripPlanServiceImpl implements SavedTripPlanService {

    private static final Logger log = LoggerFactory.getLogger(SavedTripPlanServiceImpl.class);

    private final TripPlanRepository tripPlans;
    private final TripPlanGroupRepository groups;
    private final TripPlanGroupMemberRepository members;
    private final UserRepository users;
    private final CityAirportService cityAirports;
    private final CountryRepository countries;
    private final DestinationCityValidator cityValidator;
    private final FlightSearchProperties flightProperties;
    private final BusSearchProperties busProperties;
    private final TrainSearchProperties trainProperties;

    public SavedTripPlanServiceImpl(
            TripPlanRepository tripPlans,
            TripPlanGroupRepository groups,
            TripPlanGroupMemberRepository members,
            UserRepository users,
            CityAirportService cityAirports,
            CountryRepository countries,
            DestinationCityValidator cityValidator,
            FlightSearchProperties flightProperties,
            BusSearchProperties busProperties,
            TrainSearchProperties trainProperties
    ) {
        this.tripPlans = tripPlans;
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.cityAirports = cityAirports;
        this.countries = countries;
        this.cityValidator = cityValidator;
        this.flightProperties = flightProperties;
        this.busProperties = busProperties;
        this.trainProperties = trainProperties;
    }

    @Override
    @Transactional
    public TripPlanResponse create(UUID userId, SaveTripPlanRequest request) {
        return createPlan(userId, null, request);
    }

    @Override
    @Transactional
    public TripPlanResponse createForGroup(UUID groupId, UUID userId, SaveTripPlanRequest request) {
        requireMember(groupId, userId);
        return createPlan(userId, groupId, request);
    }

    private TripPlanResponse createPlan(UUID userId, UUID groupId, SaveTripPlanRequest request) {
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
        TripPlanGroup group = groupId == null ? null : groups.findByIdForUpdate(groupId)
                .orElseThrow(TripGroupNotFoundException::new);
        boolean current = group != null && !tripPlans.existsByGroup_IdAndCurrentTrue(groupId);
        TripPlan plan = new TripPlan(
                group == null ? user : null,
                group,
                group == null ? null : user,
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
                request.adults(),
                request.ticketOption().name(),
                request.transportBudget(),
                request.accommodationBudget(),
                current
        );

        TripPlan saved = tripPlans.save(plan);
        log.info("Saved {} trip plan {} for user {} in group {}", request.transportMode(),
                saved.getId(), userId, groupId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TripPlanResponse get(UUID userId, UUID planId) {
        TripPlan plan = tripPlans.findByIdAndUser_IdAndGroupIsNull(planId, userId)
                .orElseThrow(TripPlanNotFoundException::new);

        return toResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public TripPlanResponse getForGroup(UUID groupId, UUID userId, UUID planId) {
        requireMember(groupId, userId);
        return toResponse(tripPlans.findByIdAndGroup_Id(planId, groupId)
                .orElseThrow(TripPlanNotFoundException::new));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripPlanResponse> listForGroup(UUID groupId, UUID userId) {
        requireMember(groupId, userId);
        return tripPlans.findByGroup_IdOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public TripPlanResponse makeCurrent(UUID groupId, UUID adminId, UUID planId) {
        groups.findByIdForUpdate(groupId).orElseThrow(TripGroupNotFoundException::new);
        TripPlanGroupMember member = requireMember(groupId, adminId);
        if (!"ADMIN".equals(member.getRole())) throw new TripGroupForbiddenException();
        if (tripPlans.findByIdAndGroup_Id(planId, groupId).isEmpty()) throw new TripPlanNotFoundException();

        tripPlans.clearCurrent(groupId);
        TripPlan plan = tripPlans.findByIdAndGroup_Id(planId, groupId)
                .orElseThrow(TripPlanNotFoundException::new);
        plan.setCurrent(true);
        log.info("Selected trip plan {} for group {} by admin {}", planId, groupId, adminId);
        return toResponse(plan);
    }

    private TripPlanGroupMember requireMember(UUID groupId, UUID userId) {
        return members.findByGroup_IdAndUser_Id(groupId, userId)
                .orElseThrow(TripGroupNotFoundException::new);
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
                plan.getGroup() == null ? null : plan.getGroup().getId(),
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
                plan.getAdults(),
                TicketOption.valueOf(plan.getTicketOption()),
                plan.getTransportBudget(),
                plan.getAccommodationBudget(),
                plan.isCurrent(),
                plan.getCreatedAt()
        );
    }
}
