-- Flyway V005: Create event_log table for WarmTierEventLogStore (EventLogStore SPI)
-- Maps to: com.ghatana.datacloud.storage.WarmTierEventLogStore
-- Part of the WARM tier — durable, queryable PostgreSQL event log

CREATE TABLE IF NOT EXISTS event_log (
    offset_value     BIGINT          GENERATED ALWAYS AS IDENTITY,
    tenant_id        VARCHAR(255)    NOT NULL,
    event_id         UUID            NOT NULL,
    event_type       VARCHAR(255)    NOT NULL,
    event_version    VARCHAR(64)     NOT NULL DEFAULT '1.0.0',
    payload          BYTEA           NOT NULL,
    content_type     VARCHAR(128)    NOT NULL DEFAULT 'application/json',
    headers          JSONB           NOT NULL DEFAULT '{}',
    idempotency_key  VARCHAR(255),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_event_log        PRIMARY KEY (offset_value),
    CONSTRAINT uk_event_log_id     UNIQUE (tenant_id, event_id),
    CONSTRAINT uk_event_log_idem   UNIQUE (tenant_id, idempotency_key)
);

-- Tenant-scoped offset lookups (sequential scan within a tenant)
CREATE INDEX idx_event_log_tenant_offset
    ON event_log (tenant_id, offset_value);

-- Event-type filtering per tenant
CREATE INDEX idx_event_log_type
    ON event_log (tenant_id, event_type, offset_value);

-- Time-range queries
CREATE INDEX idx_event_log_created_at
    ON event_log (tenant_id, created_at DESC);

-- Documentation
COMMENT ON TABLE  event_log                  IS 'Append-only warm-tier event log. Implements EventLogStore SPI. Immutable — no UPDATE/DELETE.';
COMMENT ON COLUMN event_log.offset_value     IS 'Monotonically increasing primary offset assigned by PostgreSQL IDENTITY. Unique globally.';
COMMENT ON COLUMN event_log.tenant_id        IS 'Multi-tenancy isolation key. All queries scoped to a single tenant.';
COMMENT ON COLUMN event_log.event_id         IS 'Client-generated UUID for the event. Unique per tenant.';
COMMENT ON COLUMN event_log.event_type       IS 'Dot-notation event type name (e.g. commerce.order.created).';
COMMENT ON COLUMN event_log.event_version    IS 'Semantic version of the event schema.';
COMMENT ON COLUMN event_log.payload          IS 'Raw event payload bytes. Encoding dictated by content_type.';
COMMENT ON COLUMN event_log.content_type     IS 'MIME type of payload (default application/json).';
COMMENT ON COLUMN event_log.headers          IS 'Arbitrary string key/value headers stored as JSONB.';
COMMENT ON COLUMN event_log.idempotency_key  IS 'Optional client-provided deduplication key. Unique per tenant when set.';
COMMENT ON COLUMN event_log.created_at       IS 'Wall-clock time of ingestion by the server.';
