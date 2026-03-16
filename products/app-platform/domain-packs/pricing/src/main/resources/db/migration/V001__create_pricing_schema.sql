-- ============================================================================
-- D05 Pricing Engine Schema
-- Stores price history, yield curves, and price validation log.
-- ============================================================================

-- ─── Price History (partitioned by date for TimescaleDB-style range queries) ─
CREATE TABLE IF NOT EXISTS price_history (
    price_id            TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       TEXT            NOT NULL,
    price_date_ad       DATE            NOT NULL,
    price_date_bs       TEXT            NOT NULL,
    open_price          NUMERIC(20, 4),
    high_price          NUMERIC(20, 4),
    low_price           NUMERIC(20, 4),
    close_price         NUMERIC(20, 4)  NOT NULL,
    vwap_price          NUMERIC(20, 4),
    last_trade_price    NUMERIC(20, 4),
    bid_price           NUMERIC(20, 4),
    ask_price           NUMERIC(20, 4),
    volume              BIGINT          NOT NULL DEFAULT 0,
    source              TEXT            NOT NULL DEFAULT 'EXCHANGE',  -- EXCHANGE | VWAP | LAST | PREV
    is_official_close   BOOLEAN         NOT NULL DEFAULT false,
    captured_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (instrument_id, price_date_ad)
);

CREATE INDEX IF NOT EXISTS idx_price_history_instrument_date
    ON price_history (instrument_id, price_date_ad DESC);

-- ─── Real-time Price Cache (latest tick per instrument, also in Redis) ────────
CREATE TABLE IF NOT EXISTS latest_prices (
    instrument_id       TEXT            NOT NULL PRIMARY KEY,
    bid_price           NUMERIC(20, 4),
    ask_price           NUMERIC(20, 4),
    mid_price           NUMERIC(20, 4),
    last_price          NUMERIC(20, 4)  NOT NULL,
    volume              BIGINT          NOT NULL DEFAULT 0,
    source              TEXT            NOT NULL DEFAULT 'MARKET_DATA',
    price_status        TEXT            NOT NULL DEFAULT 'LIVE',  -- LIVE | STALE | HALTED
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─── Yield Curves ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS yield_curves (
    curve_id            TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    currency            TEXT            NOT NULL DEFAULT 'NPR',
    curve_date_ad       DATE            NOT NULL,
    curve_date_bs       TEXT            NOT NULL,
    curve_type          TEXT            NOT NULL DEFAULT 'RISK_FREE',  -- RISK_FREE | CREDIT | SWAP
    tenors_json         TEXT            NOT NULL,   -- JSON array of {tenor_days, zero_rate}
    interpolation       TEXT            NOT NULL DEFAULT 'LINEAR',  -- LINEAR | LOG_LINEAR | CUBIC
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (currency, curve_type, curve_date_ad)
);

CREATE INDEX IF NOT EXISTS idx_yield_curves_date
    ON yield_curves (currency, curve_date_ad DESC);

-- ─── Price Validation Log ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_validation_log (
    log_id              TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       TEXT            NOT NULL,
    raw_bid             NUMERIC(20, 4),
    raw_ask             NUMERIC(20, 4),
    raw_last            NUMERIC(20, 4),
    rejection_reason    TEXT            NOT NULL,
    source              TEXT,
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_price_validation_log_instrument
    ON price_validation_log (instrument_id, received_at DESC);
