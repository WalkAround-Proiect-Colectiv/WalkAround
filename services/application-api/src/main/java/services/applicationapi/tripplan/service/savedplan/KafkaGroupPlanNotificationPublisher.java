package services.applicationapi.tripplan.service.savedplan;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import services.applicationapi.tripplan.configurations.TripGroupProperties;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaGroupPlanNotificationPublisher implements GroupPlanNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaGroupPlanNotificationPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TripGroupProperties properties;

    public KafkaGroupPlanNotificationPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            TripGroupProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(GroupPlanSavedEvent event) {
        for (var recipientId : event.recipientIds()) {
            String userId = recipientId.toString();
            try {
                String payload = objectMapper.writeValueAsString(new GroupPlanNotificationMessage(
                        "GROUP_PLAN_SAVED", userId, event.groupId().toString(), event.planId().toString(),
                        event.groupName(), event.planTitle(), event.createdAt()));

                kafkaTemplate.send(properties.planNotificationTopic(), userId, payload)
                        .whenComplete((result, failure) -> {
                            if (failure != null) {
                                log.error("Could not publish group plan {} notification for user {}",
                                        event.planId(), recipientId, failure);
                            }
                        });
            } catch (RuntimeException exception) {
                log.error("Could not queue group plan {} notification for user {}",
                        event.planId(), recipientId, exception);
            }
        }
        log.info("Queued group plan {} notifications for {} members", event.planId(), event.recipientIds().size());
    }

    private record GroupPlanNotificationMessage(
            String type,
            String userId,
            String groupId,
            String planId,
            String groupName,
            String title,
            Instant createdAt
    ) {
    }
}
