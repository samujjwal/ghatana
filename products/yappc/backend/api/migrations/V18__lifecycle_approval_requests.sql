-- V18: Lifecycle approval requests
-- Durable storage for phase-advance approval gates.
-- Unlike the general approval_workflows table (V16), this stores simpler,
-- lifecycle-specific approval requests tied to project phase transitions.

CREATE TABLE IF NOT EXISTS yappc.approval_requests (
    id               TEXT PRIMARY KEY,
    project_id       TEXT NOT NULL,
    requesting_agent_id TEXT,
    approval_type    TEXT NOT NULL, -- PHASE_ADVANCE | DEPLOYMENT | RISK_ACCEPTANCE
    context          JSONB NOT NULL DEFAULT '{}',
    status           TEXT NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | EXPIRED
    tenant_id        TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at       TIMESTAMPTZ,
    decided_by       TEXT,
    expires_at       TIMESTAMPTZ,

    CONSTRAINT chk_approval_type   CHECK (approval_type IN ('PHASE_ADVANCE', 'DEPLOYMENT', 'RISK_ACCEPTANCE')),
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'))
);

-- Tenant-scoped PENDING queries (the hot path for UI polling)
CREATE INDEX IF NOT EXISTS idx_approval_requests_tenant_status
    ON yappc.approval_requests (tenant_id, status, created_at DESC);

-- Project-scoped lookups
CREATE INDEX IF NOT EXISTS idx_approval_requests_project
    ON yappc.approval_requests (tenant_id, project_id, status);

-- Agent-requested approvals
CREATE INDEX IF NOT EXISTS idx_approval_requests_agent
    ON yappc.approval_requests (tenant_id, requesting_agent_id, created_at DESC);

-- Auto-expire PENDING requests that are past their expiry timestamp
-- (run periodically by a maintenance job or application startup)
CREATE INDEX IF NOT EXISTS idx_approval_requests_expiry
    ON yappc.approval_requests (status, expires_at)
    WHERE status = 'PENDING' AND expires_at IS NOT NULL;

COMMENT ON TABLE yappc.approval_requests IS
    'Lifecycle-specific approval requests for phase-advance gates (Lifecycle 3.5).';
COMMENT ON COLUMN yappc.approval_requests.approval_type IS
    'PHASE_ADVANCE=lifecycle transition, DEPLOYMENT=prod deploy gate, RISK_ACCEPTANCE=risk sign-off';
COMMENT ON COLUMN yappc.approval_requests.context IS
    'Rich context payload: fromPhase, toPhase, gateResults, blockReason, and any custom fields';
COMMENT ON COLUMN yappc.approval_requests.expires_at IS
    'Optional SLA deadline; NULL means no expiry. Expired requests must be re-submitted.';
