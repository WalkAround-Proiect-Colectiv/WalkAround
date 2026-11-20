CREATE TABLE trip_group_chat_conversations (
    group_id UUID PRIMARY KEY REFERENCES trip_plan_groups(id) ON DELETE CASCADE,
    next_sequence BIGINT NOT NULL DEFAULT 1 CHECK (next_sequence > 0)
);

CREATE TABLE trip_group_chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES trip_group_chat_conversations(group_id) ON DELETE CASCADE,
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    sender_id UUID REFERENCES users(id) ON DELETE SET NULL,
    body VARCHAR(2000) NOT NULL CHECK (BTRIM(body) <> ''),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trip_group_chat_message_sequence_unique UNIQUE (conversation_id, sequence)
);

CREATE INDEX trip_group_chat_messages_conversation_sequence_idx
    ON trip_group_chat_messages(conversation_id, sequence DESC);

CREATE TABLE trip_group_chat_outbox (
    message_id UUID PRIMARY KEY REFERENCES trip_group_chat_messages(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_group_chat_outbox_message_sequence_fk
        FOREIGN KEY (conversation_id, sequence)
        REFERENCES trip_group_chat_messages(conversation_id, sequence)
        ON DELETE CASCADE
);

CREATE INDEX trip_group_chat_outbox_pending_idx
    ON trip_group_chat_outbox(next_attempt_at, created_at)
    WHERE published_at IS NULL;
