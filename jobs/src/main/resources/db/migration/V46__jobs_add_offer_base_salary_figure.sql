-- An offer's base salary as a figure: an amount, its currency and what it pays for, picked on the job's page rather
-- than typed in words. The words saved before stay in base_salary, read until the offer's figure is saved.
ALTER TABLE job_offers
    ADD COLUMN base_salary_amount   NUMERIC(14, 2) CHECK (base_salary_amount > 0),
    ADD COLUMN base_salary_currency VARCHAR(3),
    ADD COLUMN base_salary_period   VARCHAR(5) CHECK (base_salary_period IN ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR'));
