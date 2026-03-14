-- V003__ledger_balance_snapshots.sql
-- Point-in-time balance snapshots for K16-008.

CREATE TABLE ledger_balance_snapshots (
    snapshot_id   UUID         NOT NULL PRIMARY KEY,
    account_id    UUID         NOT NULL,
    currency_code VARCHAR(10)  NOT NULL,
    net_balance   NUMERIC(19, 4) NOT NULL,
    snapshot_at   TIMESTAMPTZ  NOT NULL,
    tenant_id     UUID         NOT NULL,
    CONSTRAINT fk_lbs_account FOREIGN KEY (account_id)
        REFERENCES ledger_account (account_id)
);

CREATE INDEX idx_lbs_account_currency
    ON ledger_balance_snapshots (account_id, currency_code, tenant_id, snapshot_at DESC);
