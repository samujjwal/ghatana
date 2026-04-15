-- V013__create_agent_releases.sql
-- Creates the agent_releases table for persisting AgentRelease lifecycle records.
-- All agent release records are stored as JSONB data alongside standard entity columns
-- (tenant isolation, soft-delete, versioning) to align with the platform entity model.

CREATE TABLE IF NOT EXISTS agent_releases (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   TEXT        NOT NULL,
    agent_release_id            TEXT        NOT NULL,
    agent_id                    TEXT        NOT NULL,
    spec_version                TEXT        NOT NULL DEFAULT '1.0.0',
    release_version             TEXT        NOT NULL,
    state                       TEXT        NOT NULL DEFAULT 'DRAFT',
    spec_digest                 TEXT,
    policy_pack_id              TEXT,
    policy_pack_digest          TEXT,
    evaluation_pack_id          TEXT,
    evaluation_pack_digest      TEXT,
    memory_contract_id          TEXT,
    signing_reference           TEXT,
    tool_contract_version       TEXT,
    telemetry_contract_version  TEXT,
    explanation_contract_version TEXT,
    redaction_profile_id        TEXT,
    threat_model_id             TEXT,
    capability_maturity_profile TEXT,
    compatible_runtime_versions JSONB       NOT NULL DEFAULT '[]',
    data_classes_handled        JSONB       NOT NULL DEFAULT '[]',
    permitted_purposes          JSONB       NOT NULL DEFAULT '[]',
    data                        JSONB       NOT NULL DEFAULT '{}',
    created_by                  TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active                      BOOLEAN     NOT NULL DEFAULT TRUE,
    version                     INTEGER     NOT NULL DEFAULT 1
);

-- Unique constraint: one canonical release record per tenant + release ID
CREATE UNIQUE INDEX IF NOT EXISTS uidx_agent_releases_tenant_release_id
    ON agent_releases (tenant_id, agent_release_id);

-- Query index: look up all releases for an agent within a tenant
CREATE INDEX IF NOT EXISTS idx_agent_releases_tenant_agent
    ON agent_releases (tenant_id, agent_id);

-- Query index: look up all releases in a given state
CREATE INDEX IF NOT EXISTS idx_agent_releases_state
    ON agent_releases (tenant_id, state);

-- Query index: active releases per agent (used by findActiveRelease)
CREATE INDEX IF NOT EXISTS idx_agent_releases_active_agent
    ON agent_releases (tenant_id, agent_id, state)
    WHERE state = 'ACTIVE' AND active = TRUE;

-- Audit comment
COMMENT ON TABLE agent_releases IS
    'Persisted lifecycle records for versioned, signed agent releases. '
    'Each row represents one agentReleaseId; state transitions update the state column.';
