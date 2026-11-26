-- A job's page asks the assistant for two more things: to research the job's company (its reviews, what it pays, its
-- benefits, its news, how its interviews go; the jobs module keeps what it finds, in company_research), and to find
-- the company's logo (which the jobs module keeps in company_logos, as it does the one a posting shows). Like the other
-- requests from a job's page, they name their job. The checks, named by PostgreSQL after their table and column, let
-- them in.
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_kind_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_kind_check
    CHECK (kind IN ('FIND_JOBS_FOR_CV', 'FIND_JOBS', 'RUN_SAVED_SEARCHES', 'CHECK_POSTINGS', 'FOLLOW_UP', 'TAILOR_CV',
                    'FIND_HIRING_POSTS', 'FIND_RECRUITERS', 'AUTO_APPLY', 'PREPARE_INTERVIEW', 'SCORE_JOB',
                    'SCORE_JOBS', 'SORT_INBOX', 'WRITE_PROJECTS', 'WRITE_COVER_LETTER', 'WRITE_OUTREACH',
                    'NEGOTIATE_OFFER', 'WRITE_STORIES', 'RESEARCH_COMPANY', 'FIND_LOGO'));

ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_application_id_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_application_id_check
    CHECK ((kind IN ('TAILOR_CV', 'PREPARE_INTERVIEW', 'SCORE_JOB', 'WRITE_COVER_LETTER', 'WRITE_OUTREACH',
                     'NEGOTIATE_OFFER', 'RESEARCH_COMPANY', 'FIND_LOGO')) = (application_id IS NOT NULL));
