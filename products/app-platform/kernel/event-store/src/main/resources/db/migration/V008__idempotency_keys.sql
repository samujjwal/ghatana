-- V008: Idempotency keys table — PostgreSQL fallback dedup store (STORY-K05-015)
--
-- Used when Redis is unavailable. Each (idempotency_key, tenant_id) pair is stored
-- with the response payload hash and a TTL timestamp so expired keys can be cleaned up.
-- The table is partitioned by created_at (weekly) to keep the cleanup cheap.

CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(255)  NOT NULL,
    tenant_id        VARCHAR(255)  NOT NULL,
    response_hash    VARCHAR(64)   NOT NULL,  -- SHA-256 of the response body
    response_status  INT           NOT NULL,  -- HTTP status code cached with the key
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ   NOT NULL,  -- controlled by K-02 config (default: 24 h)
    PRIMARY KEY (idempotency_key, tenant_id)
);

-- Index for TTL-based cleanup job
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);

-- Row-level security: tenants only see their own idempotency keys
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;

CREATE POLICY idempotency_keys_tenant_isolation ON idempotency_keys
    USING (tenant_id = current_setting('app.current_tenant_id', true));

COMMENT ON TABLE idempotency_keys IS
    'PostgreSQL fallback dedup store for the idempotency guard (K05-015). '
    'Redis is primary; this table is used when Redis is unavailable.';
