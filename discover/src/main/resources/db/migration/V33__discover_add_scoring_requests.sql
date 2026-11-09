-- A job's page asks Claude to score how well the profile fits it, and the AI Assistant page to score the jobs with no
-- score yet.
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_kind_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_kind_check
    CHECK (kind IN ('FIND_JOBS_FOR_CV', 'FIND_JOBS', 'RUN_SAVED_SEARCHES', 'CHECK_POSTINGS', 'FOLLOW_UP', 'TAILOR_CV',
                    'FIND_HIRING_POSTS', 'FIND_RECRUITERS', 'AUTO_APPLY', 'PREPARE_INTERVIEW', 'SCORE_JOB', 'SCORE_JOBS'));
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_application_id_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_application_id_check
    CHECK ((kind IN ('TAILOR_CV', 'PREPARE_INTERVIEW', 'SCORE_JOB')) = (application_id IS NOT NULL));
