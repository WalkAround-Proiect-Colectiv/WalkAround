package services.applicationapi.notifications.model;

import java.util.UUID;

public record PendingInboxSignal(UUID notificationId, UUID recipientUserId, int attempts) {
}
