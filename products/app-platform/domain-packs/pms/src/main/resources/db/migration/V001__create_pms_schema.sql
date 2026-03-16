-- ============================================================================
-- D03 Portfolio Management System Schema
-- Stores portfolios, holdings, target allocations, and NAV history.
-- ============================================================================

-- ─── Portfolios ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portfolios (
    portfolio_id        TEXT        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           TEXT        NOT NULL,
    name                TEXT        NOT NULL,
    strategy            TEXT        NOT NULL DEFAULT 'BALANCED',  -- GROWTH | INCOME | BALANCED | INDEX
    benchmark_id        TEXT,
    currency            TEXT        NOT NULL DEFAULT 'NPR',
    inception_date_ad   DATE        NOT NULL,
    inception_date_bs   TEXT        NOT NULL,  -- BS calendar
    status              TEXT        NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | FROZEN | CLOSED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portfolios_client
    ON portfolios (client_id);

CREATE INDEX IF NOT EXISTS idx_portfolios_status
    ON portfolios (status) WHERE status = 'ACTIVE';

-- ─── Portfolio Holdings ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portfolio_holdings (
    holding_id          TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id        TEXT            NOT NULL REFERENCES portfolios(portfolio_id),
    instrument_id       TEXT            NOT NULL,
    quantity            NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    avg_cost            NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    market_value        NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    weight_pct          NUMERIC(7, 4)   NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, instrument_id)
);

CREATE INDEX IF NOT EXISTS idx_holdings_portfolio
    ON portfolio_holdings (portfolio_id);

-- ─── Target Allocations ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS target_allocations (
    allocation_id       TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id        TEXT            NOT NULL REFERENCES portfolios(portfolio_id),
    model_name          TEXT            NOT NULL DEFAULT 'default',
    instrument_id       TEXT,
    asset_class         TEXT,            -- if instrument_id is null, alloc is by asset class
    target_weight_pct   NUMERIC(7, 4)   NOT NULL,
    min_weight_pct      NUMERIC(7, 4)   NOT NULL DEFAULT 0,
    max_weight_pct      NUMERIC(7, 4)   NOT NULL DEFAULT 100,
    status              TEXT            NOT NULL DEFAULT 'PENDING_REVIEW',  -- PENDING_REVIEW | APPROVED | REJECTED
    version             INT             NOT NULL DEFAULT 1,
    approved_by         TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_allocations_portfolio
    ON target_allocations (portfolio_id, model_name);

-- ─── NAV History ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nav_history (
    nav_id              TEXT            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id        TEXT            NOT NULL REFERENCES portfolios(portfolio_id),
    calc_date_ad        DATE            NOT NULL,
    calc_date_bs        TEXT            NOT NULL,
    total_nav           NUMERIC(24, 4)  NOT NULL,
    total_units         NUMERIC(24, 4)  NOT NULL DEFAULT 1,
    nav_per_unit        NUMERIC(20, 8)  NOT NULL,
    total_liabilities   NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    accrued_income      NUMERIC(20, 4)  NOT NULL DEFAULT 0,
    calculated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, calc_date_ad)
);

CREATE INDEX IF NOT EXISTS idx_nav_history_portfolio_date
    ON nav_history (portfolio_id, calc_date_ad DESC);
