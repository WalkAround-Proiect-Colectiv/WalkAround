package services.applicationapi.events.service.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.service.locality.Slugs;
import tools.jackson.databind.ObjectMapper;

/**
 * Replays SerpApi responses recorded on 2026-11-05 from {@code classpath:events/recorded/<town>.json}
 * and per-event detail fixtures from {@code classpath:events/recorded/details/<token>.json}.
 * Makes no network calls, so development and tests do not use the SerpApi monthly quota.
 * Localities or detail tokens without a recording return empty results.
 */
@Component
@ConditionalOnProperty(name = "app.events.provider", havingValue = "recorded")
public class RecordedEventSource implements EventSource {

    private static final Logger log = LoggerFactory.getLogger(RecordedEventSource.class);

    private final ObjectMapper objectMapper;

    public RecordedEventSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RawEvent> search(Locality locality) {
        ClassPathResource resource = new ClassPathResource("events/recorded/" + Slugs.slug(locality.name()) + ".json");
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream input = resource.getInputStream()) {
            SerpApiEventSource.EventsResponse response =
                    objectMapper.readValue(input, SerpApiEventSource.EventsResponse.class);
            if (response.eventsResults() == null) {
                return List.of();
            }
            return response.eventsResults().stream()
                    .filter(result -> result.title() != null && !result.title().isBlank())
                    .map(SerpApiEventSource::toRawEvent)
                    .toList();
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not read recorded events for {}", locality.key(), exception);
            return List.of();
        }
    }

    @Override
    public Optional<RawEventDetails> details(String detailsToken) {
        if (detailsToken == null || detailsToken.isBlank()) {
            return Optional.empty();
        }
        String slug = Slugs.slug(detailsToken);
        if (slug.isBlank()) {
            return Optional.empty();
        }
        ClassPathResource resource = new ClassPathResource("events/recorded/details/" + slug + ".json");
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream input = resource.getInputStream()) {
            SerpApiEventSource.DetailsResponse response =
                    objectMapper.readValue(input, SerpApiEventSource.DetailsResponse.class);
            if (response == null || response.eventDetails() == null) {
                return Optional.empty();
            }
            return Optional.of(SerpApiEventSource.toRawEventDetails(response.eventDetails()));
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not read recorded event details for token {}", slug, exception);
            return Optional.empty();
        }
    }
}
