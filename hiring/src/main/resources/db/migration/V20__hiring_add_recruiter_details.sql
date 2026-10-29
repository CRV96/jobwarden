-- Where each recruiter came from: a post's author, found by Claude, a contact saved on a job, or added by hand; the
-- company and the country they recruit for; why Claude suggests them; and whether the profile removed them, which
-- keeps them from coming back.
ALTER TABLE recruiters
    ADD COLUMN company TEXT,
    ADD COLUMN country VARCHAR(2),
    ADD COLUMN note    TEXT,
    ADD COLUMN origin  VARCHAR(20) NOT NULL DEFAULT 'POST' CHECK (origin IN ('POST', 'FOUND', 'CONTACT', 'HAND')),
    ADD COLUMN hidden  BOOLEAN     NOT NULL DEFAULT FALSE;
