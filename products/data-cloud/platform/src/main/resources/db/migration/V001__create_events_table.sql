-- Flyway V001: Create events table for EventRecord
-- Maps to: com.ghatana.datacloud.EventRecord (@Entity, table = "events")
-- Parent: com.ghatana.datacloud.DataRecord (@MappedSuperclass)

CREATE TABLE IF NOT EXISTS events (
    -- DataRecord base fields
    id                UUID            NOT NULL,
    tenant_id         VARCHAR(255)    NOT NULL,
    collection_name   VARCHAR(255)    NOT NULL,
    record_type       VARCHAR(50)     NOT NULL,
    data              JSONB           DEFAULT '{}'::jsonb,
    metadata          JSONB           DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),

    -- EventRecord-specific fields
    stream_name       VARCHAR(255)    NOT NULL,
    partition_id      INTEGER         NOT NULL DEFAULT 0,
    event_offset      BIGINT          NOT NULL,
    occurrence_time   TIMESTAMPTZ     NOT NULL,
    detection_time    TIMESTAMPTZ     NOT NULL,
    idempotency_key   VARCHAR(255),
    correlation_id    VARCHAR(255),
    causation_id      VARCHAR(255),

    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT chk_events_record_type CHECK (record_type IN ('ENTITY', 'EVENT', 'TIMESERIES', 'DOCUMENT', 'GRAPH')),
    CONSTRAINT uk_events_offset UNIQUE (tenant_id, stream_name, partition_id, event_offset),
    CONSTRAINT uk_events_idempotency UNIQUE (tenant_id, idempotency_key)
);

-- Performance indexes
CREATE INDEX idx_events_tenant ON events (tenant_id);
CREATE INDEX idx_events_stream ON events (tenant_id, stream_name);
CREATE INDEX idx_events_partition_offset ON events (tenant_id, stream_name, partition_id, event_offset);
CREATE INDEX idx_events_detection_time ON events (tenant_id, stream_name, detection_time DESC);
CREATE INDEX idx_events_correlation ON events (tenant_id, correlation_id);

-- Documentation
COMMENT ON TABLE events IS 'Event sourcing store for EventRecord. Immutable append-only event stream with partition-based ordering.';
COMMENT ON COLUMN events.id IS 'UUID primary key, auto-generated in @PrePersist if null';
COMMENT ON COLUMN events.tenant_id IS 'Tenant isolation identifier. Not updatable after creation.';
COMMENT ON COLUMN events.stream_name IS 'Logical event stream name for partitioned consumption';
COMMENT ON COLUMN events.event_offset IS 'Sequential offset within (tenant, stream, partition). Assigned by storage layer.';
COMMENT ON COLUMN events.occurrence_time IS 'When the event actually occurred in the domain';
COMMENT ON COLUMN events.detection_time IS 'When the event was recorded/detected by the system';
COMMENT ON COLUMN events.idempotency_key IS 'Optional deduplication key. Unique per tenant when present.';
COMMENT ON COLUMN events.correlation_id IS 'Distributed tracing correlation identifier';
COMMENT ON COLUMN events.causation_id IS 'ID of the event that caused this event (causal chain)';
COMMENT ON COLUMN events.data IS 'Event payload as JSONB';
COMMENT ON COLUMN events.metadata IS 'Extensible metadata as JSONB';
