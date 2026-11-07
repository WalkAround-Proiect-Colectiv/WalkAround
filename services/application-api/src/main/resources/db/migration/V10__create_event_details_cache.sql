-- Per-event cache for enriched event details fetched from the provider.
CREATE TABLE event_details_cache (
    event_key     VARCHAR(64)  PRIMARY KEY,
    locality_key  VARCHAR(200) NOT NULL,
    status        VARCHAR(16)  NOT NULL CHECK (status IN ('OK', 'FAILED', 'EMPTY')),
    details_json  TEXT,
    fetched_at    TIMESTAMPTZ  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX event_details_cache_expires_idx ON event_details_cache(expires_at);
