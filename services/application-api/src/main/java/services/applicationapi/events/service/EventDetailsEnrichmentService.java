package services.applicationapi.events.service;

import java.util.Optional;
import services.applicationapi.events.dto.EnrichmentStatus;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.service.source.RawEventDetails;

/**
 * Fetches and caches extra event details per {@code eventKey} so repeat views and marking an already
 * viewed event cost zero additional provider searches.
 */
public interface EventDetailsEnrichmentService {

    record EnrichmentResult(Optional<RawEventDetails> details, EnrichmentStatus status) {

        public static EnrichmentResult complete(RawEventDetails details) {
            return new EnrichmentResult(Optional.of(details), EnrichmentStatus.COMPLETE);
        }

        public static EnrichmentResult basic() {
            return new EnrichmentResult(Optional.empty(), EnrichmentStatus.BASIC);
        }

        public static EnrichmentResult unavailable() {
            return new EnrichmentResult(Optional.empty(), EnrichmentStatus.UNAVAILABLE);
        }
    }

    /** Enriches a cached event with provider details, never throwing on provider failures. */
    EnrichmentResult enrich(String localityKey, CachedEvent event);

    /** Returns non-expired cached details for an event key if present. */
    Optional<RawEventDetails> findCached(String eventKey);
}
