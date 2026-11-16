-- A job's page asks Claude for a cover letter, for messages to the people hiring and for a brief to negotiate its
-- offer, and the CV page for stories for the story bank.
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_kind_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_kind_check
    CHECK (kind IN ('FIND_JOBS_FOR_CV', 'FIND_JOBS', 'RUN_SAVED_SEARCHES', 'CHECK_POSTINGS', 'FOLLOW_UP', 'TAILOR_CV',
                    'FIND_HIRING_POSTS', 'FIND_RECRUITERS', 'AUTO_APPLY', 'PREPARE_INTERVIEW', 'SCORE_JOB', 'SCORE_JOBS',
                    'SORT_INBOX', 'WRITE_PROJECTS', 'WRITE_COVER_LETTER', 'WRITE_OUTREACH', 'NEGOTIATE_OFFER',
                    'WRITE_STORIES'));

-- The cover letter, the messages and the negotiation brief are each about one job, which the request names
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_application_id_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_application_id_check
    CHECK ((kind IN ('TAILOR_CV', 'PREPARE_INTERVIEW', 'SCORE_JOB', 'WRITE_COVER_LETTER', 'WRITE_OUTREACH',
                     'NEGOTIATE_OFFER')) = (application_id IS NOT NULL));
