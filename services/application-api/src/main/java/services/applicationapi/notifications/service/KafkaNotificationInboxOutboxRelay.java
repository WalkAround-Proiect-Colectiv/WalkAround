package services.applicationapi.notifications.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.notifications.configurations.NotificationInboxProperties;
import services.applicationapi.notifications.model.PendingInboxSignal;
import services.applicationapi.notifications.repository.NotificationInboxRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class KafkaNotificationInboxOutboxRelay implements NotificationInboxOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationInboxOutboxRelay.class);

    private final NotificationInboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final NotificationInboxProperties properties;

    public KafkaNotificationInboxOutboxRelay(
            NotificationInboxRepository repository,
            KafkaTemplate<String, String> kafka,
            ObjectMapper mapper,
            NotificationInboxProperties properties
    ) {
        this.repository = repository;
        this.kafka = kafka;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${app.notifications.inbox.outbox-poll-ms}")
    public void publishPending() {
        for (int processed = 0; processed < properties.outboxBatchSize(); processed++) {
            var pending = repository.lockPendingSignals(1);
            if (pending.isEmpty()) return;
            PendingInboxSignal signal = pending.getFirst();

            try {
                String payload = mapper.writeValueAsString(new InboxChangedMessage(
                        "INBOX_CHANGED", signal.recipientUserId(), signal.notificationId()));
                kafka.send(properties.topic(), signal.recipientUserId().toString(), payload)
                        .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
                repository.markSignalPublished(signal.notificationId());
                log.debug("Published inbox notification signal {}", signal.notificationId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                defer(signal);
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                log.warn("Could not publish inbox signal {} (attempt {}): {}",
                        signal.notificationId(), signal.attempts() + 1, exception.getClass().getSimpleName());
                defer(signal);
            }
        }
    }

    private void defer(PendingInboxSignal signal) {
        long seconds = Math.min(properties.retryMaxDelay().toSeconds(),
                1L << Math.min(signal.attempts(), 30));
        repository.deferSignal(signal.notificationId(), Instant.now().plusSeconds(Math.max(1, seconds)));
    }

    private record InboxChangedMessage(String type, UUID userId, UUID notificationId) {
    }
}
