-- The company's logo for an application, from its posting (e.g. the logo LinkedIn shows next to the company), made
-- into a small PNG: the pages show it before the job's title. It goes with its application.
CREATE TABLE company_logos (
    application_id BIGINT      PRIMARY KEY REFERENCES applications (id) ON DELETE CASCADE,
    image          BYTEA       NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
