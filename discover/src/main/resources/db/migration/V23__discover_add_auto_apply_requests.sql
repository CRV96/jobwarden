-- The Auto-apply page asks Claude to apply to the jobs it queued.
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_kind_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_kind_check
    CHECK (kind IN ('FIND_JOBS_FOR_CV', 'FIND_JOBS', 'RUN_SAVED_SEARCHES', 'CHECK_POSTINGS', 'FOLLOW_UP', 'TAILOR_CV',
                    'FIND_HIRING_POSTS', 'FIND_RECRUITERS', 'AUTO_APPLY'));
