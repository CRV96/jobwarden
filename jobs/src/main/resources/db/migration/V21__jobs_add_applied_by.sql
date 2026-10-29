-- Who sent the application: the profile, or Claude, applying for it from the Auto-apply page. Unknown (NULL) for
-- the applications from before, and for those never applied to.
ALTER TABLE applications ADD COLUMN applied_by VARCHAR(10) CHECK (applied_by IN ('YOU', 'CLAUDE'));
