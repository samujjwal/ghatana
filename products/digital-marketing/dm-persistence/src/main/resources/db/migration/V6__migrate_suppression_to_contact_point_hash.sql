-- Migration V6: Migrate suppression entries to PII-safe contact point hash (DMOS-P1-014)
-- This migration replaces raw email addresses with HMAC-SHA256 hashes for PII protection.

DO $$
DECLARE
    hmac_key TEXT;
BEGIN
    -- Older environments had dmos_suppression; newer clean installs may not.
    IF to_regclass('public.dmos_suppression') IS NULL THEN
        RAISE NOTICE 'Skipping V6 suppression migration: table dmos_suppression does not exist';
        RETURN;
    END IF;

    -- Add new column for contact point hash
    ALTER TABLE dmos_suppression ADD COLUMN IF NOT EXISTS contact_point_hash VARCHAR(64);

    -- Compute HMAC-SHA256 hashes for existing email addresses.
    hmac_key := current_setting('dmos.pii_hmac_key', true);

    IF hmac_key IS NULL OR hmac_key = '' THEN
        RAISE EXCEPTION 'DMOS_PII_HMAC_KEY environment variable must be set for PII-safe migration';
    END IF;

    UPDATE dmos_suppression
    SET contact_point_hash = ENCODE(hmac(LOWER(TRIM(email)), hmac_key, 'sha256'), 'hex')
    WHERE contact_point_hash IS NULL AND email IS NOT NULL;

    ALTER TABLE dmos_suppression ALTER COLUMN contact_point_hash SET NOT NULL;
    ALTER TABLE dmos_suppression DROP COLUMN IF EXISTS email;
    CREATE INDEX IF NOT EXISTS idx_suppression_contact_point_hash ON dmos_suppression(contact_point_hash);

    RAISE NOTICE 'PII suppression migration completed with HMAC-SHA256';
EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'PII migration failed: %', SQLERRM;
END $$;
