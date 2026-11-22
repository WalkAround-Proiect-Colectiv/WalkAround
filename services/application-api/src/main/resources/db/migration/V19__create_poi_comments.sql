CREATE TABLE poi_comment_subjects (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE poi_comment_aliases (
    reference varchar(160) PRIMARY KEY,
    subject_id uuid NOT NULL REFERENCES poi_comment_subjects(id) ON DELETE CASCADE
);
CREATE INDEX poi_comment_aliases_subject_idx ON poi_comment_aliases(subject_id);

CREATE TABLE poi_comments (
    id uuid PRIMARY KEY,
    subject_id uuid NOT NULL REFERENCES poi_comment_subjects(id),
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body varchar(2000) NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX poi_comments_page_idx ON poi_comments(subject_id, created_at DESC, id DESC);

CREATE TABLE poi_comment_reports (
    id uuid PRIMARY KEY,
    comment_id uuid NOT NULL REFERENCES poi_comments(id) ON DELETE CASCADE,
    reporter_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason varchar(500) NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 1 AND 500),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (comment_id, reporter_id)
);
CREATE INDEX poi_comment_reports_page_idx ON poi_comment_reports(created_at DESC, id DESC);

CREATE TABLE poi_comment_rate_limits (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    window_start timestamptz NOT NULL,
    action_count integer NOT NULL CHECK (action_count BETWEEN 1 AND 10)
);