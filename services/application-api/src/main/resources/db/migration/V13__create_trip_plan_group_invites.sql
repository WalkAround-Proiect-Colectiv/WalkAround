CREATE TABLE trip_plan_group_invites (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES trip_plan_groups(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (LENGTH(token_hash) = 64),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    used_by_user_id UUID REFERENCES users(id),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT trip_plan_group_invites_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT trip_plan_group_invites_use_check
        CHECK ((used_at IS NULL) = (used_by_user_id IS NULL))
);

CREATE INDEX trip_plan_group_invites_group_created_idx
    ON trip_plan_group_invites(group_id, created_at DESC);
