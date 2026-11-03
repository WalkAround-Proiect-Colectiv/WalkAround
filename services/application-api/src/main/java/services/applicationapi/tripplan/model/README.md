# Trip-plan persistence model

`TripPlan` maps the `trip_plans` table created by Flyway migration `V4__create_trip_plans.sql`. It is an immutable saved-search preset with a generated UUID, an owning `User`, title and optional description, origin airport, departure city/country code, destination city/country code, flight search inputs, separate EUR flight and accommodation budgets, and creation time. It does not contain provider offers or an accommodation check-in date.

The user relationship is lazy and non-null; deleting a user cascades to their plans at the database level. Departure datetimes use `LocalDateTime` because the flight search request interprets them as local times at the origin airport. `createdAt` is an `Instant` stored as `TIMESTAMPTZ`. The database checks positive budgets, a valid departure interval, staying-night range, and the two supported ticket option values.

V4 requires both `departure_city` and `departure_country_code` for every saved plan. The service validates them before inserting a row.
