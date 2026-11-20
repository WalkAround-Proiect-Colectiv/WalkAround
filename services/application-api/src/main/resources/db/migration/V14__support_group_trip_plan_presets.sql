ALTER TABLE trip_plans
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN group_id UUID REFERENCES trip_plan_groups(id) ON DELETE CASCADE,
    ADD COLUMN created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN adults INTEGER NOT NULL DEFAULT 1 CHECK (adults BETWEEN 1 AND 9),
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT trip_plans_owner_check
        CHECK ((user_id IS NOT NULL) <> (group_id IS NOT NULL)),
    ADD CONSTRAINT trip_plans_current_check
        CHECK (NOT is_current OR group_id IS NOT NULL);

CREATE INDEX trip_plans_group_created_idx ON trip_plans(group_id, created_at DESC)
    WHERE group_id IS NOT NULL;

CREATE UNIQUE INDEX trip_plans_group_current_idx ON trip_plans(group_id)
    WHERE is_current;
