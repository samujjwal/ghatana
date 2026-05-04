-- Migration V6: Migrate suppression entries to PII-safe contact point hash (DMOS-P1-014)
-- This migration replaces raw email addresses with HMAC-SHA256 hashes for PII protection.

-- Add new column for contact point hash
ALTER TABLE dmos_suppression ADD COLUMN IF NOT EXISTS contact_point_hash VARCHAR(64);

-- Compute HMAC-SHA256 hashes for existing email addresses using the HMAC key from environment
-- The HMAC key must be provided via DMOS_PII_HMAC_KEY environment variable
-- If the key is not available, the migration will fail to prevent non-PII-safe deployment
DO $$
DECLARE
    hmac_key TEXT;
BEGIN
    -- Get HMAC key from environment (requires pgcrypto extension)
    -- In production, this key must be set and rotated regularly
    hmac_key := current_setting('dmos.pii_hmac_key', true);
    
    IF hmac_key IS NULL OR hmac_key = '' THEN
        RAISE EXCEPTION 'DMOS_PII_HMAC_KEY environment variable must be set for PII-safe migration';
    END IF;
    
    -- Update existing records with HMAC-SHA256 hash
    UPDATE dmos_suppression
    SET contact_point_hash = ENCODE(hmac(LOWER(TRIM(email)), hmac_key, 'sha256'), 'hex')
    WHERE contact_point_hash IS NULL AND email IS NOT NULL;
    
    RAISE NOTICE 'PII suppression migration completed with HMAC-SHA256';
EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'PII migration failed: %', SQLERRM;
END $$;

-- Add NOT NULL constraint after migration
ALTER TABLE dmos_suppression ALTER COLUMN contact_point_hash SET NOT NULL;

-- Drop the old email column after verifying migration succeeded
-- This is irreversible - ensure verification before running in production
ALTER TABLE dmos_suppression DROP COLUMN IF EXISTS email;

-- Add index on contact_point_hash for faster suppression matching
CREATE INDEX IF NOT EXISTS idx_suppression_contact_point_hash ON dmos_suppression(contact_point_hash);
