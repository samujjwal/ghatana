-- CreateTable: refresh_tokens
-- Purpose: Store refresh tokens for secure session management
-- Migration: Add refresh token support for authentication

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id TEXT NOT NULL,
  token TEXT UNIQUE NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP,
  last_used_at TIMESTAMP,
  user_agent TEXT,
  ip_address TEXT,
  device_name TEXT,
  
  CONSTRAINT fk_refresh_tokens_user 
    FOREIGN KEY (user_id) 
    REFERENCES users(id) 
    ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens(revoked_at) WHERE revoked_at IS NULL;

-- Comments for documentation
COMMENT ON TABLE refresh_tokens IS 'Refresh tokens for secure session management';
COMMENT ON COLUMN refresh_tokens.token IS 'Cryptographically secure random token (64 hex chars)';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Token expiration timestamp (30 days from creation)';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Timestamp when token was revoked (logout, password change, etc.)';
COMMENT ON COLUMN refresh_tokens.last_used_at IS 'Last time token was used to refresh access token';
