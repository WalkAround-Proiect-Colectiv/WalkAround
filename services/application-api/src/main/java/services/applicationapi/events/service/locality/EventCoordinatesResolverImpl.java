package services.applicationapi.events.service.locality;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.EventCoordinates;
import services.applicationapi.events.repository.EventVenueCoordinateRepository;
import services.applicationapi.placesearch.dto.PlaceSearchResult;
import services.applicationapi.placesearch.service.PlaceSearchProvider;

@Service
public class EventCoordinatesResolverImpl implements EventCoordinatesResolver {

    private static final Logger log = LoggerFactory.getLogger(EventCoordinatesResolverImpl.class);
    private static final double DEFAULT_ROMANIA_LAT = 45.9432;
    private static final double DEFAULT_ROMANIA_LON = 24.9668;
    private static final int DEFAULT_PHOTON_ZOOM = 13;
    private static final int MAX_CACHE_ENTRIES = 5000;

    private final EventVenueCoordinateRepository venueCoordinateRepository;
    private final PlaceSearchProvider placeSearchProvider;
    private final EventsProperties properties;
    private final Map<String, Optional<EventCoordinates>> cache = new ConcurrentHashMap<>();

    public EventCoordinatesResolverImpl(
            EventVenueCoordinateRepository venueCoordinateRepository,
            PlaceSearchProvider placeSearchProvider,
            EventsProperties properties
    ) {
        this.venueCoordinateRepository = venueCoordinateRepository;
        this.placeSearchProvider = placeSearchProvider;
        this.properties = properties;
    }

    @Override
    public Optional<EventCoordinates> resolve(
            String localityName,
            String venueName,
            String address,
            Double hintLatitude,
            Double hintLongitude
    ) {
        String candidate = hasText(venueName) ? venueName.strip() : (hasText(address) ? address.strip() : null);
        if (candidate == null || candidate.length() < 2) {
            return Optional.empty();
        }

        String cacheKey = Slugs.slug(localityName) + "|" + Slugs.slug(candidate);
        Optional<EventCoordinates> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Optional<EventCoordinates> resolved = lookup(localityName, venueName, address, hintLatitude, hintLongitude);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(cacheKey, resolved);
        return resolved;
    }

    private Optional<EventCoordinates> lookup(
            String localityName,
            String venueName,
            String address,
            Double hintLatitude,
            Double hintLongitude
    ) {
        // 1. Primary lookup: PostGIS osm.features by venue name (or address if venue name is absent).
        String postgisTarget = hasText(venueName) ? venueName.strip() : (hasText(address) ? address.strip() : null);
        if (postgisTarget != null) {
            Optional<EventCoordinates> fromPostgis = venueCoordinateRepository.findVenueCoordinates(
                    postgisTarget, hintLatitude, hintLongitude
            );
            if (fromPostgis.isPresent()) {
                return fromPostgis;
            }
        }

        // 2. Fallback: Photon place search (skipped in recorded mode so offline tests make no network calls).
        if ("recorded".equalsIgnoreCase(properties.provider()) || placeSearchProvider == null) {
            return Optional.empty();
        }

        String query = buildPhotonQuery(localityName, venueName, address);
        if (query == null || query.length() < 2) {
            return Optional.empty();
        }

        double lat = (hintLatitude != null && Double.isFinite(hintLatitude)) ? hintLatitude : DEFAULT_ROMANIA_LAT;
        double lon = (hintLongitude != null && Double.isFinite(hintLongitude)) ? hintLongitude : DEFAULT_ROMANIA_LON;

        try {
            List<PlaceSearchResult> results = placeSearchProvider.search(query, lat, lon, DEFAULT_PHOTON_ZOOM, 1);
            if (results != null && !results.isEmpty()) {
                PlaceSearchResult best = results.getFirst();
                return Optional.of(new EventCoordinates(best.latitude(), best.longitude()));
            }
        } catch (RuntimeException exception) {
            log.debug("Photon fallback coordinate lookup failed: {}", exception.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private static String buildPhotonQuery(String localityName, String venueName, String address) {
        String base = hasText(venueName) ? venueName.strip() : (hasText(address) ? address.strip() : null);
        if (base == null) {
            return null;
        }
        if (hasText(localityName) && !base.toLowerCase().contains(localityName.strip().toLowerCase())) {
            String combined = base + ", " + localityName.strip();
            return combined.length() <= 120 ? combined : base.substring(0, Math.min(120, base.length()));
        }
        return base.length() <= 120 ? base : base.substring(0, 120);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
