-- V004: Config version history, rollback support, effective dates (K02-009/010)

-- Append-only version history — existing config_entries remain the live values
CREATE TABLE config_version_history (
    id              BIGSERIAL     PRIMARY KEY,
    namespace       VARCHAR(128)  NOT NULL,
    config_key      VARCHAR(256)  NOT NULL,
    hierarchy_level VARCHAR(32)   NOT NULL,
    level_id        VARCHAR(128),
    config_value    TEXT          NOT NULL,
    encrypted       BOOLEAN       NOT NULL DEFAULT FALSE,
    changed_by      VARCHAR(128)  NOT NULL,
    change_reason   TEXT,
    effective_from  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    effective_to    TIMESTAMPTZ,  -- null = currently active
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Prevent any mutation of history rows
REVOKE UPDATE, DELETE ON config_version_history FROM PUBLIC;

-- Effective date index for temporal queries
CREATE INDEX idx_config_history_effective
    ON config_version_history (namespace, config_key, hierarchy_level, effective_from)
 WHERE effective_to IS NULL;

-- Tenant + namespace lookup
CREATE INDEX idx_config_history_level_id
    ON config_version_history (level_id, namespace);

-- Add effective_from to config_entries for future temporal scheduling
ALTER TABLE config_entries
    ADD COLUMN IF NOT EXISTS effective_from  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_to    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS changed_by      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS change_reason   TEXT;

COMMENT ON TABLE config_version_history IS
  'Append-only audit log of all config changes for rollback and temporal queries (K02-009/010)';
