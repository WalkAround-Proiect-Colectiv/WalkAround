ALTER TABLE notification_inbox
    ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX notification_inbox_recipient_page_idx;
CREATE INDEX notification_inbox_recipient_page_idx
    ON notification_inbox (recipient_user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

DROP INDEX notification_inbox_recipient_unread_idx;
CREATE INDEX notification_inbox_recipient_unread_idx
    ON notification_inbox (recipient_user_id)
    WHERE read_at IS NULL AND deleted_at IS NULL;
