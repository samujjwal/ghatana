-- P1-050: Create migration audit table
--
-- This table tracks migration execution history and deployment metrics

CREATE TABLE IF NOT EXISTS dmos_migration_audit (
    id SERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    version VARCHAR(32),
    description TEXT,
    current_version VARCHAR(32),
    pending_count INTEGER,
    success BOOLEAN,
    duration_ms BIGINT,
    error_message TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_migration_audit_event CHECK (event_type IN (
        'MIGRATION_EXECUTED',
        'STARTUP_CHECK',
        'DRIFT_DETECTED',
        'HEALTH_CHECK'
    ))
);

CREATE INDEX idx_migration_audit_version ON dmos_migration_audit(version, recorded_at);
CREATE INDEX idx_migration_audit_event ON dmos_migration_audit(event_type, recorded_at);
CREATE INDEX idx_migration_audit_recorded ON dmos_migration_audit(recorded_at DESC);

COMMENT ON TABLE dmos_migration_audit IS 'P1-050: Migration execution audit trail for deployment metrics';
