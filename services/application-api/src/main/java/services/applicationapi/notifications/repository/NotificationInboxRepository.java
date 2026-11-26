package services.applicationapi.notifications.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import services.applicationapi.notifications.model.InboxNotification;
import services.applicationapi.notifications.model.NotificationDraft;
import services.applicationapi.notifications.model.PendingInboxSignal;

public interface NotificationInboxRepository {

    boolean insert(NotificationDraft draft);

    List<InboxNotification> findPage(UUID userId, UUID before, int limit);

    long countUnread(UUID userId);

    boolean markRead(UUID userId, UUID notificationId);

    List<PendingInboxSignal> lockPendingSignals(int limit);

    void markSignalPublished(UUID notificationId);

    void deferSignal(UUID notificationId, Instant nextAttemptAt);
}
