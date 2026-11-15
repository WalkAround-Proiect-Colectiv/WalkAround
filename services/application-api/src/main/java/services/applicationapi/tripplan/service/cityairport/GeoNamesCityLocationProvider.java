package services.applicationapi.tripplan.service.cityairport;

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
import services.applicationapi.tripplan.service.exceptions.CitySearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidCitySelectionException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Component
@ConditionalOnProperty(name = "app.trip-plan.city-provider", havingValue = "geonames", matchIfMissing = true)
public class GeoNamesCityLocationProvider implements CityLocationProvider {

    private static final Logger log = LoggerFactory.getLogger(GeoNamesCityLocationProvider.class);

    private final RestClient restClient;
    private final CityAutocompleteProperties properties;

    public GeoNamesCityLocationProvider(
            @Qualifier("geoNamesRestClient") RestClient restClient,
            CityAutocompleteProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public CityLocation find(String city, String countryCode) {
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
            log.warn("GeoNames city location lookup failed: {}", exception.getClass().getSimpleName());
            throw new CitySearchUnavailableException("City airport lookup is temporarily unavailable", exception);
        }

        if (response == null || response.status() != null || response.geonames() == null) {
            log.warn("GeoNames returned an invalid city location response");
            throw new CitySearchUnavailableException("City airport lookup is temporarily unavailable");
        }

        Set<String> cityKeys = CityNameNormalizer.toComparisonKeys(city);
        return response.geonames().stream()
                .filter(place -> "P".equals(place.fcl()))
                .filter(place -> countryCode.equalsIgnoreCase(place.countryCode()))
                .filter(place -> matchesCity(place, cityKeys))
                .filter(place -> validCoordinates(place.latitude(), place.longitude()))
                .map(place -> new CityLocation(place.latitude(), place.longitude()))
                .findFirst()
                .orElseThrow(() -> new InvalidCitySelectionException(
                        "City was not found in the selected European country"));
    }

    private boolean matchesCity(GeoNamesPlace place, Set<String> cityKeys) {
        return place.name() != null
                && (CityNameNormalizer.toComparisonKeys(place.name()).stream().anyMatch(cityKeys::contains)
                        || place.asciiName() != null
                        && CityNameNormalizer.toComparisonKeys(place.asciiName())
                                .stream().anyMatch(cityKeys::contains));
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
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
            String countryCode,
            String fcl,
            @JsonProperty("lat") Double latitude,
            @JsonProperty("lng") Double longitude
    ) {
    }
}
