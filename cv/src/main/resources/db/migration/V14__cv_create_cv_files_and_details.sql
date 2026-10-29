-- A profile's CV, one of each kind per profile: a file uploaded as it is, with the text read out of it so Claude can
-- match jobs against it; and the fields the CV page builds one from. Each list of the fields (experience, education,
-- languages, certifications) is a JSON array of objects, in the order the page shows them.
CREATE TABLE cv_files (
    user_id      BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    file_name    TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    content      BYTEA       NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    text         TEXT,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cv_details (
    user_id        BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name      TEXT,
    headline       TEXT,
    email          TEXT,
    phone          TEXT,
    location       TEXT,
    website        TEXT,
    linkedin       TEXT,
    github         TEXT,
    summary        TEXT,
    skills         TEXT[]      NOT NULL DEFAULT '{}',
    experience     JSONB       NOT NULL DEFAULT '[]',
    education      JSONB       NOT NULL DEFAULT '[]',
    languages      JSONB       NOT NULL DEFAULT '[]',
    certifications JSONB       NOT NULL DEFAULT '[]',
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
