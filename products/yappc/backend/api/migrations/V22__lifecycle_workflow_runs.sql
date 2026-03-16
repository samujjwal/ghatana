-- V22: Durable state store for YAPPC lifecycle-service workflow runs.
-- Written by LifecycleJdbcWorkflowStateStore.
-- Mirrors the schema in V13__workflow_runs.sql (used by the backend/api module) but
-- is isolated to the lifecycle service so each module owns its own table.

CREATE TABLE IF NOT EXISTS lifecycle_workflow_runs (
    workflow_id    TEXT        NOT NULL PRIMARY KEY,
    status         TEXT        NOT NULL,                -- RunStatus enum name
    step_statuses  JSONB       NOT NULL DEFAULT '[]'::jsonb,  -- JSON array of StepStatus names
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE lifecycle_workflow_runs IS
    'Durable workflow run state for YAPPC lifecycle-service. '
    'Populated and queried exclusively by LifecycleJdbcWorkflowStateStore.';

COMMENT ON COLUMN lifecycle_workflow_runs.step_statuses IS
    'JSON array of StepStatus enum names, e.g. [\"PENDING\",\"RUNNING\",\"SUCCEEDED\"].';

-- Fast lookup by current status (e.g. find all RUNNING runs for metrics / stale-run GC)
CREATE INDEX IF NOT EXISTS idx_lifecycle_workflow_runs_status
    ON lifecycle_workflow_runs (status, updated_at DESC);
