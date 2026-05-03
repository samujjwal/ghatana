-- DMOS-P0-001: Add PII hashing and encryption columns to contacts table
-- This migration adds email_hash and encrypted_email columns for privacy compliance
-- The email column will be deprecated in favor of these new columns

-- Add email_hash column for privacy-compliant lookups
ALTER TABLE contacts 
ADD COLUMN IF NOT EXISTS email_hash VARCHAR(255) NOT NULL DEFAULT '';

-- Add encrypted_email column for storing encrypted raw email
ALTER TABLE contacts 
ADD COLUMN IF NOT EXISTS encrypted_email TEXT NOT NULL DEFAULT '';

-- Create index on email_hash for fast lookups
CREATE INDEX IF NOT EXISTS idx_contacts_email_hash 
ON contacts(email_hash, workspace_id);

-- Create unique constraint on email_hash + workspace_id to prevent duplicates
ALTER TABLE contacts 
ADD CONSTRAINT IF NOT EXISTS uq_contacts_email_hash_workspace 
UNIQUE (email_hash, workspace_id);

-- Add comment documenting the PII protection
COMMENT ON COLUMN contacts.email_hash IS 'HMAC-SHA256 hash of email for privacy-compliant lookups (DMOS-P0-001)';
COMMENT ON COLUMN contacts.encrypted_email IS 'AES-GCM encrypted email for deliverability (DMOS-P0-001)';
COMMENT ON COLUMN contacts.email IS 'DEPRECATED: Will be removed after migration to email_hash/encrypted_email';
