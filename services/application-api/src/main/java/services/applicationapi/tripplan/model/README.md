# Trip-plan persistence model

`TripPlan` maps the `trip_plans` table created by Flyway V4 and extended by V11 and V14. It stores a saved-search preset with a generated UUID, exactly one owner (a user or a group), title and optional description, transport mode, optional origin airport, departure and destination city/country codes, departure interval, stay length, adult count, ticket option, separate EUR transport and accommodation budgets, and creation time. It does not contain provider offers or an accommodation check-in date.

Personal plans have a non-null user owner, so deleting that user cascades to those plans. Group plans have a non-null group owner and an optional creator reference; deleting the creator leaves the group plan, while deleting the group cascades to its plans. Departure datetimes use `LocalDateTime` to match the transport search requests' local time inputs. `createdAt` is an `Instant` stored as `TIMESTAMPTZ`. The database checks positive budgets, a valid departure interval, staying-night and adult-count ranges, ticket option and transport mode values, and that an airport exists only for Plane.

V4 requires both `departure_city` and `departure_country_code` for every saved plan. V11 retains existing rows as Plane presets and preserves their budgets. The service validates both city/country pairs before inserting a row.

V14 adds `group_id`, optional `created_by_user_id`, `adults` defaulting to 1, and `is_current`. It requires exactly one personal or group owner and at most one current plan per group through a partial unique index. Existing rows remain personal one-adult plans. Group members can save alternatives; admins choose the current shared plan.

Flyway V12 creates `trip_plan_groups`, `trip_plan_group_members`, and `trip_plan_group_pictures`. `TripPlanGroup` stores the creator, name, optional description, and creation time. `TripPlanGroupMember` has a unique group/user pair, role, and join time; the creator is inserted as `ADMIN`. `TripPlanGroupPicture` stores normalized JPEG bytes separately to keep image data out of ordinary group reads.

Flyway V13 creates `trip_plan_group_invites`. `TripPlanGroupInvite` stores a unique SHA-256 token hash, creator, expiry, optional use and revocation times, and the accepting user. The raw token is never stored. Group deletion cascades to its invites; the service makes invite use and membership insertion one transaction. Chat has no table yet.

Group editing mutates the existing `TripPlanGroup` name and description and can replace or delete its separate `TripPlanGroupPicture` row. The V12 tables already support these changes, so editing needs no migration.
