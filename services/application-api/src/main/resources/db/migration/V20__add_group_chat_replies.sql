ALTER TABLE trip_group_chat_messages
    ADD COLUMN reply_to_message_id uuid,
    ADD CONSTRAINT trip_group_chat_message_conversation_id_unique UNIQUE (conversation_id, id),
    ADD CONSTRAINT trip_group_chat_reply_same_conversation_fk
        FOREIGN KEY (conversation_id, reply_to_message_id)
        REFERENCES trip_group_chat_messages(conversation_id, id),
    ADD CONSTRAINT trip_group_chat_reply_not_self CHECK (reply_to_message_id IS NULL OR reply_to_message_id <> id);

CREATE INDEX trip_group_chat_reply_parent_idx
    ON trip_group_chat_messages(conversation_id, reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;