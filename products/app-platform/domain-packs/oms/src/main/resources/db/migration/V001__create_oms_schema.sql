-- V001__create_oms_schema.sql
-- D-01 Order Management System — Core Schema
-- Covers D01-001 through D01-018 primary persistence needs

-- ─── Orders ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    order_id           TEXT        PRIMARY KEY,
    client_id          TEXT        NOT NULL,
    account_id         TEXT        NOT NULL,
    instrument_id      TEXT        NOT NULL,
    side               TEXT        NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type         TEXT        NOT NULL CHECK (order_type IN ('MARKET','LIMIT','STOP','STOP_LIMIT')),
    time_in_force      TEXT        NOT NULL CHECK (time_in_force IN ('DAY','GTC','IOC','FOK')),
    quantity           NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    price              NUMERIC(18,6),
    stop_price         NUMERIC(18,6),
    status             TEXT        NOT NULL DEFAULT 'PENDING',
    idempotency_key    TEXT        NOT NULL UNIQUE,

    -- Enrichment (from D-11 instrument lookup)
    instrument_symbol  TEXT,
    exchange           TEXT,
    currency           TEXT,
    order_value        NUMERIC(22,6),
    arrival_price      NUMERIC(18,6),

    -- Fill tracking
    filled_quantity    NUMERIC(18,6) NOT NULL DEFAULT 0,
    remaining_quantity NUMERIC(18,6) NOT NULL,
    avg_fill_price     NUMERIC(18,6),

    -- Dual calendar timestamps
    created_at         TIMESTAMPTZ  NOT NULL,
    created_at_bs      TEXT,           -- Bikram Sambat date from K-15
    updated_at         TIMESTAMPTZ  NOT NULL,

    -- Trail
    rejection_reason   TEXT,
    routing_id         TEXT
);

CREATE INDEX IF NOT EXISTS idx_orders_client_id        ON orders (client_id);
CREATE INDEX IF NOT EXISTS idx_orders_status           ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_instrument_id    ON orders (instrument_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at       ON orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_client_status    ON orders (client_id, status);

-- ─── Order Fills ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_fills (
    fill_id     TEXT        PRIMARY KEY,
    order_id    TEXT        NOT NULL REFERENCES orders(order_id),
    exec_id     TEXT        NOT NULL,     -- Exchange execution ID (idempotency)
    fill_qty    NUMERIC(18,6) NOT NULL CHECK (fill_qty > 0),
    fill_price  NUMERIC(18,6) NOT NULL CHECK (fill_price > 0),
    fees        NUMERIC(18,6) NOT NULL DEFAULT 0,
    filled_at   TIMESTAMPTZ  NOT NULL,
    UNIQUE (order_id, exec_id)  -- idempotency per order+exec pair
);

CREATE INDEX IF NOT EXISTS idx_fills_order_id ON order_fills (order_id);

-- ─── Order Amendments ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_amendments (
    amendment_id           TEXT        PRIMARY KEY,
    order_id               TEXT        NOT NULL REFERENCES orders(order_id),
    old_price              NUMERIC(18,6),
    new_price              NUMERIC(18,6),
    old_quantity           NUMERIC(18,6),
    new_quantity           NUMERIC(18,6),
    old_time_in_force      TEXT,
    new_time_in_force      TEXT,
    requested_by           TEXT        NOT NULL,
    requested_at           TIMESTAMPTZ  NOT NULL,
    requires_maker_checker BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_amendments_order_id ON order_amendments (order_id);

-- ─── Approval Records ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_approvals (
    decision_id    TEXT        PRIMARY KEY,
    order_id       TEXT        NOT NULL REFERENCES orders(order_id),
    decision       TEXT        NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    approver_id    TEXT        NOT NULL,
    notes          TEXT,
    decided_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_approvals_order_id   ON order_approvals (order_id);
CREATE INDEX IF NOT EXISTS idx_approvals_approver   ON order_approvals (approver_id);

-- ─── Position Read Model (CQRS) ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS positions (
    client_id      TEXT        NOT NULL,
    instrument_id  TEXT        NOT NULL,
    account_id     TEXT        NOT NULL,
    quantity       NUMERIC(18,6) NOT NULL DEFAULT 0,
    avg_cost       NUMERIC(18,6) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(22,6) NOT NULL DEFAULT 0,
    realized_pnl   NUMERIC(22,6) NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (client_id, instrument_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_positions_client ON positions (client_id);
