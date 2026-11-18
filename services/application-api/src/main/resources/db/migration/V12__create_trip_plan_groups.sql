CREATE TABLE trip_plan_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (BTRIM(name) <> ''),
    description VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE trip_plan_group_members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES trip_plan_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trip_plan_group_members_unique UNIQUE (group_id, user_id)
);

CREATE INDEX trip_plan_group_members_user_id_idx ON trip_plan_group_members(user_id);

CREATE TABLE trip_plan_group_pictures (
    group_id UUID PRIMARY KEY REFERENCES trip_plan_groups(id) ON DELETE CASCADE,
    image_data BYTEA NOT NULL
);
