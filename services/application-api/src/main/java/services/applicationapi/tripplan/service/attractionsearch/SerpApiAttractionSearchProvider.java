package services.applicationapi.tripplan.service.attractionsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.AttractionSearchProperties;
import services.applicationapi.tripplan.service.exceptions.AttractionSearchUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.attraction-provider", havingValue = "serpapi", matchIfMissing = true)
public class SerpApiAttractionSearchProvider implements AttractionSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SerpApiAttractionSearchProvider.class);
    private static final String MAPS_SEARCH_URL = "https://www.google.com/maps/search/?api=1&query=";

    private final RestClient restClient;
    private final AttractionSearchProperties properties;

    public SerpApiAttractionSearchProvider(
            @Qualifier("serpApiRestClient") RestClient restClient,
            AttractionSearchProperties properties
    ) {
        if (properties.serpApiKey() == null || properties.serpApiKey().isBlank()) {
            throw new IllegalArgumentException("SERP_API_KEY must be configured");
        }

        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<Attraction> search(String city, String country) {
        SerpApiResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/search.json")
                            .queryParam("engine", "google_maps")
                            .queryParam("type", "search")
                            .queryParam("q", "{query}")
                            .queryParam("hl", "en")
                            .queryParam("api_key", "{apiKey}")
                            .build(Map.of(
                                    "query", "top-rated tourist attractions in " + city + ", " + country,
                                    "apiKey", properties.serpApiKey()
                            )))
                    .retrieve()
                    .body(SerpApiResponse.class);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("SerpApi attraction search failed: {}", exception.getClass().getSimpleName());
            throw new AttractionSearchUnavailableException("Attraction search is temporarily unavailable", exception);
        }

        if (response == null || response.searchMetadata() == null
                || !"Success".equals(response.searchMetadata().status())) {
            log.warn("SerpApi returned an unsuccessful attraction search response (status={})",
                    response == null || response.searchMetadata() == null
                            ? "missing" : response.searchMetadata().status());
            throw new AttractionSearchUnavailableException("Attraction search is temporarily unavailable");
        }

        if (response.localResults() == null) {
            log.debug("SerpApi attraction search returned no local results");
            return List.of();
        }

        List<Attraction> attractions = response.localResults().stream()
                .filter(Objects::nonNull)
                .filter(result -> result.title() != null && !result.title().isBlank())
                .map(result -> new Attraction(
                        result.title(),
                        result.type(),
                        result.address(),
                        result.description(),
                        result.rating(),
                        result.reviews(),
                        latitude(result.gpsCoordinates()),
                        longitude(result.gpsCoordinates()),
                        mapsUrl(result, city, country),
                        imageUrl(result),
                        safeExternalUrl(result.website())
                ))
                .toList();

        log.debug("SerpApi returned {} attraction candidates", attractions.size());
        return attractions;
    }

    private Double latitude(GpsCoordinates coordinates) {
        if (coordinates == null || coordinates.latitude() == null) {
            return null;
        }

        double value = coordinates.latitude();
        return Double.isFinite(value) && value >= -90 && value <= 90 ? value : null;
    }

    private Double longitude(GpsCoordinates coordinates) {
        if (coordinates == null || coordinates.longitude() == null) {
            return null;
        }

        double value = coordinates.longitude();
        return Double.isFinite(value) && value >= -180 && value <= 180 ? value : null;
    }

    private String mapsUrl(LocalResult result, String city, String country) {
        String query = result.title() + ", " + city + ", " + country;
        String url = MAPS_SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8);

        if (result.placeId() != null && !result.placeId().isBlank()) {
            url += "&query_place_id=" + URLEncoder.encode(result.placeId(), StandardCharsets.UTF_8);
        }

        return url;
    }

    private String imageUrl(LocalResult result) {
        String preferred = safeExternalUrl(result.serpapiThumbnail());
        return preferred != null ? preferred : safeExternalUrl(result.thumbnail());
    }

    private String safeExternalUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (query == null || !query.toLowerCase(Locale.ROOT).contains("api_key"))) {
                return uri.toString();
            }
        } catch (IllegalArgumentException exception) {
            log.debug("SerpApi returned an invalid attraction link");
        }

        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiResponse(
            @JsonProperty("search_metadata") SearchMetadata searchMetadata,
            @JsonProperty("local_results") List<LocalResult> localResults
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchMetadata(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocalResult(
            String title,
            String type,
            String address,
            String description,
            BigDecimal rating,
            Integer reviews,
            @JsonProperty("place_id") String placeId,
            @JsonProperty("gps_coordinates") GpsCoordinates gpsCoordinates,
            String thumbnail,
            @JsonProperty("serpapi_thumbnail") String serpapiThumbnail,
            String website
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GpsCoordinates(Double latitude, Double longitude) {
    }
}
