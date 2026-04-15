-- V016: Create memory_namespaces table for agent memory isolation
--
-- Stores registered memory namespaces per tenant and agent. Each namespace
-- defines the scope (episodic, semantic, procedural, preference) of stored
-- memories and their retention/promotion policies.

CREATE TABLE IF NOT EXISTS memory_namespaces (
    id                  BIGSERIAL       PRIMARY KEY,
    namespace_id        VARCHAR(255)    NOT NULL,
    tenant_id           VARCHAR(255)    NOT NULL,
    agent_id            VARCHAR(255)    NOT NULL,
    scope               VARCHAR(50)     NOT NULL,
    label               VARCHAR(255)    NOT NULL,
    description         TEXT,
    retention_days      INTEGER         CHECK (retention_days > 0),
    promotion_enabled   BOOLEAN         NOT NULL DEFAULT FALSE,
    max_entries         INTEGER         CHECK (max_entries > 0),
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,
    data                JSONB           NOT NULL DEFAULT '{}',
    CONSTRAINT ck_memory_scope CHECK (
        scope IN ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'PREFERENCE')
    )
);

-- Unique namespace per tenant and agent
CREATE UNIQUE INDEX IF NOT EXISTS uidx_memory_namespaces_id
    ON memory_namespaces (tenant_id, namespace_id);

-- Lookup by agent: find all namespaces for a specific agent
CREATE INDEX IF NOT EXISTS idx_memory_namespaces_agent
    ON memory_namespaces (tenant_id, agent_id);

-- Lookup by scope across agents
CREATE INDEX IF NOT EXISTS idx_memory_namespaces_scope
    ON memory_namespaces (tenant_id, scope);

-- Partial index: promotion-enabled namespaces for promotion service queries
CREATE INDEX IF NOT EXISTS idx_memory_namespaces_promotion
    ON memory_namespaces (tenant_id, agent_id, scope)
    WHERE promotion_enabled = TRUE;
