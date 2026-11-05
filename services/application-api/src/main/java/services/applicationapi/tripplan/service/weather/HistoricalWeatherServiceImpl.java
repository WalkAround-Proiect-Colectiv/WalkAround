package services.applicationapi.tripplan.service.weather;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.configurations.HistoricalWeatherProperties;
import services.applicationapi.tripplan.dto.HistoricalWeatherResponse;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationCityValidator;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.HistoricalWeatherUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidHistoricalWeatherRequestException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class HistoricalWeatherServiceImpl implements HistoricalWeatherService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalWeatherServiceImpl.class);

    private final AccommodationCityValidator cityValidator;
    private final HistoricalWeatherProvider provider;
    private final HistoricalWeatherProperties properties;

    public HistoricalWeatherServiceImpl(
            AccommodationCityValidator cityValidator,
            HistoricalWeatherProvider provider,
            HistoricalWeatherProperties properties
    ) {
        this.cityValidator = cityValidator;
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public HistoricalWeatherResponse get(String city, String countryCode, String checkInDate, String stayingNights) {
        if (city == null || city.isBlank() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidHistoricalWeatherRequestException("City must contain 1 to 100 valid characters");
        }
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidHistoricalWeatherRequestException("Country code must contain two letters");
        }

        String normalizedCity = CityNameNormalizer.toLookupName(city);
        if (normalizedCity.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidHistoricalWeatherRequestException("City name is invalid");
        }

        LocalDate date;
        int nights;
        try {
            date = LocalDate.parse(checkInDate);
            nights = Integer.parseInt(stayingNights);
        } catch (DateTimeException | NumberFormatException | NullPointerException exception) {
            throw new InvalidHistoricalWeatherRequestException("Enter an ISO check-in date and 1 to 365 staying nights");
        }
        if (date.isBefore(LocalDate.now()) || nights < 1 || nights > 365) {
            throw new InvalidHistoricalWeatherRequestException("Check-in must be today or later, with 1 to 365 staying nights");
        }

        String code = countryCode.toUpperCase(Locale.ROOT);
        AccommodationCityValidator.ValidatedCity validated;
        try {
            validated = cityValidator.validate(normalizedCity, code);
        } catch (InvalidAccommodationSearchException exception) {
            throw new InvalidHistoricalWeatherRequestException("City was not found in the selected European country");
        } catch (AccommodationSearchUnavailableException exception) {
            throw new HistoricalWeatherUnavailableException("Destination validation is unavailable", exception);
        }

        if (validated.latitude() == null || validated.longitude() == null
                || !Double.isFinite(validated.latitude()) || !Double.isFinite(validated.longitude())
                || Math.abs(validated.latitude()) > 90 || Math.abs(validated.longitude()) > 180) {
            log.warn("GeoNames returned invalid historical weather coordinates");
            throw new HistoricalWeatherUnavailableException("Destination validation is unavailable");
        }

        LocalDate latestAvailable = LocalDate.now().minusDays(5);
        int offset = Math.max(1, date.getYear() - latestAvailable.getYear());
        while (!date.minusYears(offset).plusDays(nights - 1L).isBefore(latestAvailable)) {
            offset++;
        }

        List<HistoricalWeatherResponse.Year> years = new ArrayList<>();
        String timezone = null;
        for (int index = 0; index < properties.weatherHistoryYears(); index++) {
            LocalDate start = date.minusYears(offset + index);
            LocalDate end = start.plusDays(nights - 1L);
            HistoricalWeatherProvider.Period period = provider.history(validated.latitude(), validated.longitude(), start, end);
            if (timezone != null && !timezone.equals(period.timezone())) {
                throw new HistoricalWeatherUnavailableException("Historical weather is temporarily unavailable");
            }
            timezone = period.timezone();
            years.add(new HistoricalWeatherResponse.Year(start.getYear(), start, end, period.days()));
        }

        log.debug("Loaded {} historical stays for {}, {}", years.size(), validated.name(), code);
        return new HistoricalWeatherResponse(
                validated.name(), validated.countryName(), code, timezone, date, nights, List.copyOf(years));
    }
}
