package services.applicationapi.tripplan.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.CityAutocompleteProperties;
import services.applicationapi.tripplan.dto.CitySuggestion;

@Component
@ConditionalOnProperty(name = "app.trip-plan.city-provider", havingValue = "geonames", matchIfMissing = true)
public class GeoNamesCitySearchProvider implements CitySearchProvider {

    private static final Logger log = LoggerFactory.getLogger(GeoNamesCitySearchProvider.class);

    private final RestClient restClient;
    private final CityAutocompleteProperties properties;

    public GeoNamesCitySearchProvider(
            @Qualifier("geoNamesRestClient") RestClient restClient,
            CityAutocompleteProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<CitySuggestion> searchByPrefix(String prefix, int limit) {
        GeoNamesResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/searchJSON")
                            .queryParam("name_startsWith", "{prefix}")
                            .queryParam("featureClass", "P")
                            .queryParam("continentCode", "EU")
                            .queryParam("maxRows", limit)
                            .queryParam("username", "{username}")
                            .build(Map.of(
                                    "prefix", prefix,
                                    "username", properties.geonamesUsername()
                            )))
                    .retrieve()
                    .body(GeoNamesResponse.class);
        } catch (RestClientException exception) {
            log.warn("GeoNames city search request failed: {}", exception.getClass().getSimpleName());
            throw new CitySearchUnavailableException("City suggestions are temporarily unavailable", exception);
        }

        if (response == null || response.status() != null || response.geonames() == null) {
            log.warn("GeoNames returned an invalid city search response, status={}",
                    response == null || response.status() == null ? "missing" : response.status().value());
            throw new CitySearchUnavailableException("City suggestions are temporarily unavailable");
        }

        List<CitySuggestion> suggestions = response.geonames().stream()
                .filter(place -> "P".equals(place.fcl()))
                .filter(place -> place.name() != null && place.countryName() != null)
                .map(place -> new CitySuggestion(
                        place.name(),
                        place.countryName(),
                        place.countryCode(),
                        place.lat(),
                        place.lng()
                ))
                .toList();

        log.debug("GeoNames returned {} places and {} city suggestions",
                response.geonames().size(), suggestions.size());
        return suggestions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesResponse(List<GeoNamesPlace> geonames, GeoNamesStatus status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesStatus(int value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesPlace(
            String name,
            String countryName,
            String countryCode,
            String fcl,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }
}
