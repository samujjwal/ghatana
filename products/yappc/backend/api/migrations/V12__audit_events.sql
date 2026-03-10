-- V12: Durable audit event storage
-- Replaces the in-memory InMemoryAuditQueryService used in production.
-- All writes go through JdbcAuditService wrapped in Promise.ofBlocking.

CREATE TABLE IF NOT EXISTS audit_events (
    id            TEXT        NOT NULL,
    tenant_id     TEXT        NOT NULL,
    event_type    TEXT        NOT NULL,
    principal     TEXT,
    resource_type TEXT,
    resource_id   TEXT,
    success       BOOLEAN,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    details       JSONB       NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

-- Tenant-scoped queries are the hot path
CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_time
    ON audit_events (tenant_id, occurred_at DESC);

-- Resource-level drill-down
CREATE INDEX IF NOT EXISTS idx_audit_events_resource
    ON audit_events (tenant_id, resource_type, resource_id);

-- Principal-based queries (compliance reports)
CREATE INDEX IF NOT EXISTS idx_audit_events_principal
    ON audit_events (tenant_id, principal);

-- Event-type filtering
CREATE INDEX IF NOT EXISTS idx_audit_events_type
    ON audit_events (tenant_id, event_type);

-- 90-day automatic retention via PostgreSQL partition pruning (optional future step).
-- For now, a manual CRON can do:
--   DELETE FROM audit_events WHERE occurred_at < now() - INTERVAL '90 days';

COMMENT ON TABLE audit_events IS
    'Durable audit trail. Replaces InMemoryAuditQueryService (Phase 0.3).';
