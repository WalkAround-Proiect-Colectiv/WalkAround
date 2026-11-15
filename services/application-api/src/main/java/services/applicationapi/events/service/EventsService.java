package services.applicationapi.events.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.EventCoordinates;
import services.applicationapi.events.model.EventLocalitySnapshot;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.repository.EventLocalitySnapshotRepository;
import services.applicationapi.events.service.exceptions.EventsUnavailableException;
import services.applicationapi.events.service.locality.EventCoordinatesResolver;
import services.applicationapi.events.service.locality.LocalityResolver;
import services.applicationapi.events.service.source.EventSource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Upcoming events for the locality a user is in, served from a two-level cache.
 *
 * <ol>
 *   <li>Server memory, reused while younger than {@code app.events.cache-ttl}.</li>
 *   <li>A per-locality database snapshot, which survives restarts.</li>
 *   <li>Only when both are stale, the event source is called (two SerpApi searches).</li>
 * </ol>
 * If the source fails, the last snapshot is served even when stale, so users still see events.
 * Concurrent requests for the same locality share one fetch.
 */
@Service
public class EventsService {

    public record NearbyEvents(Optional<Locality> locality, List<CachedEvent> events, Instant fetchedAt, boolean stale) {

        static NearbyEvents noLocality() {
            return new NearbyEvents(Optional.empty(), List.of(), null, false);
        }
    }

