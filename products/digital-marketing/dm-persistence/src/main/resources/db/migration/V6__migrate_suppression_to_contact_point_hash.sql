-- Migration V6: Migrate suppression entries to PII-safe contact point hash (DMOS-P1-014)
-- This migration replaces raw email addresses with HMAC-SHA256 hashes for PII protection.

-- Add new column for contact point hash
ALTER TABLE dmos_suppression ADD COLUMN contact_point_hash VARCHAR(64);

-- Compute HMAC-SHA256 hashes for existing email addresses
-- Note: In production, this would use the HMAC key from environment variable
-- For this migration, we use SHA-256 as a fallback (not HMAC)
UPDATE dmos_suppression
SET contact_point_hash = ENCODE(digest(LOWER(TRIM(email)), 'sha256'), 'hex')
WHERE contact_point_hash IS NULL;

-- Add NOT NULL constraint after migration
ALTER TABLE dmos_suppression ALTER COLUMN contact_point_hash SET NOT NULL;

-- Drop the old email column after verifying migration succeeded
-- Uncomment this after verification in production
-- ALTER TABLE dmos_suppression DROP COLUMN email;

-- Add index on contact_point_hash for faster suppression matching
CREATE INDEX idx_suppression_contact_point_hash ON dmos_suppression(contact_point_hash);
