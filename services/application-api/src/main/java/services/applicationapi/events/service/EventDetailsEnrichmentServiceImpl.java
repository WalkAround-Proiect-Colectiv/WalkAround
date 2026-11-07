package services.applicationapi.events.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.EventDetailsCacheEntry;
import services.applicationapi.events.repository.EventDetailsCacheRepository;
import services.applicationapi.events.service.source.EventSource;
import services.applicationapi.events.service.source.RawEventDetails;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class EventDetailsEnrichmentServiceImpl implements EventDetailsEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EventDetailsEnrichmentServiceImpl.class);
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("\\b20\\d{2}\\b");

    private final EventSource eventSource;
    private final EventDetailsCacheRepository cacheRepository;
    private final EventsProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Object> fetchLocks = new ConcurrentHashMap<>();

    public EventDetailsEnrichmentServiceImpl(
            EventSource eventSource,
            EventDetailsCacheRepository cacheRepository,
            EventsProperties properties,
            ObjectMapper objectMapper,
            Clock eventsClock
    ) {
        this.eventSource = eventSource;
        this.cacheRepository = cacheRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = eventsClock;
    }

    @Override
    public EnrichmentResult enrich(String localityKey, CachedEvent event) {
        if (event == null) {
            return EnrichmentResult.basic();
        }
        Instant now = clock.instant();
        Optional<EnrichmentResult> cached = fromCache(event.key(), now);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (!properties.detailsEnabled()
                || event.detailsToken() == null
                || event.detailsToken().isBlank()
                || isAlreadyComplete(event)) {
            return EnrichmentResult.basic();
        }

        synchronized (fetchLocks.computeIfAbsent(event.key(), key -> new Object())) {
            cached = fromCache(event.key(), now);
            if (cached.isPresent()) {
                return cached.get();
            }
            try {
                Optional<RawEventDetails> fetched = eventSource.details(event.detailsToken());
                if (fetched.isPresent()) {
                    RawEventDetails details = fetched.get();
                    saveCacheEntry(
                            event.key(),
                            localityKey,
                            EventDetailsCacheEntry.STATUS_OK,
                            writeDetails(details),
                            now,
                            now.plus(properties.detailsCacheTtl())
                    );
                    return EnrichmentResult.complete(details);
                }
                saveCacheEntry(
                        event.key(),
                        localityKey,
                        EventDetailsCacheEntry.STATUS_EMPTY,
                        null,
                        now,
                        now.plus(properties.detailsFailureTtl())
                );
                return EnrichmentResult.basic();
            } catch (RuntimeException exception) {
                log.warn("Event details enrichment failed for {}: {}",
                        event.key(), exception.getClass().getSimpleName());
                saveCacheEntry(
                        event.key(),
                        localityKey,
                        EventDetailsCacheEntry.STATUS_FAILED,
                        null,
                        now,
                        now.plus(properties.detailsFailureTtl())
                );
                return EnrichmentResult.unavailable();
            }
        }
    }

    @Override
    public Optional<RawEventDetails> findCached(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return fromCache(eventKey, now).flatMap(EnrichmentResult::details);
    }

    @Scheduled(fixedDelayString = "PT6H")
    @Transactional
    public void purgeExpiredCache() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(1));
        cacheRepository.deleteExpiredBefore(cutoff);
    }

    private Optional<EnrichmentResult> fromCache(String eventKey, Instant now) {
        Optional<EventDetailsCacheEntry> existing = cacheRepository.findById(eventKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        EventDetailsCacheEntry entry = existing.get();
        if (entry.getExpiresAt() == null || !entry.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return switch (entry.getStatus()) {
            case EventDetailsCacheEntry.STATUS_OK -> readDetails(entry)
                    .map(EnrichmentResult::complete);
            case EventDetailsCacheEntry.STATUS_FAILED -> Optional.of(EnrichmentResult.unavailable());
            case EventDetailsCacheEntry.STATUS_EMPTY -> Optional.of(EnrichmentResult.basic());
            default -> Optional.empty();
        };
    }

    private void saveCacheEntry(
            String eventKey,
            String localityKey,
            String status,
            String detailsJson,
            Instant fetchedAt,
            Instant expiresAt
    ) {
        try {
            Optional<EventDetailsCacheEntry> existing = cacheRepository.findById(eventKey);
            EventDetailsCacheEntry entry = existing.orElseGet(() -> new EventDetailsCacheEntry(
                    eventKey, localityKey, status, detailsJson, fetchedAt, expiresAt
            ));
            if (existing.isPresent()) {
                entry.update(localityKey, status, detailsJson, fetchedAt, expiresAt);
            }
            cacheRepository.save(entry);
        } catch (RuntimeException exception) {
            log.warn("Could not persist event details cache entry for {}: {}",
                    eventKey, exception.getClass().getSimpleName());
        }
    }

    private String writeDetails(RawEventDetails details) {
        return objectMapper.writeValueAsString(details);
    }

    private Optional<RawEventDetails> readDetails(EventDetailsCacheEntry entry) {
        if (entry.getDetailsJson() == null || entry.getDetailsJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(entry.getDetailsJson(), RawEventDetails.class));
        } catch (JacksonException exception) {
            log.warn("Ignoring unreadable event details cache row for {}", entry.getEventKey());
            return Optional.empty();
        }
    }

    private static boolean isAlreadyComplete(CachedEvent event) {
        return hasText(event.description())
                && hasText(event.address())
                && hasYear(event.whenText())
                && event.tickets() != null
                && !event.tickets().isEmpty();
    }

    private static boolean hasYear(String text) {
        return text != null && FOUR_DIGIT_YEAR.matcher(text).find();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
