-- P1-021: Create idempotency store table for shared idempotency middleware
--
-- This table stores idempotent request responses for deduplication.
-- It supports the IdempotencyMiddleware in dm-api.

CREATE TABLE IF NOT EXISTS dmos_idempotency_store (
    idempotency_key VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(256) NOT NULL,
    response_status INTEGER NOT NULL,
    response_headers JSONB,
    response_body BYTEA,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (idempotency_key, tenant_id),

    -- Index for TTL cleanup
    CONSTRAINT dmos_idempotency_store_expires_check
        CHECK (expires_at > created_at)
);

-- Index for cleanup of expired entries
CREATE INDEX idx_dmos_idempotency_expires ON dmos_idempotency_store(expires_at);

-- Index for tenant-scoped lookups
CREATE INDEX idx_dmos_idempotency_tenant ON dmos_idempotency_store(tenant_id);

-- Documentation
COMMENT ON TABLE dmos_idempotency_store IS 'P1-021: Stores idempotent request responses for deduplication';
COMMENT ON COLUMN dmos_idempotency_store.idempotency_key IS 'Client-provided idempotency key from X-Idempotency-Key header';
COMMENT ON COLUMN dmos_idempotency_store.tenant_id IS 'Tenant scope for the request';
COMMENT ON COLUMN dmos_idempotency_store.request_fingerprint IS 'Hash of request content for collision detection';
COMMENT ON COLUMN dmos_idempotency_store.response_status IS 'Cached HTTP status code';
COMMENT ON COLUMN dmos_idempotency_store.response_body IS 'Cached response body';
COMMENT ON COLUMN dmos_idempotency_store.expires_at IS 'TTL expiration time - entries cleaned up after this';
