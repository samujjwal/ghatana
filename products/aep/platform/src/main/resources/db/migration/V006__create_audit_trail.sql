-- Flyway V006: Create audit trail table for production observability
-- Replaces in-memory ConcurrentHashMap audit stores with persistent storage

CREATE TABLE IF NOT EXISTS audit_trail (
    id                BIGSERIAL       NOT NULL,
    tenant_id         VARCHAR(100)    NOT NULL,
    event_type        VARCHAR(100)    NOT NULL,
    entity_type       VARCHAR(100)    NOT NULL,
    entity_id         VARCHAR(255)    NOT NULL,
    action            VARCHAR(50)     NOT NULL,
    actor             VARCHAR(255),
    actor_type        VARCHAR(50),
    timestamp         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    details           JSONB           DEFAULT '{}'::jsonb,
    previous_state    JSONB,
    new_state         JSONB,
    correlation_id    VARCHAR(255),
    source            VARCHAR(255),
    ip_address        VARCHAR(45),
    success           BOOLEAN         NOT NULL DEFAULT TRUE,
    error_message     TEXT,

    CONSTRAINT pk_audit_trail PRIMARY KEY (id),
    CONSTRAINT chk_audit_action CHECK (action IN (
        'CREATE', 'READ', 'UPDATE', 'DELETE',
        'EXECUTE', 'ACTIVATE', 'DEACTIVATE',
        'DEPLOY', 'UNDEPLOY', 'CONFIGURE',
        'LOGIN', 'LOGOUT', 'GRANT', 'REVOKE'
    )),
    CONSTRAINT chk_audit_entity_type CHECK (entity_type IN (
        'PIPELINE', 'AGENT', 'PATTERN', 'COLLECTION',
        'EVENT', 'ENTITY', 'CONFIG', 'USER', 'ROLE', 'POLICY'
    ))
);

-- Query patterns: recent activity, entity history, actor audit
CREATE INDEX idx_audit_trail_tenant_time ON audit_trail (tenant_id, timestamp DESC);
CREATE INDEX idx_audit_trail_entity ON audit_trail (tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_trail_actor ON audit_trail (tenant_id, actor);
CREATE INDEX idx_audit_trail_action ON audit_trail (tenant_id, action);
CREATE INDEX idx_audit_trail_correlation ON audit_trail (correlation_id);
CREATE INDEX idx_audit_trail_event_type ON audit_trail (tenant_id, event_type);

-- Partial index: only failures for alerting/investigation
CREATE INDEX idx_audit_trail_failures
    ON audit_trail (tenant_id, timestamp DESC)
    WHERE success = FALSE;

-- Time-based partitioning hint (for production: convert to partitioned table)
-- For now, use BRIN index for time-range scans on large tables
CREATE INDEX idx_audit_trail_timestamp_brin
    ON audit_trail USING BRIN (timestamp);

-- Documentation
COMMENT ON TABLE audit_trail IS 'Persistent audit trail for compliance and observability. Replaces in-memory ConcurrentHashMap audit stores. Append-only — no UPDATE or DELETE in application layer.';
COMMENT ON COLUMN audit_trail.event_type IS 'Audit event classification (e.g., pipeline.executed, agent.registered, pattern.activated)';
COMMENT ON COLUMN audit_trail.entity_type IS 'Type of entity being audited (PIPELINE, AGENT, PATTERN, etc.)';
COMMENT ON COLUMN audit_trail.entity_id IS 'Identifier of the audited entity';
COMMENT ON COLUMN audit_trail.previous_state IS 'Snapshot of entity state before the action (for UPDATE/DELETE)';
COMMENT ON COLUMN audit_trail.new_state IS 'Snapshot of entity state after the action (for CREATE/UPDATE)';
COMMENT ON COLUMN audit_trail.correlation_id IS 'Distributed tracing correlation for cross-service audit correlation';
COMMENT ON COLUMN audit_trail.source IS 'Originating service or component name';
