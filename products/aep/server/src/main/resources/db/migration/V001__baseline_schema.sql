-- AEP Baseline Schema — V001
--
-- Creates the full AEP baseline schema using IF NOT EXISTS guards so this
-- migration is safe to run against both fresh databases and databases that
-- were bootstrapped before Flyway tracking was introduced.
--
-- The Flyway configuration sets baselineVersion="0" so that this migration
-- IS executed on every install — fresh and existing alike. The IF NOT EXISTS
-- guards prevent duplicate-table errors on pre-Flyway databases.
--
-- All subsequent migrations (V011 onward) build on top of this baseline.
--
-- @since 1.0.0
-- @doc.purpose Baseline DDL for all AEP core tables

-- ============================================================================
-- pipeline_checkpoints — pipeline execution checkpoints (orchestrator)
-- ============================================================================
CREATE TABLE IF NOT EXISTS pipeline_checkpoints (
    instance_id      TEXT PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    pipeline_id      VARCHAR(100) NOT NULL,
    idempotency_key  VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    state            JSONB,
    result           JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    current_step_id  VARCHAR(100),
    current_step_name VARCHAR(200),
    completed_steps  INT          NOT NULL DEFAULT 0,
    total_steps      INT          NOT NULL DEFAULT 0,
    version          BIGINT,
    UNIQUE (tenant_id, idempotency_key)
);

-- ============================================================================
-- aep_event_checkpoints — event-cloud operator window checkpoints
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_event_checkpoints (
    id          TEXT PRIMARY KEY,
    tenant_id   TEXT NOT NULL,
    operator_id TEXT NOT NULL,
    window_id   TEXT NOT NULL,
    state       JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ
);

-- ============================================================================
-- patterns — event-pattern definitions evaluated by the pattern engine
-- ============================================================================
CREATE TABLE IF NOT EXISTS patterns (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    TEXT        NOT NULL,
    name         TEXT        NOT NULL,
    description  TEXT,
    spec         JSONB       NOT NULL,
    labels       TEXT[],
    event_types  TEXT[],
    pattern_type TEXT,
    status       TEXT        NOT NULL DEFAULT 'DRAFT',
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    compiled_at  TIMESTAMPTZ,
    UNIQUE (tenant_id, name)
);

-- ============================================================================
-- agent_registry — registered AEP agents per tenant
-- ============================================================================
CREATE TABLE IF NOT EXISTS agent_registry (
    id         TEXT        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    agent_type TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE',
    config     JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- pipeline_registry — registered AEP pipelines per tenant
-- ============================================================================
CREATE TABLE IF NOT EXISTS pipeline_registry (
    id         TEXT        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    name       TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE',
    config     JSONB,
    version    INT         NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- audit_trail — immutable compliance event log
-- ============================================================================
CREATE TABLE IF NOT EXISTS audit_trail (
    id              TEXT        PRIMARY KEY,
    tenant_id       TEXT        NOT NULL,
    event_type      TEXT        NOT NULL,
    entity_id       TEXT,
    actor           TEXT,
    event_data      JSONB,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- aep_sessions — tenant authentication sessions
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_sessions (
    id         TEXT        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    token_hash TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- aep_audit_log — legacy high-frequency audit log (pre-V011 name)
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_audit_log (
    id         TEXT        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    actor      TEXT,
    action     TEXT        NOT NULL,
    entity_id  TEXT,
    details    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- aep_kill_switch — per-feature kill-switch flags (legacy table)
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_kill_switch (
    feature_key TEXT        PRIMARY KEY,
    enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- aep_policy_versions — policy-as-code versioned rules
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_policy_versions (
    policy_id  TEXT NOT NULL,
    version    INT  NOT NULL,
    payload    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (policy_id, version)
);

-- ============================================================================
-- aep_approvals — change-approval workflow records
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_approvals (
    id           TEXT        PRIMARY KEY,
    workflow     TEXT        NOT NULL,
    state        TEXT        NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at  TIMESTAMPTZ
);

-- ============================================================================
-- aep_consents — tenant consent records
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_consents (
    id         TEXT        PRIMARY KEY,
    tenant_id  TEXT        NOT NULL,
    purpose    TEXT        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

-- ============================================================================
-- aep_recertification — periodic access recertification entries
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_recertification (
    id           TEXT        PRIMARY KEY,
    tenant_id    TEXT        NOT NULL,
    subject_id   TEXT        NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
