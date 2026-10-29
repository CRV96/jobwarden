-- The extension's Easy Apply run sends applications on LinkedIn too: those are applied to by the extension.
ALTER TABLE applications DROP CONSTRAINT applications_applied_by_check;
ALTER TABLE applications ADD CONSTRAINT applications_applied_by_check
    CHECK (applied_by IN ('YOU', 'CLAUDE', 'EXTENSION'));
