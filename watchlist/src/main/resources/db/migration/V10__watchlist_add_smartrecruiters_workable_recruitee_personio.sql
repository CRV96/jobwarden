-- The watchlist reads job boards on SmartRecruiters, Workable, Recruitee and Personio too, by their public feeds for
-- careers sites. The check on ats, named by PostgreSQL after its table and column, lets them in.
ALTER TABLE watched_companies DROP CONSTRAINT watched_companies_ats_check;
ALTER TABLE watched_companies ADD CONSTRAINT watched_companies_ats_check
    CHECK (ats IN ('GREENHOUSE', 'LEVER', 'LEVER_EU', 'ASHBY', 'SMARTRECRUITERS', 'WORKABLE', 'RECRUITEE', 'PERSONIO'));
