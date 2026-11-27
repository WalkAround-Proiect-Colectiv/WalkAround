CREATE TABLE notification_inbox_outbox (
    notification_id UUID PRIMARY KEY REFERENCES notification_inbox(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    published_at TIMESTAMPTZ
);

CREATE INDEX notification_inbox_outbox_pending_idx
    ON notification_inbox_outbox (next_attempt_at, created_at, notification_id)
    WHERE published_at IS NULL;
