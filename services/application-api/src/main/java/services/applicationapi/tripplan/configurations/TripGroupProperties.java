package services.applicationapi.tripplan.configurations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-groups")
public record TripGroupProperties(
        @DefaultValue("2097152") long pictureMaxBytes,
        @DefaultValue("12000000") long pictureMaxPixels,
        @DefaultValue("512") int pictureOutputSize,
        @DefaultValue("P7D") Duration inviteTtl,
        @DefaultValue("10") int maxActiveInvites,
        @DefaultValue("trip-group-plan-notifications-topic") String planNotificationTopic
) {

    public TripGroupProperties {
        if (pictureMaxBytes < 1 || pictureMaxPixels < 1 || pictureOutputSize < 1
                || inviteTtl.isZero() || inviteTtl.isNegative()
                || inviteTtl.compareTo(Duration.ofDays(30)) > 0
                || maxActiveInvites < 1 || maxActiveInvites > 100
                || planNotificationTopic == null || planNotificationTopic.isBlank()) {
            throw new IllegalArgumentException("Trip group limits are outside their allowed ranges");
        }
    }
}
