-- ============================================================================
-- D08 Surveillance Schema
-- Stores wash trade alerts, spoofing alerts, and surveillance audit trail.
-- ============================================================================

-- ─── Wash Trade Alerts ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wash_trade_alerts (
    alert_id            TEXT        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           TEXT        NOT NULL,
    instrument_id       TEXT        NOT NULL,
    buy_order_id        TEXT        NOT NULL,
    sell_order_id       TEXT        NOT NULL,
    buy_fill_id         TEXT,
    sell_fill_id        TEXT,
    buy_price           NUMERIC(20, 4),
    sell_price          NUMERIC(20, 4),
    quantity            NUMERIC(20, 4),
    time_window_secs    INT         NOT NULL DEFAULT 300,  -- default 5-min window
    confidence_score    NUMERIC(4, 3) NOT NULL,            -- 0.0–1.0
    rule_violated       TEXT        NOT NULL,
    alert_status        TEXT        NOT NULL DEFAULT 'OPEN',  -- OPEN | UNDER_REVIEW | CONFIRMED | DISMISSED
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ,
    reviewed_by         TEXT
);

CREATE INDEX IF NOT EXISTS idx_wash_trade_alerts_client
    ON wash_trade_alerts (client_id, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_wash_trade_alerts_status
    ON wash_trade_alerts (alert_status) WHERE alert_status = 'OPEN';

-- ─── Ring Trade Patterns ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ring_trade_patterns (
    pattern_id          TEXT        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       TEXT        NOT NULL,
    participant_chain   TEXT        NOT NULL,   -- JSON: ["clientA","clientB","clientC","clientA"]
    party_count         INT         NOT NULL,
    total_volume        NUMERIC(20, 4),
    complexity_score    NUMERIC(4, 3),
    k03_rule_id         TEXT,
    pattern_status      TEXT        NOT NULL DEFAULT 'OPEN',
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ring_trade_instrument
    ON ring_trade_patterns (instrument_id, detected_at DESC);

-- ─── Spoofing Alerts ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS spoofing_alerts (
    alert_id            TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           TEXT            NOT NULL,
    instrument_id       TEXT            NOT NULL,
    spoofing_order_id   TEXT            NOT NULL,
    spoofing_side       TEXT            NOT NULL,  -- BUY | SELL
    spoofing_qty        NUMERIC(20, 4)  NOT NULL,
    price_move_bps      NUMERIC(10, 2),            -- basis points move after placement
    cancel_latency_ms   BIGINT,
    opposite_fill_id    TEXT,
    confidence_score    NUMERIC(4, 3)   NOT NULL,
    event_timeline_json TEXT            NOT NULL,  -- JSON array of timestamped events
    alert_status        TEXT            NOT NULL DEFAULT 'OPEN',
    detected_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    reviewed_at         TIMESTAMPTZ,
    reviewed_by         TEXT
);

CREATE INDEX IF NOT EXISTS idx_spoofing_alerts_client
    ON spoofing_alerts (client_id, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_spoofing_alerts_status
    ON spoofing_alerts (alert_status) WHERE alert_status = 'OPEN';

-- ─── Surveillance Audit Log ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS surveillance_audit (
    audit_id            TEXT        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type          TEXT        NOT NULL,   -- WASH_TRADE | SPOOFING | RING_TRADE
    alert_id            TEXT        NOT NULL,
    action              TEXT        NOT NULL,   -- DETECTED | REVIEWED | ESCALATED | DISMISSED
    actor               TEXT,
    notes               TEXT,
    logged_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_surveillance_audit_alert
    ON surveillance_audit (alert_id, logged_at DESC);
