-- ============================================================================
-- V001: Agent Registry Schema
-- Stores agent descriptors, configurations, and capability index for
-- durable, multi-tenant agent discovery across nodes.
-- ============================================================================

CREATE TABLE IF NOT EXISTS agent_registrations (
    agent_id        VARCHAR(200)        NOT NULL,
    tenant_id       VARCHAR(100)        NOT NULL,
    agent_type      VARCHAR(100)        NOT NULL,
    descriptor_json JSONB               NOT NULL,  -- Serialized AgentDescriptor
    config_json     JSONB               NOT NULL,  -- Serialized AgentConfig
    status          VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE',
    registered_at   TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    heartbeat_at    TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    node_id         VARCHAR(200),                  -- Node that registered this agent

    CONSTRAINT pk_agent_registrations PRIMARY KEY (agent_id, tenant_id),
    CONSTRAINT chk_agent_status CHECK (status IN ('ACTIVE', 'DEGRADED', 'INACTIVE', 'DEREGISTERED'))
);

-- Fast look-up by tenant
CREATE INDEX IF NOT EXISTS idx_agent_reg_tenant
    ON agent_registrations (tenant_id, status);

-- Fast look-up by tenant + type
CREATE INDEX IF NOT EXISTS idx_agent_reg_tenant_type
    ON agent_registrations (tenant_id, agent_type, status);

-- Heartbeat-based expiry queries
CREATE INDEX IF NOT EXISTS idx_agent_reg_heartbeat
    ON agent_registrations (tenant_id, heartbeat_at);

-- ============================================================================
-- Capability index: one row per (agentId, tenantId, capability)
-- Allows O(1) look-up of all agents with a given capability.
-- ============================================================================

CREATE TABLE IF NOT EXISTS agent_capabilities (
    agent_id    VARCHAR(200)    NOT NULL,
    tenant_id   VARCHAR(100)    NOT NULL,
    capability  VARCHAR(200)    NOT NULL,
    added_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_agent_capabilities PRIMARY KEY (agent_id, tenant_id, capability),
    CONSTRAINT fk_agent_cap_reg FOREIGN KEY (agent_id, tenant_id)
        REFERENCES agent_registrations (agent_id, tenant_id) ON DELETE CASCADE
);

-- Primary capability search index
CREATE INDEX IF NOT EXISTS idx_agent_cap_lookup
    ON agent_capabilities (tenant_id, capability);

-- ============================================================================
-- Auto-update updated_at timestamp
-- ============================================================================

CREATE OR REPLACE FUNCTION agent_registrations_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_agent_registrations_updated_at
    BEFORE UPDATE ON agent_registrations
    FOR EACH ROW EXECUTE FUNCTION agent_registrations_updated_at();
