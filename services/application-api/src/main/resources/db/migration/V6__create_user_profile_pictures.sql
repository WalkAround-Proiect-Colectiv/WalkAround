CREATE TABLE user_profile_pictures (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    image_data BYTEA NOT NULL
);
