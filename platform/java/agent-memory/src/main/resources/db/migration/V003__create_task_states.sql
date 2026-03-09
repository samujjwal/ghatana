-- ============================================================================
-- V003: Create task_states table
-- Dedicated table for task state memory with JSONB for flexible sub-structures.
-- ============================================================================

CREATE TABLE IF NOT EXISTS task_states (
    task_id         VARCHAR(200) PRIMARY KEY,
    agent_id        VARCHAR(100) NOT NULL,
    tenant_id       VARCHAR(100) NOT NULL,
    description     TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    -- Complex sub-structures stored as JSONB
    phases          JSONB NOT NULL DEFAULT '[]',
    checkpoints     JSONB NOT NULL DEFAULT '[]',
    blockers        JSONB NOT NULL DEFAULT '[]',
    invariants      JSONB NOT NULL DEFAULT '[]',
    done_criteria   JSONB NOT NULL DEFAULT '{}',
    dependencies    JSONB NOT NULL DEFAULT '[]',
    environment     JSONB NOT NULL DEFAULT '{}',

    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ,

    CONSTRAINT chk_task_status CHECK (status IN ('ACTIVE', 'PAUSED', 'BLOCKED', 'COMPLETED', 'FAILED', 'ARCHIVED'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_task_states_agent ON task_states (agent_id);
CREATE INDEX IF NOT EXISTS idx_task_states_tenant ON task_states (tenant_id);
CREATE INDEX IF NOT EXISTS idx_task_states_status ON task_states (status);
CREATE INDEX IF NOT EXISTS idx_task_states_active ON task_states (agent_id, status)
    WHERE status IN ('ACTIVE', 'PAUSED', 'BLOCKED');

-- Auto-update timestamp
CREATE OR REPLACE FUNCTION task_states_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_task_states_updated
    BEFORE UPDATE ON task_states
    FOR EACH ROW EXECUTE FUNCTION task_states_updated_at();
