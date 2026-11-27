CREATE TABLE journal_trips (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title varchar(120) NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    description varchar(2000) NOT NULL CHECK (char_length(btrim(description)) BETWEEN 1 AND 2000),
    destination_city varchar(100) NOT NULL CHECK (btrim(destination_city) <> ''),
    country_code varchar(2) NOT NULL REFERENCES countries(code),
    country_name text NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL CHECK (end_date >= start_date),
    source_plan_id uuid REFERENCES trip_plans(id) ON DELETE SET NULL,
    request_fingerprint varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX journal_trips_owner_page_idx ON journal_trips(owner_id, created_at DESC, id DESC);
CREATE INDEX journal_trips_source_idx ON journal_trips(source_plan_id) WHERE source_plan_id IS NOT NULL;
CREATE INDEX trip_plans_personal_page_idx ON trip_plans(user_id, created_at DESC, id DESC) WHERE group_id IS NULL;