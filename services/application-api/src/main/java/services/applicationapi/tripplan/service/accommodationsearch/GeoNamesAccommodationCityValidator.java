package services.applicationapi.tripplan.service.accommodationsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import services.applicationapi.tripplan.configurations.CityAutocompleteProperties;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Component
@ConditionalOnProperty(name = "app.trip-plan.city-provider", havingValue = "geonames", matchIfMissing = true)
public class GeoNamesAccommodationCityValidator implements AccommodationCityValidator {

    private static final Logger log = LoggerFactory.getLogger(GeoNamesAccommodationCityValidator.class);

    private final RestClient restClient;
    private final CityAutocompleteProperties properties;

    public GeoNamesAccommodationCityValidator(
            @Qualifier("geoNamesRestClient") RestClient restClient,
            CityAutocompleteProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public ValidatedCity validate(String city, String countryCode) {
        GeoNamesResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/searchJSON")
                            .queryParam("name", "{city}")
                            .queryParam("country", countryCode)
                            .queryParam("featureClass", "P")
                            .queryParam("continentCode", "EU")
                            .queryParam("maxRows", 100)
                            .queryParam("username", "{username}")
                            .build(Map.of("city", city, "username", properties.geonamesUsername())))
                    .retrieve()
                    .body(GeoNamesResponse.class);
        } catch (RestClientException exception) {
            log.warn("GeoNames accommodation city validation failed: {}", exception.getClass().getSimpleName());
            throw new AccommodationSearchUnavailableException("Accommodation destination validation is unavailable", exception);
        }

        if (response == null || response.status() != null || response.geonames() == null) {
            log.warn("GeoNames returned an invalid accommodation city validation response");
            throw new AccommodationSearchUnavailableException("Accommodation destination validation is unavailable");
        }

        log.debug("GeoNames returned {} candidate places for accommodation validation", response.geonames().size());

        Set<String> cityKeys = CityNameNormalizer.toComparisonKeys(city);
        return response.geonames().stream()
                .filter(place -> "P".equals(place.featureClass()))
                .filter(place -> countryCode.equalsIgnoreCase(place.countryCode()))
                .filter(place -> place.name() != null && place.countryName() != null)
                .filter(place -> matchesCity(place, cityKeys))
                .map(place -> new ValidatedCity(place.name(), place.countryName()))
                .findFirst()
                .orElseThrow(() -> new InvalidAccommodationSearchException(
                        "Destination city was not found in the selected European country"));
    }

    private boolean matchesCity(GeoNamesPlace place, Set<String> cityKeys) {
        if (CityNameNormalizer.toComparisonKeys(place.name()).stream().anyMatch(cityKeys::contains)) {
            return true;
        }

        return place.asciiName() != null
                && CityNameNormalizer.toComparisonKeys(place.asciiName())
                        .stream().anyMatch(cityKeys::contains);
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
            String asciiName,
            String countryName,
            String countryCode,
            @JsonProperty("fcl") String featureClass
    ) {
    }
}
