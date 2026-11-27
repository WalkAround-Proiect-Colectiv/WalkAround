package services.applicationapi.notifications.model;

import java.time.Instant;
import java.util.UUID;

public record InboxNotification(
        UUID id,
        String category,
        String title,
        String body,
        NotificationTarget target,
        Instant createdAt,
        Instant readAt
) {
}
