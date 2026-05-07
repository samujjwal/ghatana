CREATE TABLE IF NOT EXISTS task_states (
    task_id        TEXT PRIMARY KEY,
    agent_id       TEXT NOT NULL,
    tenant_id      TEXT NOT NULL,
    description    TEXT NOT NULL,
    status         TEXT NOT NULL,
    phases         JSONB NOT NULL DEFAULT '[]'::jsonb,
    checkpoints    JSONB NOT NULL DEFAULT '[]'::jsonb,
    blockers       JSONB NOT NULL DEFAULT '[]'::jsonb,
    invariants     JSONB NOT NULL DEFAULT '[]'::jsonb,
    done_criteria  JSONB NOT NULL DEFAULT '[]'::jsonb,
    dependencies   JSONB NOT NULL DEFAULT '[]'::jsonb,
    environment    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ,
    archived_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_task_states_agent_status
    ON task_states (agent_id, status);

CREATE INDEX IF NOT EXISTS idx_task_states_updated_at
    ON task_states (updated_at);
