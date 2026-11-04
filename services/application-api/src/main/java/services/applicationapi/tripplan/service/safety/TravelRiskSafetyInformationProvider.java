package services.applicationapi.tripplan.service.safety;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.tripplan.configurations.SafetyInformationProperties;
import services.applicationapi.tripplan.dto.DestinationSafetyResponse;
import services.applicationapi.tripplan.service.exceptions.SafetyInformationUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.safety-provider", havingValue = "travel-risk-api", matchIfMissing = true)
public class TravelRiskSafetyInformationProvider implements SafetyInformationProvider {

    private static final Logger log = LoggerFactory.getLogger(TravelRiskSafetyInformationProvider.class);
    private static final int PROVIDER_PAGE_LIMIT = 1000;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final RestClient restClient;
    private final SafetyInformationProperties properties;

    public TravelRiskSafetyInformationProvider(
            @Qualifier("travelRiskRestClient") RestClient restClient,
            SafetyInformationProperties properties
    ) {
        if (properties.travelRiskApiKey() == null || properties.travelRiskApiKey().isBlank()) {
            throw new IllegalArgumentException("TRAVEL_RISK_API_KEY must be configured");
        }
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public DestinationSafetyResponse fetch(Location location) {
        AdvisoryListResponse advisories = get("advisories", location.countryCode(), false, AdvisoryListResponse.class);
        AlertListResponse alerts = get("alerts", location.countryCode(), true, AlertListResponse.class);
        ConflictListResponse conflicts = get("conflicts", location.countryCode(), true, ConflictListResponse.class);

        if (advisories == null || advisories.data() == null
                || alerts == null || alerts.data() == null || alerts.total() == null
                || conflicts == null || conflicts.data() == null || conflicts.total() == null) {
            log.warn("Travel Risk API returned an incomplete safety response");
            throw new SafetyInformationUnavailableException("Safety information is temporarily unavailable");
        }

        List<DestinationSafetyResponse.Alert> nearbyAlerts = alerts.data().stream()
                .filter(Objects::nonNull)
                .filter(alert -> withinRadius(location, alert.latitude(), alert.longitude()))
                .map(alert -> new DestinationSafetyResponse.Alert(
                        alert.id(), alert.type(), alert.severity(), alert.location(), alert.description(),
                        alert.eventDate(), alert.source(), distanceKm(location, alert.latitude(), alert.longitude())))
                .sorted(Comparator.comparingInt((DestinationSafetyResponse.Alert alert) -> severityRank(alert.severity()))
                        .reversed().thenComparingDouble(DestinationSafetyResponse.Alert::distanceKm))
                .toList();

        List<DestinationSafetyResponse.Conflict> nearbyConflicts = conflicts.data().stream()
                .filter(Objects::nonNull)
                .filter(conflict -> withinRadius(location, conflict.latitude(), conflict.longitude()))
                .map(conflict -> new DestinationSafetyResponse.Conflict(
                        conflict.id(), conflict.severity(), conflict.location(), conflict.description(),
                        conflict.eventDate(), safeUrl(conflict.sourceUrl()),
                        distanceKm(location, conflict.latitude(), conflict.longitude())))
                .sorted(Comparator.comparingInt((DestinationSafetyResponse.Conflict conflict) -> severityRank(conflict.severity()))
                        .reversed().thenComparingDouble(DestinationSafetyResponse.Conflict::distanceKm))
                .toList();

        DestinationSafetyResponse.Advisory advisory = advisories.data().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::toAdvisory)
                .orElse(null);

        boolean alertsPartial = alerts.total() > alerts.data().size()
                || nearbyAlerts.size() > properties.safetyMaxResults();
        boolean conflictsPartial = conflicts.total() > conflicts.data().size()
                || nearbyConflicts.size() > properties.safetyMaxResults();
        log.debug("Safety data for {}: {} nearby alerts, {} nearby conflicts, partial alerts={}, partial conflicts={}",
                location.countryCode(), nearbyAlerts.size(), nearbyConflicts.size(), alertsPartial, conflictsPartial);

        return new DestinationSafetyResponse(
                location.city(), location.country(), location.countryCode(), properties.safetyNearbyRadiusKm(),
                Instant.now(), advisory,
                nearbyAlerts.stream().limit(properties.safetyMaxResults()).toList(),
                nearbyConflicts.stream().limit(properties.safetyMaxResults()).toList(),
                alertsPartial, conflictsPartial);
    }

    private <T> T get(String resource, String countryCode, boolean limited, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(builder -> {
                        var request = builder.path("/api/v1/" + resource)
                                .queryParam("country_iso", countryCode);
                        if (limited) {
                            request = request.queryParam("limit", PROVIDER_PAGE_LIMIT);
                        }
                        return request.build();
                    })
                    .header("X-API-Key", properties.travelRiskApiKey())
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException exception) {
            log.warn("Travel Risk API {} returned HTTP {}", resource, exception.getStatusCode().value());
            throw new SafetyInformationUnavailableException("Safety information is temporarily unavailable", exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Travel Risk API {} request failed: {}", resource, exception.getClass().getSimpleName());
            throw new SafetyInformationUnavailableException("Safety information is temporarily unavailable", exception);
        }
    }

    private DestinationSafetyResponse.Advisory toAdvisory(AdvisoryEntry entry) {
        List<DestinationSafetyResponse.Source> sources = entry.sources() == null ? List.of() : entry.sources().stream()
                .filter(Objects::nonNull)
                .map(source -> new DestinationSafetyResponse.Source(
                        source.agency(), source.level(), source.summary(), safeUrl(source.url()), source.updatedAt()))
                .toList();
        return new DestinationSafetyResponse.Advisory(entry.maxLevel(), entry.summary(), sources);
    }

    private boolean withinRadius(Location location, Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
                && rawDistanceKm(location, latitude, longitude) <= properties.safetyNearbyRadiusKm();
    }

    private double distanceKm(Location location, double latitude, double longitude) {
        return Math.round(rawDistanceKm(location, latitude, longitude) * 10.0) / 10.0;
    }

    private double rawDistanceKm(Location location, double latitude, double longitude) {
        double latitudeDelta = Math.toRadians(latitude - location.latitude());
        double longitudeDelta = Math.toRadians(longitude - location.longitude());
        double arc = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(location.latitude())) * Math.cos(Math.toRadians(latitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(arc)));
    }

    private int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private String safeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (query == null || !query.toLowerCase(Locale.ROOT).contains("api_key"))) {
                return uri.toString();
            }
        } catch (IllegalArgumentException exception) {
            log.debug("Travel Risk API returned an invalid source link");
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdvisoryListResponse(List<AdvisoryEntry> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdvisoryEntry(
            @JsonProperty("max_level") Integer maxLevel,
            @JsonProperty("summary_label") String summary,
            List<AdvisorySource> sources
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdvisorySource(
            String agency,
            Integer level,
            @JsonProperty("summary_label") String summary,
            String url,
            @JsonProperty("updated_at") String updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlertListResponse(Integer total, List<AlertEntry> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlertEntry(
            Long id,
            @JsonProperty("alert_type") String type,
            String severity,
            String location,
            String description,
            @JsonProperty("event_date") String eventDate,
            String source,
            Double latitude,
            Double longitude
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConflictListResponse(Integer total, List<ConflictEntry> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConflictEntry(
            Long id,
            String severity,
            String location,
            String description,
            @JsonProperty("event_date") String eventDate,
            @JsonProperty("source_url") String sourceUrl,
            Double latitude,
            Double longitude
    ) {
    }
}
