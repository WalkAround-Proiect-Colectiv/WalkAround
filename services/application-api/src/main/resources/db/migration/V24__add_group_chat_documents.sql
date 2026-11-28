ALTER TABLE trip_group_chat_messages DROP CONSTRAINT trip_group_chat_messages_message_type_check;
ALTER TABLE trip_group_chat_messages ADD CONSTRAINT trip_group_chat_messages_message_type_check
    CHECK (message_type IN ('TEXT', 'POLL', 'IMAGE', 'DOCUMENT'));
ALTER TABLE trip_group_chat_messages DROP CONSTRAINT trip_group_chat_messages_body_check;
ALTER TABLE trip_group_chat_messages ADD CONSTRAINT trip_group_chat_messages_body_check
    CHECK (message_type IN ('IMAGE', 'DOCUMENT') OR BTRIM(body) <> '');

CREATE TABLE trip_group_chat_documents (
    message_id UUID PRIMARY KEY REFERENCES trip_group_chat_messages(id) ON DELETE CASCADE,
    object_key VARCHAR(200) NOT NULL UNIQUE,
    bucket_name VARCHAR(63) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL CHECK (content_type IN ('application/pdf', 'text/plain')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);
