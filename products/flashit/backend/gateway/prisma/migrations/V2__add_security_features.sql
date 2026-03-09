-- Add security features for Phase 6: Refresh tokens, 2FA, Sessions, Password Reset, Rate Limiting

-- ============================================================================
-- REFRESH TOKENS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_name VARCHAR(255),
    device_type VARCHAR(50), -- MOBILE, WEB, DESKTOP
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens(revoked_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE refresh_tokens IS 'Stores refresh tokens for JWT rotation and session management';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the refresh token';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Token expiration timestamp (default 30 days)';

-- ============================================================================
-- TWO-FACTOR AUTHENTICATION (2FA) TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS two_factor_auth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT false,
    secret_key VARCHAR(255), -- Encrypted TOTP secret
    backup_codes TEXT[], -- Array of hashed backup codes
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_two_factor_auth_user_id ON two_factor_auth(user_id);
CREATE INDEX idx_two_factor_auth_enabled ON two_factor_auth(enabled) WHERE enabled = true;

COMMENT ON TABLE two_factor_auth IS 'Two-factor authentication settings and secrets for users';
COMMENT ON COLUMN two_factor_auth.secret_key IS 'TOTP secret key (base32 encoded and encrypted)';
COMMENT ON COLUMN two_factor_auth.backup_codes IS 'Array of hashed backup recovery codes';

-- ============================================================================
-- USER SESSIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_id UUID REFERENCES refresh_tokens(id) ON DELETE CASCADE,
    device_name VARCHAR(255),
    device_type VARCHAR(50), -- MOBILE, WEB, DESKTOP, TABLET
    os_name VARCHAR(100),
    os_version VARCHAR(50),
    browser_name VARCHAR(100),
    browser_version VARCHAR(50),
    ip_address INET,
    location VARCHAR(255), -- City, Country (optional geolocation)
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(255)
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_is_active ON user_sessions(is_active) WHERE is_active = true;
CREATE INDEX idx_user_sessions_last_activity ON user_sessions(last_activity_at DESC);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

COMMENT ON TABLE user_sessions IS 'Active user sessions with device tracking and multi-device support';
COMMENT ON COLUMN user_sessions.last_activity_at IS 'Last API request timestamp for this session';

-- ============================================================================
-- PASSWORD RESET TOKENS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

COMMENT ON TABLE password_reset_tokens IS 'Secure password reset tokens with time-limited validity';
COMMENT ON COLUMN password_reset_tokens.token_hash IS 'SHA-256 hash of the reset token';
COMMENT ON COLUMN password_reset_tokens.expires_at IS 'Token expiration (default 1 hour)';

-- ============================================================================
-- RATE LIMITING TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    ip_address INET,
    endpoint VARCHAR(255) NOT NULL,
    request_count INT NOT NULL DEFAULT 1,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE', -- FREE, BASIC, PRO, ENTERPRISE
    blocked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rate_limits_user_id ON rate_limits(user_id);
CREATE INDEX idx_rate_limits_ip_address ON rate_limits(ip_address);
CREATE INDEX idx_rate_limits_endpoint ON rate_limits(endpoint);
CREATE INDEX idx_rate_limits_window_end ON rate_limits(window_end);
CREATE INDEX idx_rate_limits_blocked_until ON rate_limits(blocked_until) WHERE blocked_until IS NOT NULL;

COMMENT ON TABLE rate_limits IS 'Request rate limiting with tier-based quotas';
COMMENT ON COLUMN rate_limits.tier IS 'User subscription tier affecting rate limits';
COMMENT ON COLUMN rate_limits.blocked_until IS 'Temporary block expiration for repeated violations';

-- ============================================================================
-- USER TIER SETTINGS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_tier_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE', -- FREE, BASIC, PRO, ENTERPRISE
    requests_per_minute INT NOT NULL DEFAULT 10,
    requests_per_hour INT NOT NULL DEFAULT 100,
    requests_per_day INT NOT NULL DEFAULT 1000,
    max_sessions INT NOT NULL DEFAULT 3,
    storage_limit_bytes BIGINT NOT NULL DEFAULT 1073741824, -- 1GB default
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_tier_settings_user_id ON user_tier_settings(user_id);
CREATE INDEX idx_user_tier_settings_tier ON user_tier_settings(tier);

COMMENT ON TABLE user_tier_settings IS 'Per-user tier configuration and quota limits';
COMMENT ON COLUMN user_tier_settings.tier IS 'Subscription tier: FREE, BASIC, PRO, ENTERPRISE';

-- ============================================================================
-- SECURITY AUDIT LOG TABLE (Enhanced)
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL, -- LOGIN_SUCCESS, LOGIN_FAILED, 2FA_ENABLED, 2FA_DISABLED, TOKEN_REFRESH, SESSION_REVOKED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED, RATE_LIMIT_EXCEEDED
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO', -- INFO, WARNING, CRITICAL
    ip_address INET,
    user_agent TEXT,
    device_type VARCHAR(50),
    location VARCHAR(255),
    details JSONB,
    success BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_audit_log_user_id ON security_audit_log(user_id);
CREATE INDEX idx_security_audit_log_event_type ON security_audit_log(event_type);
CREATE INDEX idx_security_audit_log_severity ON security_audit_log(severity);
CREATE INDEX idx_security_audit_log_created_at ON security_audit_log(created_at DESC);
CREATE INDEX idx_security_audit_log_ip_address ON security_audit_log(ip_address);

COMMENT ON TABLE security_audit_log IS 'Comprehensive security event audit trail';

-- ============================================================================
-- ADD 2FA COLUMNS TO USERS TABLE
-- ============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

CREATE INDEX idx_users_two_factor_enabled ON users(two_factor_enabled) WHERE two_factor_enabled = true;
CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;

COMMENT ON COLUMN users.two_factor_enabled IS 'Whether 2FA is enabled for this user';
COMMENT ON COLUMN users.failed_login_attempts IS 'Failed login counter for account lockout';
COMMENT ON COLUMN users.locked_until IS 'Account lockout expiration timestamp';

-- ============================================================================
-- FUNCTIONS AND TRIGGERS
-- ============================================================================

-- Function to clean up expired tokens
CREATE OR REPLACE FUNCTION cleanup_expired_tokens()
RETURNS void AS $$
BEGIN
    -- Delete expired refresh tokens
    DELETE FROM refresh_tokens WHERE expires_at < NOW() AND revoked_at IS NULL;
    
    -- Delete expired password reset tokens
    DELETE FROM password_reset_tokens WHERE expires_at < NOW() AND used_at IS NULL;
    
    -- Delete old rate limit records (older than 7 days)
    DELETE FROM rate_limits WHERE window_end < NOW() - INTERVAL '7 days';
    
    -- Delete old security audit logs (older than 90 days)
    DELETE FROM security_audit_log WHERE created_at < NOW() - INTERVAL '90 days';
END;
$$ LANGUAGE plpgsql;

-- Function to update session activity
CREATE OR REPLACE FUNCTION update_session_activity()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE user_sessions 
    SET last_activity_at = NOW() 
    WHERE id = NEW.refresh_token_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update refresh token last_used_at
CREATE OR REPLACE FUNCTION update_refresh_token_last_used()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_used_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_refresh_token_last_used
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_refresh_token_last_used();

-- ============================================================================
-- DEFAULT TIER SETTINGS FOR EXISTING USERS
-- ============================================================================
INSERT INTO user_tier_settings (user_id, tier, requests_per_minute, requests_per_hour, requests_per_day, max_sessions, storage_limit_bytes)
SELECT 
    id,
    'FREE',
    10,
    100,
    1000,
    3,
    1073741824
FROM users
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================================
-- GRANTS (if using specific app user)
-- ============================================================================
-- GRANT ALL ON ALL TABLES IN SCHEMA public TO flashit_app_user;
-- GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO flashit_app_user;
