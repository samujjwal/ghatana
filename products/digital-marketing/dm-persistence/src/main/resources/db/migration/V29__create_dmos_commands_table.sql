-- P1-023: Create DMOS command store table for durable workflow execution
--
-- This table implements the outbox pattern for reliable external platform writes.
-- Commands are written transactionally with domain changes, then executed asynchronously.
-- Supports idempotent execution, retry with backoff, dead-letter queue, and compensation.

CREATE TABLE IF NOT EXISTS dmos_commands (
    id VARCHAR(64) PRIMARY KEY,
    command_type VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    issued_by VARCHAR(128) NOT NULL,
    serialized_payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    idempotency_key VARCHAR(256),           -- for idempotent command issuance
    parent_command_id VARCHAR(64),          -- for rollback/compensation chain
    workflow_id VARCHAR(64),                -- groups related commands

    -- Status check constraint
    CONSTRAINT chk_command_status CHECK (status IN ('PENDING', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'ROLLED_BACK')),

    -- Attempt count must be non-negative
    CONSTRAINT chk_command_attempts CHECK (attempt_count >= 0)
);

-- Index for pending commands (outbox worker queries)
CREATE INDEX idx_commands_pending ON dmos_commands(tenant_id, scheduled_at, status)
    WHERE status IN ('PENDING', 'FAILED');

-- Index for type/status queries
CREATE INDEX idx_commands_type_status ON dmos_commands(tenant_id, command_type, status);

-- Index for correlation tracking
CREATE INDEX idx_commands_correlation ON dmos_commands(correlation_id);

-- Index for workflow lookups
CREATE INDEX idx_commands_workflow ON dmos_commands(workflow_id, status);

-- Index for parent command chain
CREATE INDEX idx_commands_parent ON dmos_commands(parent_command_id) WHERE parent_command_id IS NOT NULL;

-- Unique index for idempotency (prevents duplicate commands)
CREATE UNIQUE INDEX idx_commands_idempotent ON dmos_commands(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Documentation
COMMENT ON TABLE dmos_commands IS 'P1-023: Durable command store for outbox pattern workflow execution';
COMMENT ON COLUMN dmos_commands.command_type IS 'Command type from DmCommandType enum';
COMMENT ON COLUMN dmos_commands.serialized_payload IS 'JSON-serialized command payload';
COMMENT ON COLUMN dmos_commands.status IS 'Lifecycle: PENDING -> EXECUTING -> SUCCEEDED|FAILED -> ROLLED_BACK';
COMMENT ON COLUMN dmos_commands.attempt_count IS 'Number of execution attempts (max 3 before DLQ)';
COMMENT ON COLUMN dmos_commands.idempotency_key IS 'Client-provided key for idempotent command issuance';
COMMENT ON COLUMN dmos_commands.parent_command_id IS 'References original command for rollback/compensation';
COMMENT ON COLUMN dmos_commands.workflow_id IS 'Groups related commands in a workflow';
