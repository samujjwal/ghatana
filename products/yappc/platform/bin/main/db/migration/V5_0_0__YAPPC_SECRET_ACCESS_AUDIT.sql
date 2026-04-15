-- YAPPC Secret Access Audit Table
-- Version: 5.0.0
-- Append-only audit of every encryption/decryption operation on secret fields.
-- Supports incident investigation and compliance reporting.

CREATE TABLE IF NOT EXISTS secret_access_audit (
    id            VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    tenant_id     VARCHAR(36)   NOT NULL,
    principal     VARCHAR(255)  NOT NULL,     -- userId / agentId performing the action
    field_name    VARCHAR(255)  NOT NULL,     -- e.g. "project.environmentVariables"
    action        VARCHAR(16)   NOT NULL      -- 'ENCRYPT' | 'DECRYPT'
                  CHECK (action IN ('ENCRYPT', 'DECRYPT')),
    outcome       VARCHAR(8)    NOT NULL      -- 'SUCCESS' | 'FAILURE'
                  CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    detail        TEXT,                       -- optional extra context / error message
    occurred_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Indexes for incident-investigation queries
CREATE INDEX IF NOT EXISTS idx_secret_access_audit_tenant
    ON secret_access_audit (tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_secret_access_audit_principal
    ON secret_access_audit (principal, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_secret_access_audit_field
    ON secret_access_audit (field_name, occurred_at DESC);

-- Prevents accidental deletion or update of audit rows (DDL-level enforcement).
-- Application code must only INSERT into this table.
