package services.applicationapi.events.service.reminders;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.EventInterest;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes reminders to the {@code event-reminders-topic} Kafka topic, keyed by user id.
 * The realtime service forwards each message to that user's open WebSocket connections.
 */
@Component
public class KafkaReminderPublisher implements ReminderPublisher {

    /** Message sent to the realtime service. {@code userId} selects the recipient. */
    public record EventReminderMessage(
            String type,
            String userId,
            String eventKey,
            String localityKey,
            String title,
            Instant startsAt,
            boolean allDay,
            String venueName
    ) {
    }

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventsProperties properties;

    public KafkaReminderPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            EventsProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(EventInterest interest) {
        String userId = interest.getUser().getId().toString();
        String payload = objectMapper.writeValueAsString(new EventReminderMessage(
                "EVENT_REMINDER", userId, interest.getEventKey(), interest.getLocalityKey(), interest.getTitle(),
                interest.getStartsAt(), interest.isAllDay(), interest.getVenueName()));
        try {
            kafkaTemplate.send(properties.reminderTopic(), userId, payload).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing reminder", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish reminder", exception);
        }
    }
}
