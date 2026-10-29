-- The app's key for Adzuna's API, entered in Settings: one row at most, the same for every profile. It takes the place
-- of the ADZUNA_APP_ID and ADZUNA_APP_KEY environment variables, which stay the fallback.
CREATE TABLE market_adzuna_key (
    id       SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    app_id   VARCHAR(100) NOT NULL,
    app_key  VARCHAR(100) NOT NULL,
    saved_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
