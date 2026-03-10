-- V17: Agent memory persistence
-- Persists episodic memory for agent turns, supporting cross-restart memory continuity.
-- Semantic memory (facts), procedural memory (policies), and preferences use separate tables.

CREATE TABLE IF NOT EXISTS agent_episodes (
    id              VARCHAR(128) PRIMARY KEY,
    agent_id        VARCHAR(128) NOT NULL,
    tenant_id       VARCHAR(128) NOT NULL DEFAULT 'default',
    turn_id         VARCHAR(128),
    input_text      TEXT,
    output_text     TEXT,
    action          JSONB DEFAULT '{}',
    context         JSONB DEFAULT '{}',
    tags            JSONB DEFAULT '[]',
    reward          DOUBLE PRECISION DEFAULT 0.0,
    embedding       JSONB,                          -- optional float array
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS agent_episodes_agent_tenant
    ON agent_episodes(agent_id, tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS agent_episodes_tenant_time
    ON agent_episodes(tenant_id, occurred_at DESC);

-- ──────────────────────────────────────────────────────────────────────────────
-- Semantic memory (facts: subject-predicate-object triples)
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_facts (
    id              VARCHAR(128) PRIMARY KEY,
    agent_id        VARCHAR(128) NOT NULL,
    tenant_id       VARCHAR(128) NOT NULL DEFAULT 'default',
    subject         TEXT NOT NULL,
    predicate       TEXT NOT NULL,
    object          TEXT NOT NULL,
    confidence      DOUBLE PRECISION DEFAULT 1.0,
    source          TEXT,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS agent_facts_agent
    ON agent_facts(agent_id, tenant_id);

CREATE INDEX IF NOT EXISTS agent_facts_predicate
    ON agent_facts(predicate, tenant_id);

-- ──────────────────────────────────────────────────────────────────────────────
-- Procedural memory (learned policies)
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_policies (
    id              VARCHAR(128) PRIMARY KEY,
    agent_id        VARCHAR(128) NOT NULL,
    tenant_id       VARCHAR(128) NOT NULL DEFAULT 'default',
    situation       TEXT NOT NULL,
    action          TEXT NOT NULL,
    confidence      DOUBLE PRECISION DEFAULT 0.5,
    use_count       INT DEFAULT 0,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS agent_policies_agent
    ON agent_policies(agent_id, tenant_id, confidence DESC);

-- ──────────────────────────────────────────────────────────────────────────────
-- Preference memory (key-value per namespace per agent)
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_preferences (
    id              VARCHAR(128) PRIMARY KEY,
    agent_id        VARCHAR(128) NOT NULL,
    tenant_id       VARCHAR(128) NOT NULL DEFAULT 'default',
    namespace       TEXT NOT NULL,
    key             TEXT NOT NULL,
    value           TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (agent_id, tenant_id, namespace, key)
);

CREATE INDEX IF NOT EXISTS agent_preferences_agent_ns
    ON agent_preferences(agent_id, tenant_id, namespace);
