-- V001__create_corporate_actions_schema.sql
-- D-12 Corporate Actions — Core Schema
-- Covers D12-001 through D12-014 primary persistence needs
-- Dual-calendar dates (Gregorian + BS) via K-15 integration

-- ─── Corporate Action Master ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS corporate_actions (
    ca_id               TEXT        PRIMARY KEY,
    issuer_id           TEXT        NOT NULL,
    ca_type             TEXT        NOT NULL CHECK (ca_type IN (
                            'CASH_DIVIDEND', 'STOCK_DIVIDEND', 'BONUS', 'RIGHTS', 'SPLIT', 'MERGER')),
    status              TEXT        NOT NULL DEFAULT 'ANNOUNCED' CHECK (status IN (
                            'ANNOUNCED', 'EX_DATE', 'RECORD_DATE', 'PAYMENT_DATE', 'COMPLETED', 'CANCELLED')),
    announced_date      DATE        NOT NULL,
    ex_date             DATE        NOT NULL,
    record_date         DATE        NOT NULL,
    payment_date        DATE        NOT NULL,

    -- Dual calendar support (K-15)
    ex_date_bs          TEXT,
    record_date_bs      TEXT,
    payment_date_bs     TEXT,

    -- Action-specific parameters
    ratio               NUMERIC(18,6) NOT NULL DEFAULT 0,   -- for splits/bonuses/rights
    currency            TEXT,                                 -- for cash dividends
    per_share_amount    NUMERIC(18,6),                        -- dividend per share

    -- Workflow
    created_by          TEXT        NOT NULL,
    approved_by         TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ca_issuer_id     ON corporate_actions (issuer_id);
CREATE INDEX IF NOT EXISTS idx_ca_status        ON corporate_actions (status);
CREATE INDEX IF NOT EXISTS idx_ca_payment_date  ON corporate_actions (payment_date);
CREATE INDEX IF NOT EXISTS idx_ca_ex_date       ON corporate_actions (ex_date);

-- ─── Holder Snapshots (D12-004: record-date position capture) ────────────────
CREATE TABLE IF NOT EXISTS ca_holder_snapshots (
    snapshot_id         TEXT        PRIMARY KEY,
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    client_id           TEXT        NOT NULL,
    account_id          TEXT        NOT NULL,
    instrument_id       TEXT        NOT NULL,
    position_qty        NUMERIC(18,6) NOT NULL,
    snapshot_date       DATE        NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (ca_id, client_id, account_id, instrument_id)
);

CREATE INDEX IF NOT EXISTS idx_ca_snapshots_ca_id      ON ca_holder_snapshots (ca_id);
CREATE INDEX IF NOT EXISTS idx_ca_snapshots_client_id  ON ca_holder_snapshots (client_id);

-- ─── Entitlements (D12-004 to D12-008) ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS ca_entitlements (
    entitlement_id      TEXT        PRIMARY KEY,
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    client_id           TEXT        NOT NULL,
    account_id          TEXT        NOT NULL,
    instrument_id       TEXT        NOT NULL,

    -- Derived from holder snapshot + CA ratio
    entitled_cash       NUMERIC(22,6),                       -- cash dividend amount
    entitled_shares     NUMERIC(18,6),                       -- bonus/stock dividend shares
    rights_qty          NUMERIC(18,6),                       -- rights units

    -- Tax
    withholding_tax     NUMERIC(22,6)  NOT NULL DEFAULT 0,
    net_cash            NUMERIC(22,6),

    status              TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN (
                            'PENDING', 'PROCESSED', 'FAILED', 'WAIVED')),
    processed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ca_entitlements_ca_id     ON ca_entitlements (ca_id);
CREATE INDEX IF NOT EXISTS idx_ca_entitlements_client_id ON ca_entitlements (client_id);
CREATE INDEX IF NOT EXISTS idx_ca_entitlements_status    ON ca_entitlements (status);

-- ─── Tax Certificates (D12-009 to D12-010) ───────────────────────────────────
CREATE TABLE IF NOT EXISTS ca_tax_certificates (
    cert_id             TEXT        PRIMARY KEY,
    entitlement_id      TEXT        NOT NULL REFERENCES ca_entitlements(entitlement_id),
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    client_id           TEXT        NOT NULL,
    tax_year            INT         NOT NULL,
    gross_amount        NUMERIC(22,6) NOT NULL,
    tax_deducted        NUMERIC(22,6) NOT NULL,
    net_amount          NUMERIC(22,6) NOT NULL,
    issued_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    document_path       TEXT        -- reference to stored PDF
);

CREATE INDEX IF NOT EXISTS idx_ca_tax_certs_client ON ca_tax_certificates (client_id, tax_year);

-- ─── Elections (D12-013: voluntary CA election handling) ─────────────────────
CREATE TABLE IF NOT EXISTS ca_elections (
    election_id         TEXT        PRIMARY KEY,
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    client_id           TEXT        NOT NULL,
    account_id          TEXT        NOT NULL,
    election_type       TEXT        NOT NULL,   -- e.g. 'CASH', 'SHARES', 'PARTIAL_CASH'
    elected_at          TIMESTAMPTZ  NOT NULL,
    confirmed           BOOLEAN     NOT NULL DEFAULT FALSE,
    confirmed_at        TIMESTAMPTZ,
    UNIQUE (ca_id, client_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_ca_elections_ca_id ON ca_elections (ca_id);

-- ─── Notification Log (D12-012) ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ca_notifications (
    notification_id     TEXT        PRIMARY KEY,
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    client_id           TEXT        NOT NULL,
    notification_type   TEXT        NOT NULL,   -- 'ANNOUNCEMENT', 'REMINDER', 'PAYMENT'
    channel             TEXT        NOT NULL,   -- 'EMAIL', 'SMS', 'PUSH', 'PORTAL'
    sent_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status              TEXT        NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_ca_notifications_ca_id     ON ca_notifications (ca_id);
CREATE INDEX IF NOT EXISTS idx_ca_notifications_client_id ON ca_notifications (client_id);

-- ─── Reconciliation Breaks (D12-014) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ca_reconciliation_breaks (
    break_id            TEXT        PRIMARY KEY,
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    client_id           TEXT        NOT NULL,
    break_type          TEXT        NOT NULL,   -- 'ENTITLEMENT_MISMATCH', 'POSITION_DELTA', 'TAX_VARIANCE'
    expected_value      NUMERIC(22,6),
    actual_value        NUMERIC(22,6),
    variance            NUMERIC(22,6),
    status              TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'ESCALATED')),
    detected_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ca_breaks_ca_id  ON ca_reconciliation_breaks (ca_id);
CREATE INDEX IF NOT EXISTS idx_ca_breaks_status ON ca_reconciliation_breaks (status);

-- ─── Instruction Narratives (D12-011) ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ca_instruction_narratives (
    narrative_id        TEXT        PRIMARY KEY,
    ca_id               TEXT        NOT NULL REFERENCES corporate_actions(ca_id),
    language            TEXT        NOT NULL DEFAULT 'en',
    summary             TEXT        NOT NULL,
    full_text           TEXT        NOT NULL,
    generated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ca_narratives_ca_id ON ca_instruction_narratives (ca_id);
