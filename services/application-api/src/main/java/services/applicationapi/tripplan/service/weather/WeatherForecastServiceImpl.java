package services.applicationapi.tripplan.service.weather;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.dto.DestinationWeatherResponse;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationCityValidator;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidWeatherForecastRequestException;
import services.applicationapi.tripplan.service.exceptions.WeatherForecastUnavailableException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class WeatherForecastServiceImpl implements WeatherForecastService {

    private static final Logger log = LoggerFactory.getLogger(WeatherForecastServiceImpl.class);

    private final AccommodationCityValidator cityValidator;
    private final WeatherForecastProvider provider;

    public WeatherForecastServiceImpl(
            AccommodationCityValidator cityValidator,
            WeatherForecastProvider provider
    ) {
        this.cityValidator = cityValidator;
        this.provider = provider;
    }

    @Override
    public DestinationWeatherResponse get(String city, String countryCode) {
        if (city == null || city.isBlank() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidWeatherForecastRequestException("City must contain 1 to 100 valid characters");
        }
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidWeatherForecastRequestException("Country code must contain two letters");
        }

        String normalizedCity = CityNameNormalizer.toLookupName(city);
        if (normalizedCity.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidWeatherForecastRequestException("City name is invalid");
        }

        String code = countryCode.toUpperCase(Locale.ROOT);
        AccommodationCityValidator.ValidatedCity validated;
        try {
            validated = cityValidator.validate(normalizedCity, code);
        } catch (InvalidAccommodationSearchException exception) {
            throw new InvalidWeatherForecastRequestException("City was not found in the selected European country");
        } catch (AccommodationSearchUnavailableException exception) {
            throw new WeatherForecastUnavailableException("Destination validation is unavailable", exception);
        }

        if (validated.latitude() == null || validated.longitude() == null
                || !Double.isFinite(validated.latitude()) || !Double.isFinite(validated.longitude())
                || Math.abs(validated.latitude()) > 90 || Math.abs(validated.longitude()) > 180) {
            log.warn("GeoNames returned invalid weather coordinates");
            throw new WeatherForecastUnavailableException("Destination validation is unavailable");
        }

        log.debug("Loading weather for {}, {}", validated.name(), code);
        return provider.forecast(new WeatherForecastProvider.Location(
                validated.name(), validated.countryName(), code,
                validated.latitude(), validated.longitude()));
    }
}
