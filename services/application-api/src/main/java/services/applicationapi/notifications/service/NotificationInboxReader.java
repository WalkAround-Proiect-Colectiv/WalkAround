package services.applicationapi.notifications.service;

import java.util.UUID;
import services.applicationapi.notifications.model.NotificationPage;

public interface NotificationInboxReader {

    NotificationPage list(UUID userId, UUID before, int limit);

    long unreadCount(UUID userId);

    void markRead(UUID userId, UUID notificationId);
}
