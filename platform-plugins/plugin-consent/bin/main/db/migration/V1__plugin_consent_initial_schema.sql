-- Durable Consent Plugin — Initial Schema
-- KP-023: Flyway migration for plugin_consent_records table
--
-- Append-only consent log. Every GRANT / DENY / WITHDRAW action creates a new row.
-- Revocations are recorded via revokedAt + status. The "current" consent for a
-- subject+purpose pair is determined by the most-recent row ordered by created_at.
--
-- Retention windows by domain:
--   - healthcare: 7 years (Nepal Directive 2081)
--   - domain-alpha:    5 years
--   - general:    1 year
--
-- expires_at is pre-computed at INSERT time from the action timestamp + domain retention.

CREATE TABLE IF NOT EXISTS plugin_consent_records (
    consent_id   VARCHAR(128)  NOT NULL,
    subject_id   VARCHAR(256)  NOT NULL,
    purpose      VARCHAR(512)  NOT NULL,
    action       VARCHAR(32)   NOT NULL,   -- GRANT | DENY | WITHDRAW
    status       VARCHAR(32)   NOT NULL,   -- GRANTED | DENIED | REVOKED | EXPIRED
    created_at   BIGINT        NOT NULL,
    expires_at   BIGINT,                   -- epoch milli; NULL means no expiry
    revoked_at   BIGINT,                   -- set when status transitions to REVOKED

    CONSTRAINT pk_plugin_consent_records PRIMARY KEY (consent_id)
);

-- Index used for lookup of all records for a subject (consent history)
CREATE INDEX IF NOT EXISTS idx_plugin_consent_records_subject
    ON plugin_consent_records (subject_id, created_at ASC);

-- Index used for efficient "current consent" query (most-recent for subject+purpose)
CREATE INDEX IF NOT EXISTS idx_plugin_consent_records_subject_purpose
    ON plugin_consent_records (subject_id, purpose, created_at DESC);

-- Index to support expiry maintenance (purgeExpiredConsents scheduled job)
CREATE INDEX IF NOT EXISTS idx_plugin_consent_records_expires_at
    ON plugin_consent_records (expires_at)
    WHERE expires_at IS NOT NULL;
