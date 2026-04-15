-- YAPPC Encryption Key Versions Table
-- Version: 6.0.0
-- Tracks all encryption key versions to support zero-downtime key rotation.
-- The 'ACTIVE' key is used for all new encryption operations.
-- Previous keys are kept for decryption of existing data during re-encryption.

CREATE TABLE IF NOT EXISTS key_versions (
    version_id    VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    key_alias     VARCHAR(255) NOT NULL,            -- human-readable identifier, e.g. 'yappc-main-key'
    status        VARCHAR(12)  NOT NULL             -- 'ACTIVE' | 'SUPERSEDED' | 'REVOKED'
                  CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'REVOKED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    superseded_at TIMESTAMPTZ,                      -- when this key was replaced by a newer key
    revoked_at    TIMESTAMPTZ,                      -- when this key was permanently disabled
    created_by    VARCHAR(255) NOT NULL DEFAULT 'system'
);

-- Only one ACTIVE key per alias is allowed — enforced at application level.
-- An index helps fast lookups for the current active key.
CREATE INDEX IF NOT EXISTS idx_key_versions_alias_status
    ON key_versions (key_alias, status);

-- Re-encryption job tracking table.
-- Each row represents a batch re-encryption task when a new key becomes active.
CREATE TABLE IF NOT EXISTS key_rotation_jobs (
    job_id          VARCHAR(36)  PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    key_alias       VARCHAR(255) NOT NULL,
    old_version_id  VARCHAR(36)  NOT NULL REFERENCES key_versions(version_id),
    new_version_id  VARCHAR(36)  NOT NULL REFERENCES key_versions(version_id),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'  -- 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED'
                    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETE', 'FAILED')),
    total_records   BIGINT,
    processed       BIGINT       NOT NULL DEFAULT 0,
    failed_records  BIGINT       NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_detail    TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_key_rotation_jobs_status
    ON key_rotation_jobs (status, created_at DESC);
