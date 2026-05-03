-- Migration V8: Create API keys table (DMOS-P1-016)
-- Stores hashed API keys with rotation, revocation, and usage tracking.

CREATE TABLE dmos_api_keys (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(8) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    rate_limit_plan VARCHAR(64) DEFAULT 'default',
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(64),
    created_by VARCHAR(64) NOT NULL,
    UNIQUE (tenant_id, workspace_id, key_prefix)
);

-- Index for lookup by key prefix (DMOS-P1-016)
CREATE INDEX idx_api_keys_key_prefix ON dmos_api_keys(key_prefix);

-- Index for tenant/workspace scoping (DMOS-P1-016)
CREATE INDEX idx_api_keys_tenant_workspace ON dmos_api_keys(tenant_id, workspace_id);

-- Index for active keys (DMOS-P1-016)
CREATE INDEX idx_api_keys_active ON dmos_api_keys(tenant_id, workspace_id) WHERE revoked = FALSE;

-- Index for revoked keys (DMOS-P1-016)
CREATE INDEX idx_api_keys_revoked ON dmos_api_keys(revoked) WHERE revoked = TRUE;
