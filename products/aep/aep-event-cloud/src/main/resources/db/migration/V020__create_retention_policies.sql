-- Flyway V020: Retention-policy and governance tables for AEP compliance
-- Purpose: Store GDPR retention deadlines, consent records, kill-switch state,
--          policy rules, degradation modes, and change-approval requests
--          in durable PostgreSQL tables.
-- Generated: 2026-04-14

-- ============================================================================
-- retention_policies — GDPR data-retention deadlines (AEP aep-compliance)
-- ============================================================================
CREATE TABLE IF NOT EXISTS retention_policies (
    tenant_id              VARCHAR(255) NOT NULL,
    data_id                VARCHAR(512) NOT NULL,
    expires_at             TIMESTAMPTZ  NOT NULL,
    scheduled_for_deletion BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    PRIMARY KEY (tenant_id, data_id)
);

CREATE INDEX IF NOT EXISTS idx_retention_policies_expires_at
    ON retention_policies (expires_at)
    WHERE scheduled_for_deletion = FALSE;

-- ============================================================================
-- consent_records — GDPR consent by (tenant, subject, purpose)
-- ============================================================================
CREATE TABLE IF NOT EXISTS consent_records (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   VARCHAR(255) NOT NULL,
    subject_id  VARCHAR(512) NOT NULL,
    purpose     VARCHAR(512) NOT NULL,
    granted     BOOLEAN      NOT NULL DEFAULT TRUE,
    granted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,

    UNIQUE (tenant_id, subject_id, purpose)
);

CREATE INDEX IF NOT EXISTS idx_consent_records_lookup
    ON consent_records (tenant_id, subject_id, purpose, granted);

-- ============================================================================
-- kill_switch_state — per-tenant and global kill-switch flags
-- ============================================================================
CREATE TABLE IF NOT EXISTS kill_switch_state (
    scope       VARCHAR(512) PRIMARY KEY,  -- 'global' or tenant_id
    active      BOOLEAN      NOT NULL DEFAULT FALSE,
    reason      TEXT,
    incident_id VARCHAR(255),
    activated_at TIMESTAMPTZ,
    deactivated_at TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Ensure the global sentinel row always exists
INSERT INTO kill_switch_state (scope, active)
VALUES ('global', FALSE)
ON CONFLICT (scope) DO NOTHING;

-- ============================================================================
-- policy_rules — named policy rules evaluated by PostgresPolicyEngine
-- ============================================================================
CREATE TABLE IF NOT EXISTS policy_rules (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   VARCHAR(255) NOT NULL,
    policy_name VARCHAR(512) NOT NULL,
    condition   JSONB        NOT NULL,  -- {"field": "action", "op": "eq", "value": "DELETE"}
    effect      VARCHAR(10)  NOT NULL DEFAULT 'DENY',   -- ALLOW | DENY
    risk_score  SMALLINT     NOT NULL DEFAULT 50,
    reason      TEXT         NOT NULL DEFAULT '',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, policy_name, condition)
);

CREATE INDEX IF NOT EXISTS idx_policy_rules_lookup
    ON policy_rules (tenant_id, policy_name, enabled);

-- ============================================================================
-- degradation_modes — per-tenant graceful degradation state (Redis fallback)
-- ============================================================================
CREATE TABLE IF NOT EXISTS degradation_modes (
    tenant_id  VARCHAR(255) PRIMARY KEY,
    mode       VARCHAR(50)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL | REDUCED | MINIMAL | OFFLINE
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- change_requests — risk-gated change approval workflow
-- ============================================================================
CREATE TABLE IF NOT EXISTS change_requests (
    change_id         UUID         PRIMARY KEY,
    tenant_id         VARCHAR(255) NOT NULL,
    requesting_agent  VARCHAR(512) NOT NULL,
    change_type       VARCHAR(100) NOT NULL,
    description       TEXT         NOT NULL,
    metadata          JSONB        NOT NULL DEFAULT '{}',
    status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING_REVIEW',
    risk_score        SMALLINT     NOT NULL,
    reviewer_id       VARCHAR(512),
    review_notes      TEXT,
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_change_requests_tenant_status
    ON change_requests (tenant_id, status)
    WHERE status = 'PENDING_REVIEW';
