-- Two statuses that end an application without a rejection: No response (never heard back) and Position closed (the
-- company closed it). And a rejection reason for the automatic rejection email that gives none.
ALTER TABLE applications DROP CONSTRAINT applications_current_status_check;
ALTER TABLE applications ADD CONSTRAINT applications_current_status_check
    CHECK (current_status IN ('SAVED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'NO_RESPONSE', 'POSITION_CLOSED',
                              'WITHDRAWN'));

ALTER TABLE application_events DROP CONSTRAINT application_events_stage_category_check;
ALTER TABLE application_events ADD CONSTRAINT application_events_stage_category_check
    CHECK (stage_category IN ('SAVED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'NO_RESPONSE', 'POSITION_CLOSED',
                              'WITHDRAWN'));

ALTER TABLE application_events DROP CONSTRAINT application_events_rejection_reason_check;
ALTER TABLE application_events ADD CONSTRAINT application_events_rejection_reason_check
    CHECK (rejection_reason IN ('SALARY_MISMATCH', 'POSITION_FILLED', 'LACK_OF_EXPERIENCE', 'NO_RESPONSE',
                                'AUTOMATED_EMAIL', 'OTHER'));
