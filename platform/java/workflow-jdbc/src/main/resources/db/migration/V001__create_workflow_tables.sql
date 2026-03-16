-- =============================================================================
-- V001__create_workflow_tables.sql
-- Workflow platform DDL — PostgreSQL 14+
-- =============================================================================

-- Workflow Definitions (versioned blueprints)
CREATE TABLE IF NOT EXISTS workflow_definitions (
    workflow_id   TEXT        NOT NULL,
    version       INT         NOT NULL,
    name          TEXT        NOT NULL,
    trigger_type  TEXT        NOT NULL DEFAULT 'API',
    trigger_filter TEXT,
    steps         JSONB       NOT NULL,
    entry_step_id TEXT        NOT NULL,
    timeout_ms    BIGINT,
    saga_policy   TEXT        NOT NULL DEFAULT 'NONE',
    metadata      JSONB       NOT NULL DEFAULT '{}',
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workflow_id, version)
);

CREATE INDEX idx_wf_def_latest ON workflow_definitions (workflow_id, version DESC);

-- Workflow Runs (runtime state)
CREATE TABLE IF NOT EXISTS workflow_runs (
    run_id          TEXT        PRIMARY KEY,
    workflow_id     TEXT        NOT NULL,
    tenant_id       TEXT,
    kind            TEXT        NOT NULL DEFAULT 'DURABLE',
    status          TEXT        NOT NULL DEFAULT 'PENDING',
    current_step_id TEXT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    variables       JSONB       NOT NULL DEFAULT '{}',
    triggered_by    TEXT
);

CREATE INDEX idx_wf_run_workflow  ON workflow_runs (workflow_id);
CREATE INDEX idx_wf_run_tenant    ON workflow_runs (tenant_id);
CREATE INDEX idx_wf_run_status    ON workflow_runs (status);

-- Workflow Run History (per-step audit trail)
CREATE TABLE IF NOT EXISTS workflow_run_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id      TEXT        NOT NULL REFERENCES workflow_runs (run_id) ON DELETE CASCADE,
    step_id     TEXT        NOT NULL,
    phase       TEXT        NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    details     JSONB
);

CREATE INDEX idx_wf_hist_run ON workflow_run_history (run_id);

-- Wait Conditions (for WAIT steps)
CREATE TABLE IF NOT EXISTS workflow_wait_conditions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id          TEXT NOT NULL REFERENCES workflow_runs (run_id) ON DELETE CASCADE,
    wait_kind       TEXT NOT NULL,
    event_type      TEXT,
    correlation_key TEXT,
    fire_at         TIMESTAMPTZ,
    fired           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wf_wait_run     ON workflow_wait_conditions (run_id);
CREATE INDEX idx_wf_wait_pending ON workflow_wait_conditions (fired, fire_at)
    WHERE fired = FALSE;
