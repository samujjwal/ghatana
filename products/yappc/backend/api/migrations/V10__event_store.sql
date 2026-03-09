-- YAPPC Database Migration: V10 - Event Store
-- PostgreSQL 16
-- Adds event sourcing infrastructure for event-driven architecture

-- ========== Domain Events Table ==========
CREATE TABLE IF NOT EXISTS yappc.domain_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type      VARCHAR(128)  NOT NULL,
    aggregate_id    VARCHAR(255)  NOT NULL,
    aggregate_type  VARCHAR(128)  NOT NULL,
    tenant_id       VARCHAR(64)   NOT NULL,
    user_id         VARCHAR(128),
    sequence_num    BIGINT        NOT NULL,
    payload         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    metadata        JSONB         NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE,
    schema_version  INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT chk_event_type_nonempty CHECK (char_length(event_type) > 0),
    CONSTRAINT chk_aggregate_id_nonempty CHECK (char_length(aggregate_id) > 0)
);

-- Unique sequence per aggregate to enforce ordering
CREATE UNIQUE INDEX IF NOT EXISTS uidx_domain_events_aggregate_seq
    ON yappc.domain_events (tenant_id, aggregate_id, sequence_num);

CREATE INDEX IF NOT EXISTS idx_domain_events_tenant_type
    ON yappc.domain_events (tenant_id, event_type);

CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate
    ON yappc.domain_events (tenant_id, aggregate_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_domain_events_occurred
    ON yappc.domain_events (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_domain_events_unpublished
    ON yappc.domain_events (tenant_id, published_at)
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_domain_events_payload
    ON yappc.domain_events USING GIN (payload);

-- ========== Event Outbox Table ==========
-- Transactional outbox pattern: events written here atomically with domain writes,
-- then relayed to downstream consumers by a polling publisher.
CREATE TABLE IF NOT EXISTS yappc.event_outbox (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id        UUID          NOT NULL REFERENCES yappc.domain_events(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(128)  NOT NULL,
    aggregate_id    VARCHAR(255)  NOT NULL,
    payload         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER       NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP WITH TIME ZONE,
    next_retry_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_pending
    ON yappc.event_outbox (next_retry_at, attempts)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_event_outbox_tenant
    ON yappc.event_outbox (tenant_id, status);

-- ========== Event Snapshots Table ==========
-- Aggregate state snapshots to avoid replaying entire event history on load
CREATE TABLE IF NOT EXISTS yappc.event_snapshots (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id    VARCHAR(255)  NOT NULL,
    aggregate_type  VARCHAR(128)  NOT NULL,
    tenant_id       VARCHAR(64)   NOT NULL,
    sequence_num    BIGINT        NOT NULL,
    state           JSONB         NOT NULL,
    schema_version  INTEGER       NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_snapshot_aggregate_seq UNIQUE (tenant_id, aggregate_id, sequence_num)
);

CREATE INDEX IF NOT EXISTS idx_event_snapshots_latest
    ON yappc.event_snapshots (tenant_id, aggregate_id, sequence_num DESC);

-- ========== update_updated_at trigger (reuse existing function) ==========
CREATE TRIGGER trigger_event_outbox_updated_at
    BEFORE UPDATE ON yappc.event_outbox
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();
