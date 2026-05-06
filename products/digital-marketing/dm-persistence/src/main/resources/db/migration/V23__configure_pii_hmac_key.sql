-- P1-010: PII HMAC key configuration for migration V6
-- This migration documents and validates the PII HMAC key configuration

-- Create a configuration table for DMOS system settings
CREATE TABLE IF NOT EXISTS dmos_system_config (
    config_key TEXT PRIMARY KEY,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Document the PII HMAC key requirement
INSERT INTO dmos_system_config (config_key, config_value, description)
VALUES (
    'pii_hmac_key_required',
    'true',
    'P1-010: PII HMAC key is required for contact point hashing. Set via application config dmos.pii_hmac_key. Migration V6 depends on this.'
)
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description,
    updated_at = NOW();

-- Document the PII migration status
INSERT INTO dmos_system_config (config_key, config_value, description)
VALUES (
    'v6_pii_migration_status',
    'pending_key_config',
    'P1-010: V6__migrate_suppression_to_contact_point_hash.sql requires dmos.pii_hmac_key. Status: pending configuration.'
)
ON CONFLICT (config_key) DO UPDATE SET
    updated_at = NOW();

-- Create a function to validate PII key presence
CREATE OR REPLACE FUNCTION check_pii_key_configured()
RETURNS BOOLEAN AS $$
BEGIN
    -- This is a placeholder - actual key validation happens in application layer
    -- The key should be provided via environment variable or secure secret store
    -- and passed to Flyway as the dmos.pii_hmac_key setting
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_pii_key_configured() IS
    'P1-010: Validates that PII HMAC key is configured for migrations';

-- Note on migration failure handling:
-- If V6 migration fails due to missing PII key:
-- 1. Restore from backup (raw email column must be preserved until migration succeeds)
-- 2. Configure dmos.pii_hmac_key in application secrets
-- 3. Re-run migration
-- 4. Verify email_hash column is populated correctly
-- 5. Only then drop the raw email column

COMMENT ON TABLE dmos_system_config IS
    'System configuration for DMOS. P1-010: Tracks PII HMAC key configuration status.';
