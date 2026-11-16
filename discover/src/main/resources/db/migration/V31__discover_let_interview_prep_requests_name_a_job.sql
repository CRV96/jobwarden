-- A request to prepare for an interview names its job, like one to tailor the CV.
ALTER TABLE ai_requests DROP CONSTRAINT ai_requests_application_id_check;
ALTER TABLE ai_requests ADD CONSTRAINT ai_requests_application_id_check
    CHECK ((kind IN ('TAILOR_CV', 'PREPARE_INTERVIEW')) = (application_id IS NOT NULL));
