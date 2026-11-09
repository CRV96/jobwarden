-- How a mailbox is read, as connecting it found out: over TLS from the start, or after STARTTLS (starttls), like Proton
-- Mail Bridge. For a server on this computer, which signs its own certificate, the certificate it showed then (its
-- SHA-256 fingerprint): the only one trusted after. last_problem says what went wrong the last time, when JobWarden can
-- say it in the profile's language rather than as the server did.
ALTER TABLE mail_accounts
    ADD COLUMN starttls     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN certificate  TEXT,
    ADD COLUMN last_problem VARCHAR(30) CHECK (last_problem IN ('LOCAL_SERVER_DOWN', 'CERTIFICATE_CHANGED'));