    private record CacheEntry(Locality locality, List<CachedEvent> events, Instant fetchedAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(EventsService.class);
    private static final TypeReference<List<CachedEvent>> EVENT_LIST = new TypeReference<>() {
    };
    private static final EventCoordinatesResolver NOOP_COORDINATES_RESOLVER =
            (localityName, venueName, address, hintLat, hintLng) -> Optional.empty();

    private final LocalityResolver localityResolver;
    private final EventSource eventSource;
    private final EventLocalitySnapshotRepository snapshots;
    private final EventsProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EventCoordinatesResolver coordinatesResolver;
    private final Map<String, CacheEntry> memory = new ConcurrentHashMap<>();
    private final Map<String, Object> fetchLocks = new ConcurrentHashMap<>();

    @Autowired
    public EventsService(
            LocalityResolver localityResolver,
            EventSource eventSource,
            EventLocalitySnapshotRepository snapshots,
            EventsProperties properties,
            ObjectMapper objectMapper,
            Clock eventsClock,
            EventCoordinatesResolver coordinatesResolver
    ) {
        this.localityResolver = localityResolver;
        this.eventSource = eventSource;
        this.snapshots = snapshots;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = eventsClock;
        this.coordinatesResolver = coordinatesResolver != null ? coordinatesResolver : NOOP_COORDINATES_RESOLVER;
    }

    public EventsService(
            LocalityResolver localityResolver,
            EventSource eventSource,
            EventLocalitySnapshotRepository snapshots,
            EventsProperties properties,
            ObjectMapper objectMapper,
            Clock eventsClock
    ) {
        this(
                localityResolver,
                eventSource,
                snapshots,
                properties,
                objectMapper,
                eventsClock,
                NOOP_COORDINATES_RESOLVER
        );
    }

    public NearbyEvents nearby(double latitude, double longitude) {
        Optional<Locality> resolved = localityResolver.resolve(latitude, longitude);
        if (resolved.isEmpty()) {
            return NearbyEvents.noLocality();
        }
        Locality locality = resolved.get();
        Instant now = clock.instant();

        CacheEntry fresh = freshEntry(locality.key(), now);
        if (fresh != null) {
            return result(locality, fresh, now, false);
        }
        synchronized (fetchLocks.computeIfAbsent(locality.key(), key -> new Object())) {
            fresh = freshEntry(locality.key(), now);
            if (fresh != null) {
                return result(locality, fresh, now, false);
            }
            Optional<EventLocalitySnapshot> snapshot = snapshots.findById(locality.key());
            if (snapshot.isPresent() && isFresh(snapshot.get().getFetchedAt(), now)) {
                List<CachedEvent> rawSnapshotEvents = readEvents(snapshot.get());
                List<CachedEvent> snapshotEvents = attachCoordinates(
                        locality, rawSnapshotEvents, latitude, longitude
                );
                if (!snapshotEvents.equals(rawSnapshotEvents)) {
                    saveSnapshot(locality, snapshot, snapshotEvents, snapshot.get().getFetchedAt());
                }
                CacheEntry entry = new CacheEntry(locality, snapshotEvents, snapshot.get().getFetchedAt());
                memory.put(locality.key(), entry);
                return result(locality, entry, now, false);
            }
            try {
                List<CachedEvent> events = attachCoordinates(
                        locality,
                        EventListBuilder.build(eventSource.search(locality), now, properties.timeZone()),
                        latitude,
                        longitude
                );
                saveSnapshot(locality, snapshot, events, now);
                CacheEntry entry = new CacheEntry(locality, events, now);
                memory.put(locality.key(), entry);
                return result(locality, entry, now, false);
            } catch (EventsUnavailableException exception) {
                if (snapshot.isPresent()) {
                    log.info("Serving stale events for {} because the source is unavailable", locality.key());
                    List<CachedEvent> rawSnapshotEvents = readEvents(snapshot.get());
                    List<CachedEvent> staleEvents = attachCoordinates(
                            locality, rawSnapshotEvents, latitude, longitude
                    );
                    if (!staleEvents.equals(rawSnapshotEvents)) {
                        saveSnapshot(locality, snapshot, staleEvents, snapshot.get().getFetchedAt());
                    }
                    CacheEntry stale = new CacheEntry(locality, staleEvents, snapshot.get().getFetchedAt());
                    return result(locality, stale, now, true);
                }
                throw exception;
            }
        }
    }

    /** Looks an event up in the cached list of a locality, regardless of the cache age. */
    public Optional<CachedEvent> findEvent(String localityKey, String eventKey) {
        CacheEntry entry = memory.get(localityKey);
        List<CachedEvent> events = entry != null
                ? entry.events()
                : snapshots.findById(localityKey).map(this::readEvents).orElse(List.of());
        return events.stream().filter(event -> event.key().equals(eventKey)).findFirst();
    }

    private List<CachedEvent> attachCoordinates(
            Locality locality,
            List<CachedEvent> events,
            double hintLatitude,
            double hintLongitude
    ) {
        return events.stream()
                .map(event -> {
                    if (event.latitude() != null && event.longitude() != null) {
                        return event;
                    }
                    Optional<EventCoordinates> coords = coordinatesResolver.resolve(
                            locality.name(),
                            event.venueName(),
                            event.address(),
                            hintLatitude,
                            hintLongitude
                    );
                    return coords
                            .map(c -> event.withCoordinates(c.latitude(), c.longitude()))
                            .orElse(event);
                })
                .toList();
    }

    private CacheEntry freshEntry(String localityKey, Instant now) {
        CacheEntry entry = memory.get(localityKey);
        return entry != null && isFresh(entry.fetchedAt(), now) ? entry : null;
    }

    private boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt != null && fetchedAt.plus(properties.cacheTtl()).isAfter(now);
    }

    private NearbyEvents result(Locality locality, CacheEntry entry, Instant now, boolean stale) {
        return new NearbyEvents(
                Optional.of(locality),
                EventListBuilder.upcoming(entry.events(), now, properties.timeZone()),
                entry.fetchedAt(),
                stale);
    }

    private void saveSnapshot(Locality locality, Optional<EventLocalitySnapshot> existing, List<CachedEvent> events, Instant now) {
        EventLocalitySnapshot snapshot = existing.orElseGet(() -> new EventLocalitySnapshot(
                locality.key(), locality.name(), locality.county(), locality.countryCode()));
        snapshot.replaceEvents(objectMapper.writeValueAsString(events), now);
        snapshots.save(snapshot);
    }

    private List<CachedEvent> readEvents(EventLocalitySnapshot snapshot) {
        try {
            return objectMapper.readValue(snapshot.getEventsJson(), EVENT_LIST);
        } catch (JacksonException exception) {
            log.warn("Ignoring unreadable events snapshot for {}", snapshot.getLocalityKey());
            return List.of();
        }
    }
}
