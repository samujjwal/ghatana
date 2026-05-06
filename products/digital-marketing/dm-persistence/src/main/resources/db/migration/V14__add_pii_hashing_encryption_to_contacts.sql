-- DMOS-P0-001: Add PII hashing and encryption columns to contacts table
-- This migration adds email_hash and encrypted_email columns for privacy compliance
-- The email column will be deprecated in favor of these new columns

DO $$
BEGIN
	IF to_regclass('public.contacts') IS NULL THEN
		RAISE NOTICE 'Skipping V14 contacts PII migration: table contacts does not exist';
		RETURN;
	END IF;

	-- Add privacy-safe columns
	ALTER TABLE contacts ADD COLUMN IF NOT EXISTS email_hash VARCHAR(255) NOT NULL DEFAULT '';
	ALTER TABLE contacts ADD COLUMN IF NOT EXISTS encrypted_email TEXT NOT NULL DEFAULT '';

	-- Indexes/constraints for privacy-safe lookup path
	CREATE INDEX IF NOT EXISTS idx_contacts_email_hash ON contacts(email_hash, workspace_id);

	IF NOT EXISTS (
		SELECT 1
		FROM information_schema.table_constraints
		WHERE table_schema = 'public'
		  AND table_name = 'contacts'
		  AND constraint_name = 'uq_contacts_email_hash_workspace'
	) THEN
		ALTER TABLE contacts
			ADD CONSTRAINT uq_contacts_email_hash_workspace
			UNIQUE (email_hash, workspace_id);
	END IF;

	-- Documentation comments
	COMMENT ON COLUMN contacts.email_hash IS 'HMAC-SHA256 hash of email for privacy-compliant lookups (DMOS-P0-001)';
	COMMENT ON COLUMN contacts.encrypted_email IS 'AES-GCM encrypted email for deliverability (DMOS-P0-001)';
	COMMENT ON COLUMN contacts.email IS 'DEPRECATED: Will be removed after migration to email_hash/encrypted_email';
END $$;
