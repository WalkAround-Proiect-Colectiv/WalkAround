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
import tools.jackson.databind.ObjectMapper;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.service.locality.Slugs;

/**
 * Replays SerpApi responses recorded on 2026-11-05 from {@code classpath:events/recorded/<town>.json}.
 * Makes no network calls, so development and tests do not use the SerpApi monthly quota.
 * Localities without a recording return no events.
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
        return Optional.empty();
    }
}
