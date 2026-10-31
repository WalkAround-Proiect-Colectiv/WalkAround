package services.applicationapi.tripplan.service;

import java.util.List;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.configurations.CityAutocompleteProperties;
import services.applicationapi.tripplan.dto.CitySuggestion;

@Service
public class CityAutocompleteServiceImpl implements CityAutocompleteService {

    private final CitySearchProvider citySearchProvider;
    private final CityAutocompleteProperties properties;

    public CityAutocompleteServiceImpl(
            CitySearchProvider citySearchProvider,
            CityAutocompleteProperties properties
    ) {
        this.citySearchProvider = citySearchProvider;
        this.properties = properties;
    }

    @Override
    public List<CitySuggestion> autocomplete(String city) {
        if (city == null) {
            throw new InvalidCityQueryException("City is required");
        }

        String prefix = city.trim();

        if (prefix.isEmpty() || prefix.length() > 100 || prefix.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidCityQueryException("City must contain 1 to 100 characters without control characters");
        }

        return citySearchProvider.searchByPrefix(prefix, properties.cityAutocompleteMaxResults());
    }
}
