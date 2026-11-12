-- The id a profile shows, to copy into the browser extension and an assistant's settings: a UUID made when it's
-- created, which tells nothing of how many profiles there are. The number the database counts with stays JobWarden's
-- own, and still works where it was given before. Profiles there already get one now.
ALTER TABLE users
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE users
    ADD CONSTRAINT users_public_id_key UNIQUE (public_id);
