-- Position reopened: the company opened the position again after closing it, so the job is in play again, and may
-- close again.
ALTER TABLE applications DROP CONSTRAINT applications_current_status_check;
ALTER TABLE applications ADD CONSTRAINT applications_current_status_check
    CHECK (current_status IN ('SAVED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'POSITION_REOPENED', 'REJECTED', 'NO_RESPONSE',
                              'POSITION_CLOSED', 'WITHDRAWN'));

ALTER TABLE application_events DROP CONSTRAINT application_events_stage_category_check;
ALTER TABLE application_events ADD CONSTRAINT application_events_stage_category_check
    CHECK (stage_category IN ('SAVED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'POSITION_REOPENED', 'REJECTED', 'NO_RESPONSE',
                              'POSITION_CLOSED', 'WITHDRAWN'));

-- When the profile archived the job, by hand or with the jobs unchanged since a day; null while it's in use. Archived
-- jobs leave the list, the pipeline, the calendar and its feed, and the offers, and stay in the statistics and the
-- salaries.
ALTER TABLE applications ADD COLUMN archived_at TIMESTAMPTZ;
CREATE INDEX applications_user_id_archived_idx ON applications (user_id) WHERE archived_at IS NOT NULL;
