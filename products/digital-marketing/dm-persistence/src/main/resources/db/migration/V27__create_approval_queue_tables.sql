-- P1-012 & P1-013: Create approval queue and delegation tables
--
-- This migration creates tables for canonical approval queue governance
-- and pending approval queue semantics.

-- Approval Queue Table
CREATE TABLE IF NOT EXISTS dmos_approval_queue (
    id SERIAL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL UNIQUE REFERENCES dmos_approvals(id),
    tenant_id VARCHAR(64) NOT NULL,
    queue_position INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sla_deadline TIMESTAMP WITH TIME ZONE,
    assigned_to VARCHAR(128),
    escalation_level INTEGER NOT NULL DEFAULT 0,
    escalated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_approval_queue_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'ESCALATED')),
    CONSTRAINT chk_approval_queue_escalation CHECK (escalation_level >= 0)
);

-- Indexes for queue operations
CREATE INDEX idx_approval_queue_tenant ON dmos_approval_queue(tenant_id, status, queue_position);
CREATE INDEX idx_approval_queue_position ON dmos_approval_queue(queue_position) WHERE status = 'PENDING';
CREATE INDEX idx_approval_queue_sla ON dmos_approval_queue(sla_deadline) WHERE status = 'PENDING';
CREATE INDEX idx_approval_queue_assigned ON dmos_approval_queue(assigned_to, status);

-- Approval Delegations Table
CREATE TABLE IF NOT EXISTS dmos_approval_delegations (
    id SERIAL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL REFERENCES dmos_approvals(id),
    from_user_id VARCHAR(128) NOT NULL,
    to_user_id VARCHAR(128) NOT NULL,
    reason TEXT,
    delegated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_delegation_not_self CHECK (from_user_id != to_user_id)
);

CREATE INDEX idx_approval_delegations_approval ON dmos_approval_delegations(approval_id, active);
CREATE INDEX idx_approval_delegations_to_user ON dmos_approval_delegations(to_user_id, active);

-- Queue Audit Log (for tracking queue movements)
CREATE TABLE IF NOT EXISTS dmos_approval_queue_audit (
    id SERIAL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    old_position INTEGER,
    new_position INTEGER,
    old_status VARCHAR(32),
    new_status VARCHAR(32),
    performed_by VARCHAR(128),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_queue_audit_approval ON dmos_approval_queue_audit(approval_id, created_at);
CREATE INDEX idx_queue_audit_tenant ON dmos_approval_queue_audit(tenant_id, created_at);

-- SLA Breach Tracking
CREATE TABLE IF NOT EXISTS dmos_approval_sla_breaches (
    id SERIAL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL REFERENCES dmos_approvals(id),
    tenant_id VARCHAR(64) NOT NULL,
    scheduled_sla TIMESTAMP WITH TIME ZONE NOT NULL,
    breached_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    hours_overdue INTEGER NOT NULL,
    escalation_level INTEGER NOT NULL DEFAULT 1,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_action VARCHAR(32)
);

CREATE INDEX idx_sla_breaches_approval ON dmos_approval_sla_breaches(approval_id);
CREATE INDEX idx_sla_breaches_active ON dmos_approval_sla_breaches(tenant_id, resolved_at) WHERE resolved_at IS NULL;

-- Function to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_approval_queue_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER approval_queue_updated_at
    BEFORE UPDATE ON dmos_approval_queue
    FOR EACH ROW
    EXECUTE FUNCTION update_approval_queue_updated_at();

-- Documentation
COMMENT ON TABLE dmos_approval_queue IS 'P1-012: Canonical approval queue for governance';
COMMENT ON TABLE dmos_approval_delegations IS 'P1-012: Approval delegation tracking';
COMMENT ON TABLE dmos_approval_queue_audit IS 'P1-013: Queue movement audit trail';
COMMENT ON TABLE dmos_approval_sla_breaches IS 'P1-013: SLA breach tracking for escalations';
