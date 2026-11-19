package services.applicationapi.tripplan.configurations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.group-chat")
public record GroupChatProperties(
        @DefaultValue("trip-group-chat-topic") String topic,
        @DefaultValue("6") int partitions,
        @DefaultValue("100") int pageSize,
        @DefaultValue("2000") int maxMessageLength,
        @DefaultValue("10") int maxPollOptions,
        @DefaultValue("100") int maxPollOptionLength,
        @DefaultValue("PT5M") Duration socketTokenTtl,
        @DefaultValue("1000") long outboxPollMs,
        @DefaultValue("50") int outboxBatchSize,
        @DefaultValue("PT5S") Duration publishTimeout,
        @DefaultValue("PT1M") Duration retryMaxDelay
) {
    public GroupChatProperties {
        if (topic == null || topic.isBlank() || partitions < 2 || partitions > 64
                || pageSize < 1 || pageSize > 200 || maxMessageLength < 1 || maxMessageLength > 2000
                || maxPollOptions < 2 || maxPollOptions > 20
                || maxPollOptionLength < 1 || maxPollOptionLength > 100
                || socketTokenTtl.isNegative() || socketTokenTtl.isZero()
                || socketTokenTtl.compareTo(Duration.ofMinutes(15)) > 0
                || outboxPollMs < 100 || outboxBatchSize < 1 || outboxBatchSize > 200
                || publishTimeout.isNegative() || publishTimeout.isZero()
                || retryMaxDelay.isNegative() || retryMaxDelay.isZero()) {
            throw new IllegalArgumentException("Group chat settings are outside their allowed ranges");
        }
    }
}
