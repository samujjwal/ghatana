-- Migration V7: Add credential revocation support (DMOS-P1-015)
-- This migration adds revoked and revokedAt columns to support token revocation.

DO $$
BEGIN
	IF to_regclass('public.dmos_google_ads_credentials') IS NULL THEN
		RAISE NOTICE 'Skipping V7 credential revocation migration: table dmos_google_ads_credentials does not exist';
		RETURN;
	END IF;

	ALTER TABLE dmos_google_ads_credentials ADD COLUMN IF NOT EXISTS revoked BOOLEAN DEFAULT FALSE;
	ALTER TABLE dmos_google_ads_credentials ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

	CREATE INDEX IF NOT EXISTS idx_google_ads_credentials_active
		ON dmos_google_ads_credentials(tenant_id, connector_id)
		WHERE revoked = FALSE;

	CREATE INDEX IF NOT EXISTS idx_google_ads_credentials_revoked
		ON dmos_google_ads_credentials(revoked)
		WHERE revoked = TRUE;
END $$;
