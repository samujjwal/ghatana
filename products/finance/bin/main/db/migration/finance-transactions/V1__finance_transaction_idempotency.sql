CREATE TABLE IF NOT EXISTS finance_transaction_idempotency (
    transaction_id VARCHAR(255) PRIMARY KEY,
    fingerprint TEXT NOT NULL,
    status VARCHAR(64) NOT NULL,
    message TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_finance_transaction_idempotency_expires_at
    ON finance_transaction_idempotency (expires_at);

CREATE TABLE IF NOT EXISTS finance_transaction_rate_limit_window (
    rate_limit_key VARCHAR(255) NOT NULL,
    window_start_epoch_seconds BIGINT NOT NULL,
    request_count INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rate_limit_key, window_start_epoch_seconds)
);

CREATE INDEX IF NOT EXISTS idx_finance_transaction_rate_limit_window_updated_at
    ON finance_transaction_rate_limit_window (updated_at);