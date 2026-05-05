-- P1-025: Create compensation log table for workflow rollback tracking
--
-- This table stores compensating actions for failed workflows.
-- It enables eventual consistency through idempotent compensation.

CREATE TABLE IF NOT EXISTS dmos_compensation_log (
    id VARCHAR(64) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    failed_step VARCHAR(256) NOT NULL,      -- which step caused the failure
    compensation_type VARCHAR(64) NOT NULL, -- type of compensation
    payload TEXT,                          -- JSON data needed for compensation
    status VARCHAR(32) NOT NULL,           -- PENDING, COMPLETED, FAILED
    correlation_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    last_retry_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,

    -- Valid status values
    CONSTRAINT chk_compensation_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    -- Valid compensation types
    CONSTRAINT chk_compensation_type CHECK (
        compensation_type IN (
            'CAMPAIGN_ROLLBACK',
            'GOOGLE_ADS_CLEANUP',
            'BUDGET_RESTORE',
            'STRATEGY_INVALIDATE',
            'AUDIT_RECORD'
        )
    )
);

-- Index for pending compensations (compensation processor queries)
CREATE INDEX idx_compensation_pending ON dmos_compensation_log(status, created_at)
    WHERE status = 'PENDING';

-- Index for workflow lookups
CREATE INDEX idx_compensation_workflow ON dmos_compensation_log(workflow_id, status);

-- Index for tenant-scoped queries
CREATE INDEX idx_compensation_tenant ON dmos_compensation_log(tenant_id, created_at);

-- Index for correlation tracking
CREATE INDEX idx_compensation_correlation ON dmos_compensation_log(correlation_id);

-- Index for retry scheduling
CREATE INDEX idx_compensation_retry ON dmos_compensation_log(status, retry_count, last_retry_at)
    WHERE status = 'PENDING';

-- Documentation
COMMENT ON TABLE dmos_compensation_log IS 'P1-025: Compensation actions for workflow failure recovery';
COMMENT ON COLUMN dmos_compensation_log.workflow_id IS 'ID of the workflow that failed';
COMMENT ON COLUMN dmos_compensation_log.failed_step IS 'Name of the workflow step that caused failure';
COMMENT ON COLUMN dmos_compensation_log.compensation_type IS 'Type: CAMPAIGN_ROLLBACK, GOOGLE_ADS_CLEANUP, BUDGET_RESTORE, STRATEGY_INVALIDATE, AUDIT_RECORD';
COMMENT ON COLUMN dmos_compensation_log.payload IS 'JSON data required to execute the compensation';
COMMENT ON COLUMN dmos_compensation_log.status IS 'PENDING (awaiting execution), COMPLETED (success), FAILED (max retries exceeded)';
COMMENT ON COLUMN dmos_compensation_log.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN dmos_compensation_log.max_retries IS 'Maximum allowed retry attempts';
