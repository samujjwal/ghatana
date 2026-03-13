-- AEP Memory Plane tables (Phase 3)
-- Creates persistent storage for the GAA agent memory system:
--   agent_memory_items : episodic, semantic, procedural, preference, working, artifact tiers
--   aep_task_states    : multi-session task state keyed by (task_id, agent_id, tenant_id)

-- ─── Memory Items ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_memory_items (
    id              UUID        PRIMARY KEY,
    agent_id        TEXT        NOT NULL,
    tenant_id       TEXT        NOT NULL,
    sphere_id       TEXT,
    item_type       TEXT        NOT NULL,          -- EPISODIC | SEMANTIC | PROCEDURAL | PREFERENCE | WORKING | ARTIFACT
    payload         JSONB       NOT NULL,
    confidence      FLOAT8      NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0.0 AND 1.0),
    validity_status TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (validity_status IN ('ACTIVE','STALE','ARCHIVED','SUPERSEDED')),
    entrenchment    INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Primary lookup: agent memory items sorted newest-first within a tenant
CREATE INDEX IF NOT EXISTS idx_agent_memory_items_agent_tenant_type
    ON agent_memory_items (agent_id, tenant_id, item_type, created_at DESC);

-- Sphere-scoped lookup (shared memory spheres across agents)
CREATE INDEX IF NOT EXISTS idx_agent_memory_items_sphere
    ON agent_memory_items (sphere_id, item_type)
    WHERE sphere_id IS NOT NULL;

-- Validity filter (active items only)
CREATE INDEX IF NOT EXISTS idx_agent_memory_items_validity
    ON agent_memory_items (agent_id, tenant_id, validity_status)
    WHERE validity_status = 'ACTIVE';

-- ─── Task State ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS aep_task_states (
    task_id     TEXT        NOT NULL,
    agent_id    TEXT        NOT NULL,
    tenant_id   TEXT        NOT NULL,
    state_data  JSONB       NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking counter
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (task_id, agent_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_aep_task_states_agent_tenant
    ON aep_task_states (agent_id, tenant_id);

-- ─── Auto-update trigger for updated_at ───────────────────────────────────────
CREATE OR REPLACE FUNCTION aep_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_agent_memory_items_updated_at'
    ) THEN
        CREATE TRIGGER trg_agent_memory_items_updated_at
            BEFORE UPDATE ON agent_memory_items
            FOR EACH ROW EXECUTE FUNCTION aep_set_updated_at();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_aep_task_states_updated_at'
    ) THEN
        CREATE TRIGGER trg_aep_task_states_updated_at
            BEFORE UPDATE ON aep_task_states
            FOR EACH ROW EXECUTE FUNCTION aep_set_updated_at();
    END IF;
END;
$$;
