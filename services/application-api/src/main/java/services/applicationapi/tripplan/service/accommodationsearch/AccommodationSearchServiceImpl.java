package services.applicationapi.tripplan.service.accommodationsearch;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.configurations.AccommodationSearchProperties;
import services.applicationapi.tripplan.dto.AccommodationBookingOptionResponse;
import services.applicationapi.tripplan.dto.AccommodationOfferResponse;
import services.applicationapi.tripplan.dto.AccommodationSearchRequest;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationSearchProvider.AccommodationDetails;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationSearchProvider.AccommodationOffer;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;

@Service
public class AccommodationSearchServiceImpl implements AccommodationSearchService {

    private static final Logger log = LoggerFactory.getLogger(AccommodationSearchServiceImpl.class);
    private static final String CURRENCY = "EUR";

    private final AccommodationCityValidator cityValidator;
    private final AccommodationSearchProvider provider;
    private final AccommodationSearchProperties properties;

    public AccommodationSearchServiceImpl(
            AccommodationCityValidator cityValidator,
            AccommodationSearchProvider provider,
            AccommodationSearchProperties properties
    ) {
        this.cityValidator = cityValidator;
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public List<AccommodationOfferResponse> search(AccommodationSearchRequest request) {
        LocalDate checkInDate = request.checkInDate();
        if (checkInDate.isBefore(LocalDate.now())) {
            throw new InvalidAccommodationSearchException("Check-in date must not be in the past");
        }

        LocalDate checkOutDate;
        try {
            checkOutDate = checkInDate.plusDays(request.stayingNights());
        } catch (DateTimeException exception) {
            throw new InvalidAccommodationSearchException("Check-out date is out of range");
        }

        String city = request.destinationCity().trim();
        if (city.isEmpty() || city.codePoints().anyMatch(Character::isISOControl)
                || city.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidAccommodationSearchException("Destination city name is invalid");
        }

        String countryCode = request.countryCode().toUpperCase(Locale.ROOT);
        AccommodationCityValidator.ValidatedCity validatedCity = cityValidator.validate(city, countryCode);
        String destination = validatedCity.name() + ", " + validatedCity.countryName();

        List<AccommodationOffer> candidates = provider.search(destination, checkInDate, checkOutDate)
                .stream()
                .filter(offer -> offer.totalPrice().compareTo(request.budget()) <= 0)
                .filter(offer -> offer.propertyToken() != null && !offer.propertyToken().isBlank())
                .sorted(Comparator.comparing(AccommodationOffer::totalPrice))
                .limit(properties.accommodationSearchMaxResults())
                .toList();

        List<AccommodationOfferResponse> offers = new ArrayList<>();
        for (AccommodationOffer offer : candidates) {
            AccommodationDetails details = provider.details(offer.propertyToken(), checkInDate, checkOutDate);
            List<AccommodationBookingOptionResponse> bookingOptions = details.bookingOptions().stream()
                    .filter(option -> option.totalPrice().compareTo(request.budget()) <= 0)
                    .sorted(Comparator.comparing(AccommodationSearchProvider.BookingOption::totalPrice))
                    .limit(properties.accommodationSearchMaxResults())
                    .map(option -> new AccommodationBookingOptionResponse(
                            option.source(), option.totalPrice(), CURRENCY, option.bookingUrl()))
                    .toList();

            if (bookingOptions.isEmpty() && !details.bookingOptions().isEmpty()) {
                log.debug("Skipping accommodation with no booking option within budget");
                continue;
            }

            BigDecimal totalPrice = details.totalPrice() == null
                    ? offer.totalPrice()
                    : details.totalPrice();
            if (!bookingOptions.isEmpty()) {
                totalPrice = bookingOptions.getFirst().totalPrice();
            }
            if (totalPrice.compareTo(request.budget()) > 0) {
                log.debug("Skipping accommodation with a detail price above budget");
                continue;
            }

            offers.add(new AccommodationOfferResponse(
                    offer.name(),
                    offer.propertyType(),
                    totalPrice,
                    CURRENCY,
                    checkInDate,
                    checkOutDate,
                    offer.propertyWebsiteUrl(),
                    details.description(),
                    details.address(),
                    details.overallRating(),
                    details.reviews(),
                    details.amenities(),
                    details.roomTypes(),
                    bookingOptions
            ));
        }

        List<AccommodationOfferResponse> sorted = offers.stream()
                .sorted(Comparator.comparing(AccommodationOfferResponse::totalPrice))
                .toList();

        log.debug("Enriched {} affordable accommodation offers with {} property detail requests",
                sorted.size(), candidates.size());
        return sorted;
    }
}
