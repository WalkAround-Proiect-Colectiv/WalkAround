ALTER TABLE trip_group_chat_documents RENAME COLUMN object_key TO file_id;
ALTER TABLE trip_group_chat_documents RENAME COLUMN bucket_name TO file_path;
ALTER TABLE trip_group_chat_documents ALTER COLUMN file_path TYPE VARCHAR(500);
