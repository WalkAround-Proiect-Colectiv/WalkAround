package services.applicationapi.tripplan.service.flightsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.CityAutocompleteProperties;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Component
public class GeoNamesDestinationCityValidator implements DestinationCityValidator {

    private static final Logger log = LoggerFactory.getLogger(GeoNamesDestinationCityValidator.class);

    private final RestClient restClient;
    private final CityAutocompleteProperties properties;

    public GeoNamesDestinationCityValidator(
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
                            .queryParam("maxRows", 100)
                            .queryParam("username", "{username}")
                            .build(Map.of("city", city, "username", properties.geonamesUsername())))
                    .retrieve()
                    .body(GeoNamesResponse.class);
        } catch (RestClientException exception) {
            log.warn("GeoNames destination validation failed: {}", exception.getClass().getSimpleName());
            throw new FlightSearchUnavailableException("Destination city validation is temporarily unavailable", exception);
        }

        if (response == null || response.status() != null || response.geonames() == null) {
            log.warn("GeoNames returned an invalid destination validation response");
            throw new FlightSearchUnavailableException("Destination city validation is temporarily unavailable");
        }

        log.debug("GeoNames returned {} candidate places for destination validation", response.geonames().size());

        var cityKeys = CityNameNormalizer.toComparisonKeys(city);

        return response.geonames().stream()
                .filter(place -> "P".equals(place.fcl()))
                .filter(place -> countryCode.equalsIgnoreCase(place.countryCode()))
                .filter(place -> place.name() != null)
                .filter(place -> matchesCity(place, cityKeys))
                .map(place -> new ValidatedCity(place.name(), place.countryName()))
                .findFirst()
                .orElseThrow(() -> new InvalidFlightSearchException(
                        "Destination city was not found in the selected country"));
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
            String fcl
    ) {
    }
}
