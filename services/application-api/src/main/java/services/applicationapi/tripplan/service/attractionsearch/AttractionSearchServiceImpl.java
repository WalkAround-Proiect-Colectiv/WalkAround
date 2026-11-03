package services.applicationapi.tripplan.service.attractionsearch;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.dto.AttractionResponse;
import services.applicationapi.tripplan.dto.AttractionPageResponse;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationCityValidator;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.AttractionSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidAttractionSearchException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class AttractionSearchServiceImpl implements AttractionSearchService {

    private static final Logger log = LoggerFactory.getLogger(AttractionSearchServiceImpl.class);
    private static final int MAX_PAGE = 5;

    private final AccommodationCityValidator cityValidator;
    private final AttractionSearchProvider provider;

    public AttractionSearchServiceImpl(
            AccommodationCityValidator cityValidator,
            AttractionSearchProvider provider
    ) {
        this.cityValidator = cityValidator;
        this.provider = provider;
    }

    @Override
    public AttractionPageResponse search(String city, String countryCode, int page) {
        if (city == null || city.isBlank() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidAttractionSearchException("City must contain 1 to 100 valid characters");
        }
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidAttractionSearchException("Country code must contain two letters");
        }
        if (page < 0 || page > MAX_PAGE) {
            throw new InvalidAttractionSearchException("Page must be between 0 and 5");
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

        if (validated.latitude() == null || validated.longitude() == null
                || !Double.isFinite(validated.latitude()) || !Double.isFinite(validated.longitude())
                || Math.abs(validated.latitude()) > 90 || Math.abs(validated.longitude()) > 180) {
            log.warn("GeoNames returned invalid coordinates for attraction search");
            throw new AttractionSearchUnavailableException("Attraction city validation is unavailable");
        }

        AttractionSearchProvider.Page providerPage = provider.search(
                validated.name(), validated.countryName(), validated.latitude(), validated.longitude(), page);
        List<AttractionResponse> results = providerPage.items()
                .stream()
                .filter(attraction -> attraction.name() != null && !attraction.name().isBlank())
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

        Integer nextPage = providerPage.hasNext() && page < MAX_PAGE ? page + 1 : null;
        log.debug("Found {} attraction recommendations for country {} on page {}", results.size(), normalizedCountryCode, page);
        return new AttractionPageResponse(results, nextPage);
    }
}
