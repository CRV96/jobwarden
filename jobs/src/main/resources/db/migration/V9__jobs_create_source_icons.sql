-- The icon of each site a profile saved postings from (the source of its applications), as the browser showed it on
-- the posting's tab: a small PNG, which the lists show next to the site. One per profile and site; saving another
-- posting from the site replaces it.
CREATE TABLE source_icons (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source     TEXT        NOT NULL,
    image      BYTEA       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source)
);
