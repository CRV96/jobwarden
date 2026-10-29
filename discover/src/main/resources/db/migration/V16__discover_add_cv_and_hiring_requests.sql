-- Two more things the profile can ask Claude: tailor the CV to one job, and find recruiters' hiring posts. A request
-- about one job names it; deleting the job deletes its requests.
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_kind_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_kind_check
    CHECK (kind IN ('FIND_JOBS_FOR_CV', 'FIND_JOBS', 'RUN_SAVED_SEARCHES', 'CHECK_POSTINGS', 'FOLLOW_UP', 'TAILOR_CV',
                    'FIND_HIRING_POSTS'));

ALTER TABLE ai_requests ADD COLUMN application_id BIGINT REFERENCES applications (id) ON DELETE CASCADE;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_application_id_check
    CHECK ((kind = 'TAILOR_CV') = (application_id IS NOT NULL));
CREATE INDEX ai_requests_application_id_idx ON ai_requests (application_id) WHERE application_id IS NOT NULL;
