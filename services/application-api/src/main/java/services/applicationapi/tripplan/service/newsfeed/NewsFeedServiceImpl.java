package services.applicationapi.tripplan.service.newsfeed;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.tripplan.dto.NewsArticleResponse;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationCityValidator;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidNewsFeedRequestException;
import services.applicationapi.tripplan.service.exceptions.NewsFeedUnavailableException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class NewsFeedServiceImpl implements NewsFeedService {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedServiceImpl.class);

    private final CountryRepository countryRepository;
    private final AccommodationCityValidator cityValidator;
    private final NewsFeedProvider provider;

    public NewsFeedServiceImpl(
            CountryRepository countryRepository,
            AccommodationCityValidator cityValidator,
            NewsFeedProvider provider
    ) {
        this.countryRepository = countryRepository;
        this.cityValidator = cityValidator;
        this.provider = provider;
    }

    @Override
    public List<NewsArticleResponse> get(String city, String countryCode) {
        if (city == null || city.isBlank() || city.length() > 100
                || city.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidNewsFeedRequestException("City must contain 1 to 100 valid characters");
        }
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidNewsFeedRequestException("Country code must contain two letters");
        }

        String normalizedCity = CityNameNormalizer.toLookupName(city);
        if (normalizedCity.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidNewsFeedRequestException("City name is invalid");
        }

        String code = countryCode.toUpperCase(Locale.ROOT);
        if (countryRepository.findByCode(code).isEmpty()) {
            throw new InvalidNewsFeedRequestException("Destination country is not supported");
        }

        AccommodationCityValidator.ValidatedCity validated;
        try {
            validated = cityValidator.validate(normalizedCity, code);
        } catch (InvalidAccommodationSearchException exception) {
            throw new InvalidNewsFeedRequestException("City was not found in the selected country");
        } catch (AccommodationSearchUnavailableException exception) {
            throw new NewsFeedUnavailableException("Destination validation is unavailable", exception);
        }

        log.debug("Loading news for {} in {}", validated.name(), code);
        return provider.search(validated.name(), code);
    }
}
