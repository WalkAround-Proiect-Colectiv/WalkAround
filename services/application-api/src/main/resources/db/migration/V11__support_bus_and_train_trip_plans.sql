ALTER TABLE trip_plans
    ADD COLUMN transport_mode VARCHAR(5) NOT NULL DEFAULT 'PLANE';

ALTER TABLE trip_plans
    RENAME COLUMN flight_budget TO transport_budget;

ALTER TABLE trip_plans
    ALTER COLUMN origin_airport DROP NOT NULL;

ALTER TABLE trip_plans
    ADD CONSTRAINT trip_plans_transport_mode_check
        CHECK (transport_mode IN ('PLANE', 'BUS', 'TRAIN')),
    ADD CONSTRAINT trip_plans_origin_airport_check
        CHECK ((transport_mode = 'PLANE' AND origin_airport IS NOT NULL)
            OR (transport_mode IN ('BUS', 'TRAIN') AND origin_airport IS NULL));
