package services.applicationapi.notifications.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.notifications.model.NotificationDraft;
import services.applicationapi.notifications.model.NotificationPage;
import services.applicationapi.notifications.model.NotificationTarget;
import services.applicationapi.notifications.repository.NotificationInboxRepository;

@Service
public class NotificationInboxService implements NotificationInboxWriter, NotificationInboxReader {

    private static final Logger log = LoggerFactory.getLogger(NotificationInboxService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final String CODE_PATTERN = "[A-Za-z][A-Za-z0-9_.-]*";
    private static final String PARAM_PATTERN = "[A-Za-z][A-Za-z0-9_]*";

    private final NotificationInboxRepository repository;

    public NotificationInboxService(NotificationInboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void publish(NotificationDraft draft) {
        validate(draft);
        if (repository.insert(draft)) {
            log.debug("Stored inbox event {} from {} for user {}",
                    draft.sourceEventId(), draft.source(), draft.recipientUserId());
        } else {
            log.debug("Ignored duplicate inbox event {} from {} for user {}",
                    draft.sourceEventId(), draft.source(), draft.recipientUserId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage list(UUID userId, UUID before, int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new InvalidNotificationException("Limit must be between 1 and 50");
        }
        var rows = repository.findPage(userId, before, limit + 1);
        boolean hasMore = rows.size() > limit;
        var items = hasMore ? rows.subList(0, limit) : rows;
        return new NotificationPage(items, hasMore ? items.getLast().id() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countUnread(userId);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        if (!repository.markRead(userId, notificationId)) {
            throw new NotificationNotFoundException();
        }
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        if (repository.delete(userId, notificationId)) {
            log.debug("Deleted inbox notification {} for user {}", notificationId, userId);
        }
    }

    private void validate(NotificationDraft draft) {
        if (draft == null || draft.recipientUserId() == null
                || !code(draft.source(), 64) || !text(draft.sourceEventId(), 160)
                || !code(draft.category(), 64) || !text(draft.title(), 160)
                || !text(draft.body(), 500)) {
            throw new InvalidNotificationException("Invalid inbox notification");
        }
        NotificationTarget target = draft.target();
        if (target == null) return;
        if (!code(target.key(), 64) || target.params() == null || target.params().size() > 10) {
            throw new InvalidNotificationException("Invalid notification target");
        }
        for (Map.Entry<String, String> param : target.params().entrySet()) {
            if (param.getKey() == null || !param.getKey().matches(PARAM_PATTERN)
                    || param.getKey().length() > 40 || param.getValue() == null
                    || param.getValue().length() > 200 || param.getValue().chars().anyMatch(c -> c < 32)) {
                throw new InvalidNotificationException("Invalid notification target parameter");
            }
        }
    }

    private boolean code(String value, int max) {
        return value != null && value.length() <= max && value.matches(CODE_PATTERN);
    }

    private boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max
                && value.chars().noneMatch(c -> c < 32);
    }
}
