CREATE TABLE IF NOT EXISTS finance_ai_model_registry (
    model_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    version VARCHAR(128),
    type VARCHAR(128),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS finance_ai_model_approval (
    model_id VARCHAR(255) PRIMARY KEY,
    approved BOOLEAN NOT NULL,
    approver VARCHAR(255),
    approval_date TIMESTAMPTZ,
    version VARCHAR(128),
    conditions_json TEXT NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS finance_ai_model_performance (
    id BIGSERIAL PRIMARY KEY,
    model_id VARCHAR(255) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    accuracy DOUBLE PRECISION NOT NULL,
    latency BIGINT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT finance_ai_model_performance_model_fk
        FOREIGN KEY (model_id) REFERENCES finance_ai_model_registry(model_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_finance_ai_model_performance_model_time
    ON finance_ai_model_performance (model_id, recorded_at);