-- Migration V7: Add credential revocation support (DMOS-P1-015)
-- This migration adds revoked and revokedAt columns to support token revocation.

-- Add revoked column (default false)
ALTER TABLE dmos_google_ads_credentials ADD COLUMN revoked BOOLEAN DEFAULT FALSE;

-- Add revoked_at column
ALTER TABLE dmos_google_ads_credentials ADD COLUMN revoked_at TIMESTAMP;

-- Add index for querying active credentials
CREATE INDEX idx_google_ads_credentials_active ON dmos_google_ads_credentials(tenant_id, connector_id) WHERE revoked = FALSE;

-- Add index for querying revoked credentials
CREATE INDEX idx_google_ads_credentials_revoked ON dmos_google_ads_credentials(revoked) WHERE revoked = TRUE;
