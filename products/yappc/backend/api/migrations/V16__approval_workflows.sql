-- V16: Approval workflows table
-- Provides durable storage for YAPPC approval workflows (replaces in-memory ConcurrentHashMap).
-- Full workflow state (stages, records) is stored as a single JSONB blob for simplicity.

CREATE TABLE IF NOT EXISTS approval_workflows (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(255) NOT NULL,
    state_json  JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Fast lookup of all workflows for a tenant
CREATE INDEX IF NOT EXISTS idx_approval_workflows_tenant
    ON approval_workflows (tenant_id, updated_at DESC);

-- Fast status-based queries (extracted from JSONB)
CREATE INDEX IF NOT EXISTS idx_approval_workflows_status
    ON approval_workflows ((state_json ->> 'status'));

COMMENT ON TABLE  approval_workflows         IS 'Durable approval workflow state (full blob per workflow)';
COMMENT ON COLUMN approval_workflows.state_json IS 'Complete workflow state JSON including stages and approval records';
