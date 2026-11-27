package services.applicationapi.notifications.service;

import services.applicationapi.notifications.model.NotificationDraft;

/** Feature services call this only for notifications worth retaining in the inbox. */
public interface NotificationInboxWriter {

    void publish(NotificationDraft draft);
}
