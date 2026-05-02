-- Durable Billing Ledger Plugin — Initial Schema
-- KP-023: Flyway migration for plugin_ledger_entries and plugin_ledger_accounts tables
--
-- Implements a double-entry ledger with:
--  - Idempotency via UNIQUE constraint on transaction_id
--  - Account balance denormalization in plugin_ledger_accounts for fast balance queries
--  - Status tracking: POSTED, REVERSED, FAILED

CREATE TABLE IF NOT EXISTS plugin_ledger_entries (
    entry_id       VARCHAR(128)   NOT NULL,
    transaction_id VARCHAR(256)   NOT NULL,
    tenant_id      VARCHAR(256)   NOT NULL,
    debit_account  VARCHAR(512)   NOT NULL,
    credit_account VARCHAR(512)   NOT NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    currency       VARCHAR(10)    NOT NULL,
    tx_type        VARCHAR(64)    NOT NULL,
    description    TEXT,
    occurred_at    BIGINT         NOT NULL,
    status         VARCHAR(32)    NOT NULL DEFAULT 'POSTED',
    reversal_of    VARCHAR(128),

    CONSTRAINT pk_plugin_ledger_entries     PRIMARY KEY (entry_id),
    CONSTRAINT uq_plugin_ledger_tx_id       UNIQUE (transaction_id),
    CONSTRAINT fk_plugin_ledger_reversal_of
        FOREIGN KEY (reversal_of) REFERENCES plugin_ledger_entries (entry_id)
        ON DELETE RESTRICT
);

-- Account balance summary table (denormalized for O(1) balance lookups)
CREATE TABLE IF NOT EXISTS plugin_ledger_accounts (
    account_name   VARCHAR(512)   NOT NULL,
    tenant_id      VARCHAR(256)   NOT NULL,
    balance        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    last_updated   BIGINT         NOT NULL,

    CONSTRAINT pk_plugin_ledger_accounts PRIMARY KEY (account_name, tenant_id)
);

-- Index for time-range queries on entries
CREATE INDEX IF NOT EXISTS idx_plugin_ledger_entries_tenant_ts
    ON plugin_ledger_entries (tenant_id, occurred_at ASC);

-- Index to accelerate account-based entry lookups
CREATE INDEX IF NOT EXISTS idx_plugin_ledger_entries_debit_account
    ON plugin_ledger_entries (debit_account, occurred_at ASC);

CREATE INDEX IF NOT EXISTS idx_plugin_ledger_entries_credit_account
    ON plugin_ledger_entries (credit_account, occurred_at ASC);
