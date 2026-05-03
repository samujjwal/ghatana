-- DMOS-P0-6: Add idempotency tokens table for duplicate request prevention
-- This table stores idempotency keys to ensure that duplicate requests
-- with the same key return the same response without re-executing the operation

CREATE TABLE IF NOT EXISTS idempotency_tokens (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    operation_key VARCHAR(255) NOT NULL,
    response_payload TEXT,
    response_status INT NOT NULL,
    response_headers TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);

-- Create unique constraint on tenant_id + workspace_id + operation_key
-- to prevent duplicate idempotency keys within the same scope
CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_scope 
ON idempotency_tokens(tenant_id, workspace_id, operation_key);

-- Create index on expires_at for cleanup of expired tokens
CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at 
ON idempotency_tokens(expires_at);

-- Create index on operation_key for fast lookups
CREATE INDEX IF NOT EXISTS idx_idempotency_operation_key 
ON idempotency_tokens(operation_key);

-- Add comments
COMMENT ON TABLE idempotency_tokens IS 'Idempotency tokens for duplicate request prevention (DMOS-P0-6)';
COMMENT ON COLUMN idempotency_tokens.id IS 'Unique identifier for the idempotency token (UUID)';
COMMENT ON COLUMN idempotency_tokens.tenant_id IS 'Tenant identifier for scoping';
COMMENT ON COLUMN idempotency_tokens.workspace_id IS 'Workspace identifier for scoping';
COMMENT ON COLUMN idempotency_tokens.operation_key IS 'Client-provided idempotency key for the operation';
COMMENT ON COLUMN idempotency_tokens.response_payload IS 'Cached response body for replay';
COMMENT ON COLUMN idempotency_tokens.response_status IS 'HTTP status code of the cached response';
COMMENT ON COLUMN idempotency_tokens.response_headers IS 'HTTP headers of the cached response';
COMMENT ON COLUMN idempotency_tokens.expires_at IS 'Expiration time after which the token can be deleted';
