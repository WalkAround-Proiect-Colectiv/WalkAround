CREATE TABLE notification_inbox (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(64) NOT NULL,
    source_event_id VARCHAR(160) NOT NULL,
    category VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    target_key VARCHAR(64),
    target_params JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT notification_inbox_source_event_unique
        UNIQUE (recipient_user_id, source, source_event_id),
    CONSTRAINT notification_inbox_target_check
        CHECK (jsonb_typeof(target_params) = 'object'
            AND ((target_key IS NULL AND target_params = '{}'::jsonb) OR target_key IS NOT NULL))
);

CREATE INDEX notification_inbox_recipient_page_idx
    ON notification_inbox (recipient_user_id, created_at DESC, id DESC);

CREATE INDEX notification_inbox_recipient_unread_idx
    ON notification_inbox (recipient_user_id) WHERE read_at IS NULL;
