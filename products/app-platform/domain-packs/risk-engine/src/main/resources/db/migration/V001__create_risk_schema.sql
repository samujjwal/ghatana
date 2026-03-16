-- ============================================================================
-- D06 Risk Engine Schema
-- Stores margin accounts, position limits, and margin call history.
-- ============================================================================

CREATE TABLE IF NOT EXISTS margin_accounts (
    client_id       TEXT            NOT NULL,
    account_id      TEXT            NOT NULL,
    deposited       NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    used            NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    currency        TEXT            NOT NULL DEFAULT 'NPR',
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_margin_accounts_client
    ON margin_accounts (client_id);

-- ─── Position Limits ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS position_limits (
    client_id           TEXT            NOT NULL,
    instrument_id       TEXT            NOT NULL,
    max_long_quantity   BIGINT          NOT NULL DEFAULT 9223372036854775807,    -- Long.MAX_VALUE
    max_concentration   NUMERIC(5, 4)   NOT NULL DEFAULT 0.10,                  -- 10%
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, instrument_id)
);

-- ─── Margin Call Log ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS margin_calls (
    call_id         TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       TEXT            NOT NULL,
    account_id      TEXT            NOT NULL,
    deposited       NUMERIC(20, 4)  NOT NULL,
    required        NUMERIC(20, 4)  NOT NULL,
    utilization     NUMERIC(7, 6)   NOT NULL,
    status          TEXT            NOT NULL DEFAULT 'ISSUED',   -- ISSUED | SATISFIED | DEFAULTED
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_margin_calls_client
    ON margin_calls (client_id, issued_at DESC);

CREATE INDEX IF NOT EXISTS idx_margin_calls_status
    ON margin_calls (status) WHERE status = 'ISSUED';

-- ─── Risk Check Audit ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS risk_check_audit (
    check_id        TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        TEXT            NOT NULL,
    client_id       TEXT            NOT NULL,
    instrument_id   TEXT            NOT NULL,
    check_type      TEXT            NOT NULL,    -- MARGIN | POSITION_LIMIT | CONCENTRATION | INITIAL_MARGIN
    status          TEXT            NOT NULL,    -- APPROVE | DENY
    reason          TEXT,
    checked_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_risk_check_audit_order
    ON risk_check_audit (order_id);
