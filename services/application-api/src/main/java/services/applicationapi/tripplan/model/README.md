# Trip-plan persistence model

`TripPlan` maps the `trip_plans` table created by Flyway V4 and extended by V11. It is an immutable saved-search preset with a generated UUID, an owning `User`, title and optional description, transport mode, optional origin airport, departure and destination city/country codes, departure interval, stay length, ticket option, separate EUR transport and accommodation budgets, and creation time. It does not contain provider offers or an accommodation check-in date.

The user relationship is lazy and non-null; deleting a user cascades to their plans at the database level. Departure datetimes use `LocalDateTime` to match the transport search requests' local time inputs. `createdAt` is an `Instant` stored as `TIMESTAMPTZ`. The database checks positive budgets, a valid departure interval, staying-night range, ticket option and transport mode values, and that an airport exists only for Plane.

V4 requires both `departure_city` and `departure_country_code` for every saved plan. V11 retains existing rows as Plane presets and preserves their budgets. The service validates both city/country pairs before inserting a row.

Flyway V12 creates `trip_plan_groups`, `trip_plan_group_members`, and `trip_plan_group_pictures`. `TripPlanGroup` stores the creator, name, optional description, and creation time. `TripPlanGroupMember` has a unique group/user pair, role, and join time; the creator is inserted as `ADMIN`. `TripPlanGroupPicture` stores normalized JPEG bytes separately to keep image data out of ordinary group reads. Invitations and chat have no tables yet.
