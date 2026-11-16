-- A profile's photo: a small square PNG, apart from users so that listing the profiles never reads the images.
-- users.image_changed_at says a profile has one, and when it last changed, which the pages put in its address so the
-- browser keeps it until it changes.
ALTER TABLE users ADD COLUMN image_changed_at TIMESTAMPTZ;

CREATE TABLE profile_images (
    user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    image   BYTEA  NOT NULL
);
