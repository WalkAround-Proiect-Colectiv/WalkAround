-- Cached SerpApi results, one row per locality (no per-event rows).
CREATE TABLE event_locality_snapshots (
    locality_key VARCHAR(200) PRIMARY KEY,
    locality_name VARCHAR(200) NOT NULL,
    county VARCHAR(200),
    country_code VARCHAR(2),
    fetched_at TIMESTAMPTZ NOT NULL,
    events_json TEXT NOT NULL
);

-- Events a user marked as interesting, with a snapshot of the event and reminder state.
CREATE TABLE event_interests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_key VARCHAR(64) NOT NULL,
    locality_key VARCHAR(200) NOT NULL,
    title VARCHAR(300) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    all_day BOOLEAN NOT NULL,
    when_text VARCHAR(200),
    venue_name VARCHAR(300),
    address VARCHAR(500),
    description TEXT,
    thumbnail_url TEXT,
    ticket_source VARCHAR(100),
    ticket_url TEXT,
    remind_before_minutes INTEGER CHECK (remind_before_minutes BETWEEN 1 AND 43200),
    remind_at TIMESTAMPTZ,
    reminded_at TIMESTAMPTZ,
    reminder_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT event_interests_user_event_unique UNIQUE (user_id, event_key),
    CONSTRAINT event_interests_reminder_consistency CHECK ((remind_before_minutes IS NULL) = (remind_at IS NULL))
);

CREATE INDEX event_interests_user_id_idx ON event_interests(user_id);
CREATE INDEX event_interests_due_reminders_idx ON event_interests(remind_at) WHERE reminded_at IS NULL;
