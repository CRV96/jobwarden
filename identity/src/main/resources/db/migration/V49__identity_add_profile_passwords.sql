-- A profile's own password, if it has one: asked whenever the profile is chosen in a browser. Only its hash is kept
-- (PBKDF2, with the algorithm's id in front, so a stronger one can follow); no password, no hash.
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255);
