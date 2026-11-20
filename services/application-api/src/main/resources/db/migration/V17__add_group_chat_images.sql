ALTER TABLE trip_group_chat_messages
    DROP CONSTRAINT trip_group_chat_messages_message_type_check;

ALTER TABLE trip_group_chat_messages
    ADD CONSTRAINT trip_group_chat_messages_message_type_check
        CHECK (message_type IN ('TEXT', 'POLL', 'IMAGE'));

ALTER TABLE trip_group_chat_messages
    DROP CONSTRAINT trip_group_chat_messages_body_check;

ALTER TABLE trip_group_chat_messages
    ADD CONSTRAINT trip_group_chat_messages_body_check
        CHECK (message_type = 'IMAGE' OR BTRIM(body) <> '');

CREATE TABLE trip_group_chat_images (
    message_id UUID PRIMARY KEY REFERENCES trip_group_chat_messages(id) ON DELETE CASCADE,
    imagekit_file_id VARCHAR(200) NOT NULL UNIQUE,
    imagekit_file_path VARCHAR(500) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0)
);
