package services.applicationapi.placesearch.service;

import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.placesearch.dto.PlaceSearchResult;

@Component
@ConditionalOnProperty(name = "app.place-search.provider", havingValue = "photon", matchIfMissing = true)
public class PhotonPlaceSearchProvider implements PlaceSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(PhotonPlaceSearchProvider.class);
    private final RestClient restClient;

    public PhotonPlaceSearchProvider(@Qualifier("photonRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<PlaceSearchResult> search(String query, double latitude, double longitude, int zoom, int limit) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/api")
                            .queryParam("q", query)
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("zoom", zoom)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Photon place search failed: {}", exception.getClass().getSimpleName());
            throw new PlaceSearchUnavailableException("Place search is temporarily unavailable", exception);
        }

        JsonNode features = response == null ? null : response.get("features");
        if (features == null || !features.isArray()) {
            throw new PlaceSearchUnavailableException("Place search returned an invalid response");
        }

        List<PlaceSearchResult> results = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            if (!coordinates.isArray() || coordinates.size() < 2
                    || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) {
                continue;
            }

            JsonNode properties = feature.path("properties");
            String name = firstText(properties, "name", "street");
            if (name == null) name = query;
            String detail = detail(properties);
            results.add(new PlaceSearchResult(name, detail,
                    coordinates.get(1).asDouble(), coordinates.get(0).asDouble()));
        }
        return List.copyOf(results);
    }

    private static String detail(JsonNode properties) {
        List<String> parts = new ArrayList<>();
        addDistinct(parts, text(properties, "street"));
        addDistinct(parts, firstText(properties, "city", "locality", "district"));
        addDistinct(parts, text(properties, "state"));
        addDistinct(parts, text(properties, "country"));
        String kind = text(properties, "osm_value");
        if (kind != null && !"yes".equals(kind)) addDistinct(parts, kind.replace('_', ' '));
        return String.join(" · ", parts);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText();
    }

    private static void addDistinct(List<String> parts, String value) {
        if (value != null && !parts.contains(value)) parts.add(value);
    }
}
