-- Who to follow up with about an application, and the dates besides the timeline: when applications to the posting
-- close, and when to follow up next.
ALTER TABLE applications
    ADD COLUMN contact_name  TEXT,
    ADD COLUMN contact_role  TEXT,
    ADD COLUMN contact_email TEXT,
    ADD COLUMN contact_phone TEXT,
    ADD COLUMN contact_link  TEXT,
    ADD COLUMN referred_by   TEXT,
    ADD COLUMN deadline      DATE,
    ADD COLUMN follow_up_on  DATE;

-- How and where an interview takes place. The event's date and time are the interview's, its stage label the round.
ALTER TABLE application_events
    ADD COLUMN interview_format   VARCHAR(20)
        CHECK (interview_format IN ('PHONE', 'VIDEO', 'ON_SITE', 'TAKE_HOME')),
    ADD COLUMN interview_location TEXT,
    ADD COLUMN duration_minutes   INTEGER CHECK (duration_minutes > 0),
    ADD CONSTRAINT interview_details_only_when_interviewing
        CHECK ((interview_format IS NULL AND interview_location IS NULL AND duration_minutes IS NULL)
            OR stage_category = 'INTERVIEWING');

CREATE INDEX applications_user_id_follow_up_on_idx ON applications (user_id, follow_up_on) WHERE follow_up_on IS NOT NULL;
