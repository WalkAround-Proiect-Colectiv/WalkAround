package services.applicationapi.notifications.configurations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.notifications.inbox")
public record NotificationInboxProperties(
        @DefaultValue("notification-inbox-topic") String topic,
        @DefaultValue("3") int partitions,
        @DefaultValue("1000") long outboxPollMs,
        @DefaultValue("50") int outboxBatchSize,
        @DefaultValue("PT5S") Duration publishTimeout,
        @DefaultValue("PT1M") Duration retryMaxDelay
) {
    public NotificationInboxProperties {
        if (topic == null || topic.isBlank() || partitions < 1 || partitions > 64
                || outboxPollMs < 100 || outboxBatchSize < 1 || outboxBatchSize > 200
                || publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()
                || retryMaxDelay == null || retryMaxDelay.isZero() || retryMaxDelay.isNegative()) {
            throw new IllegalArgumentException("Inbox notification settings are outside their allowed ranges");
        }
    }
}
