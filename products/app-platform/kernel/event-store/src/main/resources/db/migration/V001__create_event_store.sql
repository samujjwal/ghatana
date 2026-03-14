-- Flyway V001: Aggregate Event Store — bootstrap schema
--
-- Append-only DDD aggregate event store with per-aggregate sequence numbering
-- for optimistic concurrency and dual-calendar timestamp enrichment.
--
-- Distinct from :products:data-cloud events table (stream-based) and
-- :products:aep audit_trail (no aggregate ordering).
-- This table is purpose-built for DDD aggregate event sourcing.

CREATE TABLE IF NOT EXISTS event_store (
    event_id          UUID            NOT NULL,
    event_type        VARCHAR(255)    NOT NULL,
    aggregate_id      UUID            NOT NULL,
    aggregate_type    VARCHAR(100)    NOT NULL,
    sequence_number   BIGINT          NOT NULL,
    data              JSONB           NOT NULL,
    metadata          JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at_utc    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    -- populated by the calendar-service kernel once wired;
    -- NULL is valid in degradation mode
    created_at_bs     VARCHAR(10),

    CONSTRAINT pk_event_store PRIMARY KEY (event_id),
    -- Enforces strict per-aggregate ordering and detects concurrent write conflicts
    CONSTRAINT uq_event_store_aggregate_seq UNIQUE (aggregate_id, sequence_number)
);

-- Aggregate stream reads: reconstruct aggregate state from sequence
CREATE INDEX idx_event_store_aggregate
    ON event_store (aggregate_id, sequence_number ASC);

-- Event-type scans: projections, read-models, reporting
CREATE INDEX idx_event_store_type_utc
    ON event_store (event_type, created_at_utc DESC);

-- Time-range scans and audit
CREATE INDEX idx_event_store_created_utc
    ON event_store (created_at_utc DESC);

-- Metadata keys (e.g. tenant_id, correlation_id, source_service)
CREATE INDEX idx_event_store_metadata
    ON event_store USING GIN (metadata);

COMMENT ON TABLE event_store IS
    'Append-only DDD aggregate event store. '
    'Each row is an immutable domain event scoped to one aggregate instance. '
    'UPDATE and DELETE are revoked at the database level (see V002).';

COMMENT ON COLUMN event_store.event_id IS
    'UUID generated server-side; primary key and globally unique event identifier.';
COMMENT ON COLUMN event_store.aggregate_id IS
    'UUID identifying the aggregate instance (e.g. order ID, account ID).';
COMMENT ON COLUMN event_store.sequence_number IS
    'Monotonically increasing per-aggregate counter. Used for optimistic concurrency.';
COMMENT ON COLUMN event_store.data IS
    'Event payload as JSONB.';
COMMENT ON COLUMN event_store.metadata IS
    'Envelope metadata: tenant_id, user_id, correlation_id, causation_id, source_service.';
COMMENT ON COLUMN event_store.created_at_bs IS
    'Bikram Sambat (BS) calendar date string (YYYY-MM-DD format). '
    'Null until the calendar-service kernel is wired (degradation mode).';
