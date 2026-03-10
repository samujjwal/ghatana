-- V13: Durable workflow run state
-- Replaces InMemoryWorkflowStateStore used inside DurableWorkflowEngine.
-- Rows are upserted on every save() call.

CREATE TABLE IF NOT EXISTS workflow_runs (
    workflow_id    TEXT        NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'RUNNING',
    step_statuses  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_workflow_runs PRIMARY KEY (workflow_id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_runs_status
    ON workflow_runs (status);

CREATE INDEX IF NOT EXISTS idx_workflow_runs_updated
    ON workflow_runs (updated_at DESC);

COMMENT ON TABLE workflow_runs IS
    'Durable workflow execution state. Replaces InMemoryWorkflowStateStore (Phase 1.1).';

COMMENT ON COLUMN workflow_runs.step_statuses IS
    'JSON array of StepStatus enum names (PENDING/RUNNING/COMPLETED/FAILED) in execution order.';
