-- P0-006: Dead-letter queue table for permanently failed commands
-- Commands that exceed MAX_ATTEMPTS are moved here for manual inspection and replay

CREATE TABLE IF NOT EXISTS dm_command_dlq (
    id VARCHAR(36) PRIMARY KEY,
    original_command_id VARCHAR(36) NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    workspace_id VARCHAR(100) NOT NULL,
    serialized_payload TEXT NOT NULL,
    failure_reason TEXT NOT NULL,
    attempt_count INT NOT NULL,
    moved_to_dlq_at TIMESTAMP WITH TIME ZONE NOT NULL,
    original_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    UNIQUE (original_command_id)
);

CREATE INDEX IF NOT EXISTS idx_dm_command_dlq_tenant ON dm_command_dlq(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dm_command_dlq_workspace ON dm_command_dlq(workspace_id);
CREATE INDEX IF NOT EXISTS idx_dm_command_dlq_moved_at ON dm_command_dlq(moved_to_dlq_at DESC);

COMMENT ON TABLE dm_command_dlq IS 'Dead-letter queue for permanently failed DMOS commands (P0-006)';
COMMENT ON COLUMN dm_command_dlq.original_command_id IS 'Original command ID that failed';
COMMENT ON COLUMN dm_command_dlq.failure_reason IS 'Final failure reason explaining why command moved to DLQ';
