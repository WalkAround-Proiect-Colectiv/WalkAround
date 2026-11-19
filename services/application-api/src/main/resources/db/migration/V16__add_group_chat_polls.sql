ALTER TABLE trip_group_chat_messages
    ADD COLUMN message_type VARCHAR(10) NOT NULL DEFAULT 'TEXT'
        CHECK (message_type IN ('TEXT', 'POLL'));

CREATE TABLE trip_group_chat_polls (
    message_id UUID PRIMARY KEY REFERENCES trip_group_chat_messages(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    closed_at TIMESTAMPTZ
);

CREATE TABLE trip_group_chat_poll_options (
    id UUID PRIMARY KEY,
    poll_message_id UUID NOT NULL REFERENCES trip_group_chat_polls(message_id) ON DELETE CASCADE,
    label VARCHAR(100) NOT NULL CHECK (BTRIM(label) <> ''),
    position SMALLINT NOT NULL CHECK (position >= 0),
    CONSTRAINT trip_group_chat_poll_option_position_unique UNIQUE (poll_message_id, position),
    CONSTRAINT trip_group_chat_poll_option_pair_unique UNIQUE (poll_message_id, id)
);

CREATE TABLE trip_group_chat_poll_votes (
    poll_message_id UUID NOT NULL REFERENCES trip_group_chat_polls(message_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    option_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (poll_message_id, user_id),
    CONSTRAINT trip_group_chat_poll_vote_option_fk
        FOREIGN KEY (poll_message_id, option_id)
        REFERENCES trip_group_chat_poll_options(poll_message_id, id)
        ON DELETE CASCADE
);

CREATE INDEX trip_group_chat_poll_votes_option_idx
    ON trip_group_chat_poll_votes(poll_message_id, option_id);

ALTER TABLE trip_group_chat_conversations
    ADD COLUMN next_event_sequence BIGINT NOT NULL DEFAULT 1 CHECK (next_event_sequence > 0);

UPDATE trip_group_chat_conversations
SET next_event_sequence = next_sequence;

ALTER TABLE trip_group_chat_outbox ADD COLUMN event_id UUID;
UPDATE trip_group_chat_outbox SET event_id = message_id;
ALTER TABLE trip_group_chat_outbox ALTER COLUMN event_id SET NOT NULL;
ALTER TABLE trip_group_chat_outbox DROP CONSTRAINT trip_group_chat_outbox_pkey;
ALTER TABLE trip_group_chat_outbox ADD PRIMARY KEY (event_id);

ALTER TABLE trip_group_chat_outbox ADD COLUMN event_sequence BIGINT;
UPDATE trip_group_chat_outbox SET event_sequence = sequence;
ALTER TABLE trip_group_chat_outbox ALTER COLUMN event_sequence SET NOT NULL;
ALTER TABLE trip_group_chat_outbox
    ADD CONSTRAINT trip_group_chat_outbox_event_sequence_unique
        UNIQUE (conversation_id, event_sequence);

ALTER TABLE trip_group_chat_outbox ADD COLUMN payload TEXT;

CREATE INDEX trip_group_chat_outbox_conversation_event_idx
    ON trip_group_chat_outbox(conversation_id, event_sequence);
