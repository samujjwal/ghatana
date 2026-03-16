-- D-02 EMS schema
-- Tables: routed_orders, execution_fills, split_orders, adapter_health_log

CREATE TABLE IF NOT EXISTS routed_orders (
    routing_id          TEXT        PRIMARY KEY,
    parent_order_id     TEXT        NOT NULL,
    client_id           TEXT        NOT NULL,
    instrument_id       TEXT        NOT NULL,
    exchange            TEXT        NOT NULL,
    side                TEXT        NOT NULL,
    quantity            BIGINT      NOT NULL,
    limit_price         NUMERIC(18,6),
    order_type          TEXT        NOT NULL,
    time_in_force       TEXT        NOT NULL DEFAULT 'DAY',
    status              TEXT        NOT NULL DEFAULT 'PENDING_ROUTE',
    filled_quantity     BIGINT      NOT NULL DEFAULT 0,
    avg_fill_price      NUMERIC(18,6),
    external_order_id   TEXT,
    routed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_routed_orders_parent  ON routed_orders(parent_order_id);
CREATE INDEX IF NOT EXISTS idx_routed_orders_client  ON routed_orders(client_id);
CREATE INDEX IF NOT EXISTS idx_routed_orders_status  ON routed_orders(status) WHERE status NOT IN ('FILLED','CANCELLED','REJECTED');
CREATE INDEX IF NOT EXISTS idx_routed_orders_updated ON routed_orders(updated_at DESC);

-- ─── Execution Fills ──────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS execution_fills (
    fill_id             TEXT        PRIMARY KEY,
    routing_id          TEXT        NOT NULL REFERENCES routed_orders(routing_id),
    exec_id             TEXT        NOT NULL,
    filled_quantity     BIGINT      NOT NULL,
    fill_price          NUMERIC(18,6) NOT NULL,
    exchange            TEXT        NOT NULL,
    filled_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exec_id UNIQUE (exec_id)
);

CREATE INDEX IF NOT EXISTS idx_fills_routing_id ON execution_fills(routing_id);
CREATE INDEX IF NOT EXISTS idx_fills_filled_at  ON execution_fills(filled_at DESC);

-- ─── Split Orders ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS split_orders (
    parent_order_id         TEXT        PRIMARY KEY,
    client_id               TEXT        NOT NULL,
    instrument_id           TEXT        NOT NULL,
    side                    TEXT        NOT NULL,
    total_quantity          BIGINT      NOT NULL,
    limit_price             NUMERIC(18,6),
    child_routing_ids       JSONB       NOT NULL DEFAULT '[]',
    total_filled_quantity   BIGINT      NOT NULL DEFAULT 0,
    avg_fill_price          NUMERIC(18,6),
    aggregate_status        TEXT        NOT NULL DEFAULT 'PENDING_ROUTE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_split_orders_client ON split_orders(client_id);
CREATE INDEX IF NOT EXISTS idx_split_orders_status ON split_orders(aggregate_status) WHERE aggregate_status NOT IN ('FILLED','CANCELLED');

-- ─── Adapter Health Log ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS adapter_health_log (
    id              BIGSERIAL   PRIMARY KEY,
    exchange_id     TEXT        NOT NULL,
    event_type      TEXT        NOT NULL,   -- CONNECTED, DISCONNECTED, HIGH_LATENCY, REJECTION_SPIKE
    detail          TEXT,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_adapter_health_exchange  ON adapter_health_log(exchange_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_adapter_health_event     ON adapter_health_log(event_type, recorded_at DESC);
