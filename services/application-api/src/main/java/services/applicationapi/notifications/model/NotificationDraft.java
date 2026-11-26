package services.applicationapi.notifications.model;

import java.util.UUID;

/** One durable, user-relevant notification. The source event ID is stable across retries. */
public record NotificationDraft(
        UUID recipientUserId,
        String source,
        String sourceEventId,
        String category,
        String title,
        String body,
        NotificationTarget target
) {
}
