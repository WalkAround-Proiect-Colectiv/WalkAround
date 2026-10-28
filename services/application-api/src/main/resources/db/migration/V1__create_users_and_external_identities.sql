CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    profile_picture_url TEXT,
    description VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX users_username_unique ON users (LOWER(username))
    WHERE username IS NOT NULL;

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_identities_provider_subject_unique UNIQUE (provider, provider_subject),
    CONSTRAINT user_identities_user_provider_unique UNIQUE (user_id, provider)
);
