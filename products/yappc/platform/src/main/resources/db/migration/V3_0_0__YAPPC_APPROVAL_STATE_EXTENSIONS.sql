-- YAPPC Lifecycle: Approval State Machine Enhancements
-- Migration: V3_0_0__YAPPC_APPROVAL_STATE_EXTENSIONS.sql
-- Adds REVIEWING status support to approval_requests and creates the state_transition_history table.

-- ─── Step 1: Extend the approval_requests status column to include REVIEWING ───
-- The status VARCHAR(32) column already supports arbitrary values, so no DDL change needed.
-- We add a CHECK constraint update to document the allowed values for correctness.

-- Remove old implicit constraint if any (PostgreSQL allows re-adding CHECK constraints)
ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS chk_approval_requests_status;

ALTER TABLE approval_requests
    ADD CONSTRAINT chk_approval_requests_status
    CHECK (status IN ('PENDING', 'REVIEWING', 'APPROVED', 'REJECTED', 'EXPIRED'));

-- ─── Step 2: State transition history table ───────────────────────────────────
-- Provides an immutable audit trail of every status change for each approval request.

CREATE TABLE IF NOT EXISTS approval_state_history (
    id                  BIGSERIAL       NOT NULL PRIMARY KEY,
    approval_request_id VARCHAR(255)    NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    tenant_id           VARCHAR(255)    NOT NULL,
    from_status         VARCHAR(32),    -- NULL when this is the initial PENDING creation
    to_status           VARCHAR(32)     NOT NULL,
    changed_by          VARCHAR(255),   -- user ID or 'system' for automated transitions
    changed_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    reason              TEXT            -- optional human-readable reason for the transition
);

-- Index for querying transition history for a specific request
CREATE INDEX IF NOT EXISTS idx_approval_state_history_request
    ON approval_state_history (approval_request_id, changed_at DESC);

-- Index for tenant-scoped audit queries
CREATE INDEX IF NOT EXISTS idx_approval_state_history_tenant
    ON approval_state_history (tenant_id, changed_at DESC);

-- Insert a history row for every existing approval_request to bootstrap the audit trail
-- (All existing rows are in their final state; mark them as-created with from_status=NULL)
INSERT INTO approval_state_history (approval_request_id, tenant_id, from_status, to_status, changed_by, changed_at)
SELECT id, tenant_id, NULL, status, COALESCE(decided_by, 'system'), COALESCE(decided_at, created_at)
FROM approval_requests
ON CONFLICT DO NOTHING;
