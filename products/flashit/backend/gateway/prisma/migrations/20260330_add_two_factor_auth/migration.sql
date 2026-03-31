-- CreateTable: two_factor_auth
-- Purpose: Store two-factor authentication settings and backup codes
-- Migration: Add 2FA support for enhanced security

CREATE TABLE IF NOT EXISTS two_factor_auth (
  id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id TEXT UNIQUE NOT NULL,
  secret TEXT NOT NULL,  -- Encrypted TOTP secret (AES-256-GCM)
  enabled BOOLEAN DEFAULT FALSE NOT NULL,
  backup_codes TEXT[] NOT NULL DEFAULT '{}',  -- Array of encrypted backup codes
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  
  CONSTRAINT fk_two_factor_auth_user 
    FOREIGN KEY (user_id) 
    REFERENCES users(id) 
    ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_two_factor_auth_user_id ON two_factor_auth(user_id);
CREATE INDEX idx_two_factor_auth_enabled ON two_factor_auth(enabled) WHERE enabled = true;

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_two_factor_auth_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_two_factor_auth_updated_at
  BEFORE UPDATE ON two_factor_auth
  FOR EACH ROW
  EXECUTE FUNCTION update_two_factor_auth_updated_at();

-- Comments for documentation
COMMENT ON TABLE two_factor_auth IS 'Two-factor authentication settings for users';
COMMENT ON COLUMN two_factor_auth.secret IS 'Encrypted TOTP secret key (AES-256-GCM)';
COMMENT ON COLUMN two_factor_auth.enabled IS 'Whether 2FA is active for this user';
COMMENT ON COLUMN two_factor_auth.backup_codes IS 'Array of encrypted single-use backup codes';
