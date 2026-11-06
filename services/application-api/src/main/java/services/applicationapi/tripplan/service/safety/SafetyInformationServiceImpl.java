package services.applicationapi.tripplan.service.safety;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.dto.DestinationSafetyResponse;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationCityValidator;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidSafetyInformationException;
import services.applicationapi.tripplan.service.exceptions.SafetyInformationUnavailableException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class SafetyInformationServiceImpl implements SafetyInformationService {

    private static final Logger log = LoggerFactory.getLogger(SafetyInformationServiceImpl.class);

    private final AccommodationCityValidator cityValidator;
    private final SafetyInformationProvider provider;

    public SafetyInformationServiceImpl(AccommodationCityValidator cityValidator, SafetyInformationProvider provider) {
        this.cityValidator = cityValidator;
        this.provider = provider;
    }

    @Override
    public DestinationSafetyResponse get(String city, String countryCode) {
        if (city == null || city.isBlank() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidSafetyInformationException("City must contain 1 to 100 valid characters");
        }
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidSafetyInformationException("Country code must contain two letters");
        }

        String normalizedCity = CityNameNormalizer.toLookupName(city);
        if (normalizedCity.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidSafetyInformationException("City name is invalid");
        }

        String normalizedCountryCode = countryCode.toUpperCase(Locale.ROOT);
        AccommodationCityValidator.ValidatedCity validated;
        try {
            validated = cityValidator.validate(normalizedCity, normalizedCountryCode);
        } catch (InvalidAccommodationSearchException exception) {
            throw new InvalidSafetyInformationException("City was not found in the selected European country");
        } catch (AccommodationSearchUnavailableException exception) {
            throw new SafetyInformationUnavailableException("Destination validation is unavailable", exception);
        }

        if (validated.latitude() == null || validated.longitude() == null
                || !Double.isFinite(validated.latitude()) || !Double.isFinite(validated.longitude())
                || Math.abs(validated.latitude()) > 90 || Math.abs(validated.longitude()) > 180) {
            log.warn("GeoNames returned invalid coordinates for destination safety");
            throw new SafetyInformationUnavailableException("Destination validation is unavailable");
        }

        log.debug("Loading safety information for country {}", normalizedCountryCode);
        return provider.fetch(new SafetyInformationProvider.Location(
                validated.name(), validated.countryName(), normalizedCountryCode,
                validated.latitude(), validated.longitude()));
    }
}
