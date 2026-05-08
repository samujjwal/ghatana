-- DMOS durable event log tables for campaign event sourcing
-- Schema migration V31: create tenant-scoped append-only event log

CREATE TABLE IF NOT EXISTS dmos_event_log_offsets (
    tenant_id   TEXT    PRIMARY KEY,
    next_offset BIGINT  NOT NULL CHECK (next_offset >= 1)
);

CREATE TABLE IF NOT EXISTS dmos_event_log (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       TEXT         NOT NULL,
    tenant_offset   BIGINT       NOT NULL,
    event_id        UUID         NOT NULL,
    event_type      TEXT         NOT NULL,
    event_version   TEXT         NOT NULL,
    event_ts        TIMESTAMPTZ  NOT NULL,
    payload         BYTEA        NOT NULL,
    content_type    TEXT         NOT NULL,
    headers_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT dmos_event_log_tenant_offset_unique UNIQUE (tenant_id, tenant_offset),
    CONSTRAINT dmos_event_log_tenant_event_unique UNIQUE (tenant_id, event_id)
);

CREATE INDEX IF NOT EXISTS dmos_event_log_tenant_offset_idx
    ON dmos_event_log (tenant_id, tenant_offset);

CREATE INDEX IF NOT EXISTS dmos_event_log_tenant_type_offset_idx
    ON dmos_event_log (tenant_id, event_type, tenant_offset);

CREATE INDEX IF NOT EXISTS dmos_event_log_tenant_time_idx
    ON dmos_event_log (tenant_id, event_ts DESC);
