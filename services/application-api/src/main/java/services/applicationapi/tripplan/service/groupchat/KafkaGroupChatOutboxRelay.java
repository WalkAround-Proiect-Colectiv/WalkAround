package services.applicationapi.tripplan.service.groupchat;

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
import services.applicationapi.tripplan.configurations.GroupChatProperties;
import services.applicationapi.tripplan.model.GroupChatMessage;
import services.applicationapi.tripplan.repository.GroupChatRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class KafkaGroupChatOutboxRelay implements GroupChatOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaGroupChatOutboxRelay.class);

    private final GroupChatRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final GroupChatProperties properties;

    public KafkaGroupChatOutboxRelay(GroupChatRepository outbox, KafkaTemplate<String, String> kafka,
                                    ObjectMapper mapper, GroupChatProperties properties) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${app.group-chat.outbox-poll-ms}")
    public void publishPending() {
        for (GroupChatRepository.PendingMessage pending : outbox.lockPending(properties.outboxBatchSize())) {
            GroupChatMessage message = pending.message();
            try {
                String payload = mapper.writeValueAsString(new ChatEvent(
                        "GROUP_CHAT_MESSAGE", message.id(), message.conversationId(), message.sequence(),
                        message.senderId(), message.senderName(), message.body(), message.createdAt()));
                kafka.send(properties.topic(), message.conversationId().toString(), payload)
                        .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
                outbox.markPublished(message.id());
                log.info("Published chat message {} in conversation {} at sequence {}",
                        message.id(), message.conversationId(), message.sequence());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                postpone(pending, exception);
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                postpone(pending, exception);
            }
        }
    }

    private void postpone(GroupChatRepository.PendingMessage pending, Exception exception) {
        long seconds = Math.min(properties.retryMaxDelay().toSeconds(),
                1L << Math.min(pending.attempts(), 30));
        outbox.defer(pending.message().id(), Instant.now().plusSeconds(Math.max(1, seconds)));
        log.warn("Could not publish chat message {} (attempt {}): {}",
                pending.message().id(), pending.attempts() + 1, exception.getClass().getSimpleName());
    }

    private record ChatEvent(
            String type,
            UUID messageId,
            UUID conversationId,
            long sequence,
            UUID senderId,
            String senderName,
            String body,
            Instant createdAt
    ) {
    }
}
