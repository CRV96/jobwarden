-- The CV's personal projects, a JSON array like its experience; and the profile's GitHub account: its user name, a
-- token (encrypted) when it gave one for private repositories, and the repositories it chose for Claude to write the
-- projects from, as owner/name.
ALTER TABLE cv_details ADD COLUMN projects JSONB NOT NULL DEFAULT '[]';

CREATE TABLE github_accounts (
    user_id      BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    username     TEXT        NOT NULL,
    token        TEXT,
    repositories TEXT[]      NOT NULL DEFAULT '{}',
    connected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
