-- V21: Persistent lifecycle audit event log
-- Used by JdbcAuditLogger in YAPPC lifecycle service to durably record
-- all phase-service operations (intent capture, shape, generate, run, etc.)
-- and agent turn completions. Separate from the API-level audit_events table
-- so the lifecycle service can be deployed independently.

CREATE TABLE IF NOT EXISTS lifecycle_audit_events (
    id          TEXT        NOT NULL PRIMARY KEY,
    tenant_id   TEXT        NOT NULL DEFAULT 'default',
    event_type  TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload     JSONB       NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_lac_tenant_occurred
    ON lifecycle_audit_events (tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_lac_event_type
    ON lifecycle_audit_events (event_type, tenant_id, occurred_at DESC);

-- GIN index on payload enables fast structured queries on audit event fields
CREATE INDEX IF NOT EXISTS idx_lac_payload_gin
    ON lifecycle_audit_events USING GIN (payload);

COMMENT ON TABLE lifecycle_audit_events IS
    'Durable audit log for YAPPC lifecycle service phase events. Written by JdbcAuditLogger.';
COMMENT ON COLUMN lifecycle_audit_events.id IS
    'UUID idempotency key (ON CONFLICT DO NOTHING prevents duplicate inserts).';
COMMENT ON COLUMN lifecycle_audit_events.tenant_id IS
    'Tenant isolation key; all queries MUST filter by this column.';
COMMENT ON COLUMN lifecycle_audit_events.event_type IS
    'Lifecycle event type (e.g. intent.captured, phase.advanced, validation.passed).';
COMMENT ON COLUMN lifecycle_audit_events.payload IS
    'Full event payload as JSONB; includes agent_id, project_id, correlation_id, etc.';
