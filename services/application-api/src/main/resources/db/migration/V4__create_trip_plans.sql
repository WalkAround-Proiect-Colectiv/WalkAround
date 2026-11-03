CREATE TABLE trip_plans (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL CHECK (BTRIM(title) <> ''),
    description VARCHAR(2000),
    origin_airport VARCHAR(3) NOT NULL,
    departure_city VARCHAR(100) NOT NULL,
    departure_country_code VARCHAR(2) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    departure_from TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    departure_until TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    staying_nights INTEGER NOT NULL CHECK (staying_nights BETWEEN 1 AND 365),
    ticket_option VARCHAR(16) NOT NULL CHECK (ticket_option IN ('ROUND_TRIP', 'TWO_ONE_WAY')),
    flight_budget NUMERIC(11, 2) NOT NULL CHECK (flight_budget > 0),
    accommodation_budget NUMERIC(11, 2) NOT NULL CHECK (accommodation_budget > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trip_plans_departure_interval CHECK (departure_until >= departure_from)
);

CREATE INDEX trip_plans_user_id_idx ON trip_plans(user_id);
