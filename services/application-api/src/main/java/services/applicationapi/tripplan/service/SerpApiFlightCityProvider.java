package services.applicationapi.tripplan.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.FlightSearchProperties;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Component
@ConditionalOnProperty(name = "app.trip-plan.flight-provider", havingValue = "serpapi", matchIfMissing = true)
public class SerpApiFlightCityProvider implements FlightCityProvider {

    private static final Logger log = LoggerFactory.getLogger(SerpApiFlightCityProvider.class);

    private final RestClient restClient;
    private final FlightSearchProperties properties;

    public SerpApiFlightCityProvider(
            @Qualifier("serpApiRestClient") RestClient restClient,
            FlightSearchProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String findCityId(String city, String countryName) {
        AutocompleteResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/search.json")
                            .queryParam("engine", "google_flights_autocomplete")
                            .queryParam("q", "{query}")
                            .queryParam("exclude_regions", true)
                            .queryParam("api_key", "{apiKey}")
                            .build(Map.of(
                                    "query", city + ", " + countryName,
                                    "apiKey", properties.serpApiKey()
                            )))
                    .retrieve()
                    .body(AutocompleteResponse.class);
        } catch (RestClientException exception) {
            log.warn("SerpApi city resolution failed: {}", exception.getClass().getSimpleName());
            throw new FlightSearchUnavailableException("Flight destination lookup is temporarily unavailable", exception);
        }

        if (response == null || response.error() != null || response.suggestions() == null) {
            log.warn("SerpApi returned an invalid city autocomplete response");
            throw new FlightSearchUnavailableException("Flight destination lookup is temporarily unavailable");
        }

        log.debug("SerpApi returned {} city suggestions", response.suggestions().size());

        var cityKeys = CityNameNormalizer.toComparisonKeys(city);
        var countryKeys = CityNameNormalizer.toComparisonKeys(countryName);

        return response.suggestions().stream()
                .filter(suggestion -> "city".equals(suggestion.type()))
                .filter(suggestion -> suggestion.id() != null
                        && (suggestion.id().startsWith("/m/") || suggestion.id().startsWith("/g/")))
                .filter(suggestion -> matchesCityAndCountry(suggestion.name(), cityKeys, countryKeys))
                .map(AutocompleteSuggestion::id)
                .findFirst()
                .orElseThrow(() -> new InvalidFlightSearchException(
                        "Destination city is not available in Google Flights"));
    }

    private boolean matchesCityAndCountry(
            String suggestionName,
            Set<String> cityKeys,
            Set<String> countryKeys
    ) {
        if (suggestionName == null || !suggestionName.contains(",")) {
            return false;
        }

        int firstSeparator = suggestionName.indexOf(',');
        int lastSeparator = suggestionName.lastIndexOf(',');
        String suggestionCity = suggestionName.substring(0, firstSeparator).trim();
        String suggestionCountry = suggestionName.substring(lastSeparator + 1).trim();

        return CityNameNormalizer.toComparisonKeys(suggestionCity)
                .stream().anyMatch(cityKeys::contains)
                && CityNameNormalizer.toComparisonKeys(suggestionCountry)
                        .stream().anyMatch(countryKeys::contains);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AutocompleteResponse(List<AutocompleteSuggestion> suggestions, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AutocompleteSuggestion(String name, String type, String id) {
    }
}
