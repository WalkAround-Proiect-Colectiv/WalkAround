package services.applicationapi.tripplan.service.attractionsearch;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.configurations.AttractionSearchProperties;
import services.applicationapi.tripplan.dto.AttractionResponse;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationCityValidator;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.AttractionSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidAttractionSearchException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class AttractionSearchServiceImpl implements AttractionSearchService {

    private static final Logger log = LoggerFactory.getLogger(AttractionSearchServiceImpl.class);

    private final AccommodationCityValidator cityValidator;
    private final AttractionSearchProvider provider;
    private final AttractionSearchProperties properties;

    public AttractionSearchServiceImpl(
            AccommodationCityValidator cityValidator,
            AttractionSearchProvider provider,
            AttractionSearchProperties properties
    ) {
        this.cityValidator = cityValidator;
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public List<AttractionResponse> search(String city, String countryCode) {
        if (city == null || city.isBlank() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidAttractionSearchException("City must contain 1 to 100 valid characters");
        }
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidAttractionSearchException("Country code must contain two letters");
        }

        String normalizedCity = CityNameNormalizer.toLookupName(city);
        if (normalizedCity.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidAttractionSearchException("City name is invalid");
        }

        String normalizedCountryCode = countryCode.toUpperCase(Locale.ROOT);
        AccommodationCityValidator.ValidatedCity validated;
        try {
            validated = cityValidator.validate(normalizedCity, normalizedCountryCode);
        } catch (InvalidAccommodationSearchException exception) {
            throw new InvalidAttractionSearchException("City was not found in the selected European country");
        } catch (AccommodationSearchUnavailableException exception) {
            throw new AttractionSearchUnavailableException("Attraction city validation is unavailable", exception);
        }

        List<AttractionResponse> results = provider.search(validated.name(), validated.countryName())
                .stream()
                .filter(attraction -> attraction.name() != null && !attraction.name().isBlank())
                .limit(properties.attractionSearchMaxResults())
                .map(attraction -> new AttractionResponse(
                        attraction.name(),
                        attraction.category(),
                        attraction.address(),
                        attraction.description(),
                        attraction.rating(),
                        attraction.reviews(),
                        attraction.latitude(),
                        attraction.longitude(),
                        attraction.mapsUrl(),
                        attraction.imageUrl(),
                        attraction.websiteUrl()
                ))
                .toList();

        log.debug("Found {} attraction recommendations for country {}", results.size(), normalizedCountryCode);
        return results;
    }
}
