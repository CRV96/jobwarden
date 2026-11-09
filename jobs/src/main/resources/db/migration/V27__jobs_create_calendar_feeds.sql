-- The secret address calendar apps subscribe to a profile's calendar at: whoever has it reads the calendar, so it's
-- long and random, and the calendar page makes a new one when asked, which stops the old one.
CREATE TABLE calendar_feeds (
    user_id    BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
