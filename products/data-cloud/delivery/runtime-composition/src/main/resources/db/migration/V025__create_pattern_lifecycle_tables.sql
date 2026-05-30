-- Create pattern lifecycle state table
CREATE TABLE IF NOT EXISTS pattern_lifecycle_states (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    pattern_id VARCHAR(255) NOT NULL,
    state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pattern_lifecycle UNIQUE (tenant_id, pattern_id)
);

-- Create index on tenant_id for tenant isolation queries
CREATE INDEX IF NOT EXISTS idx_pattern_lifecycle_states_tenant ON pattern_lifecycle_states(tenant_id);

-- Create index on state for querying patterns by state
CREATE INDEX IF NOT EXISTS idx_pattern_lifecycle_states_state ON pattern_lifecycle_states(state);

-- Create pattern lifecycle events table
CREATE TABLE IF NOT EXISTS pattern_lifecycle_events (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    pattern_id VARCHAR(255) NOT NULL,
    from_state VARCHAR(50) NOT NULL,
    to_state VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    evidence TEXT,
    trace_id VARCHAR(255),
    policy_decision VARCHAR(255),
    confidence DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on tenant_id for tenant isolation queries
CREATE INDEX IF NOT EXISTS idx_pattern_lifecycle_events_tenant ON pattern_lifecycle_events(tenant_id);

-- Create index on pattern_id for querying events by pattern
CREATE INDEX IF NOT EXISTS idx_pattern_lifecycle_events_pattern ON pattern_lifecycle_events(pattern_id);

-- Create index on occurred_at for time-based queries
CREATE INDEX IF NOT EXISTS idx_pattern_lifecycle_events_occurred_at ON pattern_lifecycle_events(occurred_at);

-- Create composite index for tenant + pattern queries
CREATE INDEX IF NOT EXISTS idx_pattern_lifecycle_events_tenant_pattern ON pattern_lifecycle_events(tenant_id, pattern_id);

-- Add RLS policies for tenant isolation
ALTER TABLE pattern_lifecycle_states ENABLE ROW LEVEL SECURITY;

CREATE POLICY pattern_lifecycle_states_tenant_isolation ON pattern_lifecycle_states
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

ALTER TABLE pattern_lifecycle_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY pattern_lifecycle_events_tenant_isolation ON pattern_lifecycle_events
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- Grant necessary permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON pattern_lifecycle_states TO data_cloud_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON pattern_lifecycle_events TO data_cloud_user;
GRANT USAGE, SELECT ON SEQUENCE pattern_lifecycle_states_id_seq TO data_cloud_user;
GRANT USAGE, SELECT ON SEQUENCE pattern_lifecycle_events_id_seq TO data_cloud_user;
