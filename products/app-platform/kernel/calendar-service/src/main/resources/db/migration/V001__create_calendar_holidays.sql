-- V001: Holiday calendar table
-- Stores jurisdiction-scoped BS holidays with Gregorian equivalents.
-- Jurisdiction follows ISO 3166-1 alpha-2 / ISO 3166-2 format:
--   "NP"      = Nepal (federal)
--   "NP-BAG"  = Bagmati Province
--   "NP-MAD"  = Madhesh Province
--   etc.

CREATE TABLE IF NOT EXISTS calendar_holidays (
    id               TEXT         NOT NULL,
    bs_year          INTEGER      NOT NULL CHECK (bs_year BETWEEN 2000 AND 2200),
    bs_month         INTEGER      NOT NULL CHECK (bs_month BETWEEN 1 AND 12),
    bs_day           INTEGER      NOT NULL CHECK (bs_day BETWEEN 1 AND 32),
    gregorian_date   DATE         NOT NULL,
    name             TEXT         NOT NULL,
    type             TEXT         NOT NULL CHECK (type IN ('PUBLIC', 'TRADING', 'SETTLEMENT')),
    jurisdiction     TEXT         NOT NULL,
    recurring_bs_date BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_calendar_holidays PRIMARY KEY (id)
);

-- Fast lookup for the primary access pattern: holidays by jurisdiction and BS year
CREATE INDEX IF NOT EXISTS idx_cal_holidays_jurisdiction_year
    ON calendar_holidays (jurisdiction, bs_year);

-- Support recurring-holiday queries (all years, same BS month/day)
CREATE INDEX IF NOT EXISTS idx_cal_holidays_recurring
    ON calendar_holidays (jurisdiction, bs_month, bs_day)
    WHERE recurring_bs_date = TRUE;
