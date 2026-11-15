package services.applicationapi.events.service.locality;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.service.exceptions.EventsUnavailableException;

/**
 * Reverse geocoding with the public OpenStreetMap Nominatim service.
 *
 * <p>Follows the Nominatim usage policy: coordinates are rounded to about 1 km before sending (no exact
 * user position leaves the server), results are cached, requests identify the app via User-Agent, and
 * calls are serialized to at most one per second for the whole application.
 */
@Component
public class NominatimLocalityResolver implements LocalityResolver {

    private static final Logger log = LoggerFactory.getLogger(NominatimLocalityResolver.class);
    private static final long MIN_INTERVAL_MILLIS = 1100;
    private static final int MAX_CACHE_ENTRIES = 5000;

    private final RestClient restClient;
    private final Map<String, Optional<Locality>> cache = new ConcurrentHashMap<>();
    private final Object rateLock = new Object();
    private long lastCallMillis;

    public NominatimLocalityResolver(@Qualifier("nominatimRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<Locality> resolve(double latitude, double longitude) {
        double lat = round(latitude);
        double lng = round(longitude);
        String cacheKey = lat + "," + lng;
        Optional<Locality> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<Locality> resolved = lookup(lat, lng);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(cacheKey, resolved);
        return resolved;
    }

    private Optional<Locality> lookup(double lat, double lng) {
        NominatimResponse response;
        synchronized (rateLock) {
            waitForRateLimit();
            try {
                response = restClient.get()
                        .uri(builder -> builder.path("/reverse")
                                .queryParam("lat", String.format(Locale.ROOT, "%.2f", lat))
                                .queryParam("lon", String.format(Locale.ROOT, "%.2f", lng))
                                .queryParam("format", "jsonv2")
                                .queryParam("zoom", 10)
                                .queryParam("accept-language", "en")
                                .build())
                        .retrieve()
                        .body(NominatimResponse.class);
            } catch (RestClientException exception) {
                log.warn("Nominatim reverse lookup failed: {}", exception.getMessage());
                throw new EventsUnavailableException(exception);
            } finally {
                lastCallMillis = System.currentTimeMillis();
            }
        }
        if (response == null || response.address() == null) {
            return Optional.empty();
        }
        Address address = response.address();
        String name = firstNonBlank(address.city(), address.town(), address.village(), address.municipality());
        if (name == null) {
            return Optional.empty();
        }
        String countryCode = address.countryCode() == null ? null : address.countryCode().toUpperCase(Locale.ROOT);
        return Optional.of(new Locality(
                Slugs.localityKey(countryCode, address.county(), name), name, address.county(), countryCode));
    }

    private void waitForRateLimit() {
        long wait = lastCallMillis + MIN_INTERVAL_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new EventsUnavailableException(exception);
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimResponse(Address address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(
            String city,
            String town,
            String village,
            String municipality,
            String county,
            @JsonProperty("country_code") String countryCode
    ) {
    }
}
