package services.applicationapi.notifications.model;

import java.util.List;
import java.util.UUID;

public record NotificationPage(List<InboxNotification> items, UUID nextCursor) {
}
