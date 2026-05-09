-- Lifecycle executions table for durable persistence with full traceability
-- Replaces fire-and-forget HTTP persistence with transactional database storage
CREATE TABLE IF NOT EXISTS lifecycle_executions (
    execution_id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(511) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_duration_ms BIGINT NOT NULL,
    executed_phases JSONB NOT NULL,
    phase_durations_ms JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    intent_result JSONB,
    shape_result JSONB,
    validation_result JSONB,
    generation_result JSONB,
    run_result JSONB,
    observation_result JSONB,
    learning_result JSONB,
    evolution_result JSONB,
    metadata JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_tenant_project ON lifecycle_executions(tenant_id, project_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_workspace ON lifecycle_executions(workspace_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_actor ON lifecycle_executions(actor_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_correlation ON lifecycle_executions(correlation_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_idempotency ON lifecycle_executions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_status ON lifecycle_executions(status);
CREATE INDEX IF NOT EXISTS idx_lifecycle_executions_completed_at ON lifecycle_executions(completed_at DESC);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_lifecycle_executions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_lifecycle_executions_updated_at
    BEFORE UPDATE ON lifecycle_executions
    FOR EACH ROW
    EXECUTE FUNCTION update_lifecycle_executions_updated_at();
