-- What an offer pays on top of its base salary, each with how often, so a year of it all can be counted: a bonus, meal
-- vouchers, shares, a signing bonus, or other money, from the company or from elsewhere. An amount is in a currency,
-- or a percentage of the base salary (%); without one, the note says what it is. In the order typed in; deleting the
-- offer deletes them.
CREATE TABLE job_offer_extras (
    offer_id  BIGINT        NOT NULL REFERENCES job_offers (id) ON DELETE CASCADE,
    position  INTEGER       NOT NULL CHECK (position >= 0),
    kind      VARCHAR(13)   NOT NULL CHECK (kind IN ('BONUS', 'MEAL_VOUCHERS', 'EQUITY', 'SIGNING_BONUS', 'OTHER')),
    amount    NUMERIC(14, 2) CHECK (amount > 0),
    unit      VARCHAR(3)    CHECK (unit ~ '^([A-Z]{3}|%)$'),
    frequency VARCHAR(11)   NOT NULL CHECK (frequency IN ('WORKING_DAY', 'MONTH', 'QUARTER', 'YEAR', 'ONCE', 'EVENT')),
    note      VARCHAR(200),
    PRIMARY KEY (offer_id, position),
    CHECK ((amount IS NULL) = (unit IS NULL)),
    CHECK (amount IS NOT NULL OR note IS NOT NULL)
);

-- The bonus, the signing bonus and the shares, typed in as words before, become extras that say them in their note,
-- for the amount to be typed in; a nought said there was none
INSERT INTO job_offer_extras (offer_id, position, kind, frequency, note)
SELECT offer_id, (ROW_NUMBER() OVER (PARTITION BY offer_id ORDER BY place) - 1)::INTEGER, kind, frequency, LEFT(words, 200)
FROM (SELECT id AS offer_id, 1 AS place, 'BONUS' AS kind, 'YEAR' AS frequency, btrim(bonus) AS words FROM job_offers
      UNION ALL
      SELECT id, 2, 'SIGNING_BONUS', 'ONCE', btrim(signing_bonus) FROM job_offers
      UNION ALL
      SELECT id, 3, 'EQUITY', 'YEAR', btrim(equity) FROM job_offers) AS typed
WHERE words <> '' AND words !~ '^0+([.,]0+)?$';

ALTER TABLE job_offers
    DROP COLUMN bonus,
    DROP COLUMN signing_bonus,
    DROP COLUMN equity;
