-- How often each mailbox is read, in minutes (0: only when the profile asks, with Check now), and what becomes of the
-- updates found: ASK leaves them all to the profile, MATCHED applies those matched to a job, CLAUDE also has Claude
-- sort the others. An update keeps the start of its email, for Claude to tell which job it's about, and says who
-- applied or dismissed it.
ALTER TABLE mail_accounts
    ADD COLUMN read_every_minutes INT         NOT NULL DEFAULT 10 CHECK (read_every_minutes BETWEEN 0 AND 10080),
    ADD COLUMN automation         VARCHAR(10) NOT NULL DEFAULT 'ASK' CHECK (automation IN ('ASK', 'MATCHED', 'CLAUDE'));

ALTER TABLE email_updates
    ADD COLUMN text       TEXT,
    ADD COLUMN decided_by VARCHAR(10) CHECK (decided_by IN ('YOU', 'JOBWARDEN', 'CLAUDE'));

CREATE INDEX email_updates_user_id_decided_at_idx ON email_updates (user_id, decided_at);
