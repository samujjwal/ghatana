-- =============================================================================
-- V001__create_ledger_schema.sql
-- K16-001: Append-only double-entry ledger tables
-- K16-016: Chart of accounts with hierarchy
-- K16-010: Currency registry with precision rules
-- =============================================================================

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE journal_direction AS ENUM ('DEBIT', 'CREDIT');

CREATE TYPE account_type AS ENUM (
    'ASSET',
    'LIABILITY',
    'EQUITY',
    'REVENUE',
    'EXPENSE'
);

CREATE TYPE account_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');

CREATE TYPE rounding_mode AS ENUM ('HALF_UP', 'BANKERS');

-- ---------------------------------------------------------------------------
-- Currency registry (K16-010)
-- ---------------------------------------------------------------------------
CREATE TABLE currency_registry (
    code            VARCHAR(10)     NOT NULL PRIMARY KEY,   -- ISO 4217 (NPR, USD, BTC)
    name            VARCHAR(100)    NOT NULL,
    symbol          VARCHAR(10),
    decimal_places  SMALLINT        NOT NULL DEFAULT 2,     -- NPR=2, BTC=8, JPY=0
    rounding_mode   rounding_mode   NOT NULL DEFAULT 'HALF_UP',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Seed well-known currencies
INSERT INTO currency_registry (code, name, symbol, decimal_places, rounding_mode) VALUES
    ('NPR', 'Nepalese Rupee',  'रू',  2, 'HALF_UP'),
    ('USD', 'US Dollar',       '$',   2, 'HALF_UP'),
    ('EUR', 'Euro',            '€',   2, 'BANKERS'),
    ('JPY', 'Japanese Yen',    '¥',   0, 'HALF_UP'),
    ('GBP', 'British Pound',   '£',   2, 'BANKERS'),
    ('BTC', 'Bitcoin',         '₿',   8, 'HALF_UP')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Chart of accounts (K16-016)
-- ---------------------------------------------------------------------------
CREATE TABLE chart_of_accounts (
    account_id      UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    account_type    account_type    NOT NULL,
    parent_id       UUID            REFERENCES chart_of_accounts(account_id) ON DELETE RESTRICT,
    currency        VARCHAR(10)     NOT NULL REFERENCES currency_registry(code),
    status          account_status  NOT NULL DEFAULT 'ACTIVE',
    description     TEXT,
    tenant_id       UUID,               -- NULL = platform-level; non-null = tenant-scoped
    jurisdiction    VARCHAR(20),        -- e.g., 'NPL', 'SEBON'
    effective_from  DATE            NOT NULL DEFAULT CURRENT_DATE,
    effective_to    DATE,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_account_code_tenant UNIQUE (code, tenant_id)
);

CREATE INDEX idx_account_type   ON chart_of_accounts(account_type);
CREATE INDEX idx_account_tenant ON chart_of_accounts(tenant_id);
CREATE INDEX idx_account_parent ON chart_of_accounts(parent_id);

-- ---------------------------------------------------------------------------
-- Ledger journal (K16-001)
-- Immutable once posted: REVOKE UPDATE, DELETE enforced via GRANT below
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_journal (
    journal_id      UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    reference       VARCHAR(200)    NOT NULL,           -- business reference (order_id, trade_id, etc.)
    description     TEXT,
    fiscal_year     VARCHAR(10),                        -- e.g., '2081/82' (BS fiscal year)
    posted_at_utc   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    posted_at_bs    VARCHAR(30),                        -- BS date string 'YYYY-MM-DD'
    tenant_id       UUID,
    created_by      UUID,
    metadata        JSONB
);

CREATE INDEX idx_journal_reference   ON ledger_journal(reference);
CREATE INDEX idx_journal_fiscal_year ON ledger_journal(fiscal_year);
CREATE INDEX idx_journal_tenant      ON ledger_journal(tenant_id);
CREATE INDEX idx_journal_posted_at   ON ledger_journal(posted_at_utc);

-- ---------------------------------------------------------------------------
-- Journal entries (K16-001)
-- Each journal must have balanced debits == credits per currency
-- Hash chain per account for K16-006 tamper detection
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_journal_entry (
    entry_id        UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_id      UUID            NOT NULL REFERENCES ledger_journal(journal_id),
    account_id      UUID            NOT NULL REFERENCES chart_of_accounts(account_id),
    direction       journal_direction NOT NULL,
    amount          DECIMAL(28, 12) NOT NULL CHECK (amount > 0),   -- K16-001: precision 28,12
    currency        VARCHAR(10)     NOT NULL REFERENCES currency_registry(code),
    description     TEXT,
    entry_hash      VARCHAR(64),    -- SHA-256 of (prev_hash || entry data) for K16-006
    sequence_num    BIGINT,         -- per-account monotonic sequence for hash chain
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_entry_journal     ON ledger_journal_entry(journal_id);
CREATE INDEX idx_entry_account     ON ledger_journal_entry(account_id);
CREATE INDEX idx_entry_account_seq ON ledger_journal_entry(account_id, sequence_num);

-- ---------------------------------------------------------------------------
-- Account balance materialization (K16-003)
-- Updated via trigger on each journal_entry insert
-- ---------------------------------------------------------------------------
CREATE TABLE account_balance (
    account_id      UUID            NOT NULL REFERENCES chart_of_accounts(account_id),
    currency        VARCHAR(10)     NOT NULL REFERENCES currency_registry(code),
    balance         DECIMAL(28, 12) NOT NULL DEFAULT 0,
    debit_total     DECIMAL(28, 12) NOT NULL DEFAULT 0,
    credit_total    DECIMAL(28, 12) NOT NULL DEFAULT 0,
    entry_count     BIGINT          NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    PRIMARY KEY (account_id, currency)
);

-- Trigger function: update materialized balance on each entry insert
CREATE OR REPLACE FUNCTION update_account_balance()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_debit_delta  DECIMAL(28, 12) := 0;
    v_credit_delta DECIMAL(28, 12) := 0;
    v_account_type account_type;
BEGIN
    SELECT a.account_type INTO v_account_type
    FROM chart_of_accounts a WHERE a.account_id = NEW.account_id;

    IF NEW.direction = 'DEBIT' THEN
        v_debit_delta := NEW.amount;
    ELSE
        v_credit_delta := NEW.amount;
    END IF;

    INSERT INTO account_balance (account_id, currency, balance, debit_total, credit_total, entry_count)
    VALUES (NEW.account_id, NEW.currency,
            CASE v_account_type
                WHEN 'ASSET'   THEN v_debit_delta - v_credit_delta
                WHEN 'EXPENSE' THEN v_debit_delta - v_credit_delta
                ELSE                v_credit_delta - v_debit_delta
            END,
            v_debit_delta, v_credit_delta, 1)
    ON CONFLICT (account_id, currency) DO UPDATE SET
        debit_total     = account_balance.debit_total + v_debit_delta,
        credit_total    = account_balance.credit_total + v_credit_delta,
        balance         = CASE v_account_type
                              WHEN 'ASSET'   THEN account_balance.debit_total + v_debit_delta
                                                  - (account_balance.credit_total + v_credit_delta)
                              WHEN 'EXPENSE' THEN account_balance.debit_total + v_debit_delta
                                                  - (account_balance.credit_total + v_credit_delta)
                              ELSE                account_balance.credit_total + v_credit_delta
                                                  - (account_balance.debit_total + v_debit_delta)
                          END,
        entry_count     = account_balance.entry_count + 1,
        last_updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_account_balance_update
    AFTER INSERT ON ledger_journal_entry
    FOR EACH ROW EXECUTE FUNCTION update_account_balance();

-- ---------------------------------------------------------------------------
-- Immutability enforcement (K16-001, K16-005)
-- Revoke UPDATE and DELETE from the application role
-- Role 'app_ledger' must exist or use your application DB role here
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    -- Only REVOKE if the role exists (avoids migration failure in dev without the role)
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_ledger') THEN
        REVOKE UPDATE, DELETE ON ledger_journal FROM app_ledger;
        REVOKE UPDATE, DELETE ON ledger_journal_entry FROM app_ledger;
    END IF;
END $$;
