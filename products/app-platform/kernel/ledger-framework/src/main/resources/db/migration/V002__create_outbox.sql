-- =============================================================================
-- V002__create_outbox.sql
-- K17-001: Transactional outbox for distributed transaction coordination
-- K17-002: Monitoring columns and index for lag calculation
-- =============================================================================

CREATE TABLE outbox (
    id              UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID            NOT NULL,               -- e.g., journal_id, order_id
    aggregate_type  VARCHAR(100)    NOT NULL,               -- e.g., 'Journal', 'Order'
    event_type      VARCHAR(200)    NOT NULL,               -- e.g., 'JournalPosted'
    payload         JSONB           NOT NULL,
    tenant_id       UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMPTZ,
    publish_attempts SMALLINT       NOT NULL DEFAULT 0,
    last_error      TEXT            -- last relay error (for monitoring)
);

-- For the relay service: SELECT FOR UPDATE SKIP LOCKED on unpublished rows
CREATE INDEX idx_outbox_unpublished ON outbox(created_at)
    WHERE published = FALSE;

-- For cleanup: select old published rows by published_at
CREATE INDEX idx_outbox_published_at ON outbox(published_at)
    WHERE published = TRUE;

-- For monitoring lag: sort by created_at of oldest unpublished
CREATE INDEX idx_outbox_lag_monitor ON outbox(created_at)
    WHERE published = FALSE;
