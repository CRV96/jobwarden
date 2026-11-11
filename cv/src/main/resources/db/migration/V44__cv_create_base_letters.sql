-- The profile's base letter: a cover letter of its own, written once, that each job's letter starts from, by hand or by
-- Claude, who keeps its voice and the paragraphs it always uses. {company} and {role} in it stand for the job's company
-- and title. One per profile; deleting the profile deletes it.
CREATE TABLE base_letters (
    user_id    BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
