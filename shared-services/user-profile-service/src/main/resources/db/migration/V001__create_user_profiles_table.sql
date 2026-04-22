-- Migration: Create user_profiles table
-- Description: Creates the primary user_profiles table with multi-tenant isolation
-- Version: V001
-- Author: Platform Team
-- Date: 2026-04-21

CREATE TABLE IF NOT EXISTS user_profiles (
    -- Primary keys
    user_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    
    -- Profile fields
    email VARCHAR(512) NOT NULL,
    display_name VARCHAR(255),
    avatar_url TEXT,
    preferred_language VARCHAR(10) DEFAULT 'en-US',
    timezone VARCHAR(64) DEFAULT 'UTC',
    theme VARCHAR(20) DEFAULT 'system',
    notifications_enabled BOOLEAN DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT chk_user_profiles_theme CHECK (theme IN ('light', 'dark', 'system'))
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_user_profiles_tenant_id ON user_profiles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON user_profiles(email);
CREATE INDEX IF NOT EXISTS idx_user_profiles_display_name ON user_profiles(display_name);
CREATE INDEX IF NOT EXISTS idx_user_profiles_tenant_user ON user_profiles(tenant_id, user_id);

-- Unique constraint on email per tenant (from existing implementation)
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profiles_tenant_email 
    ON user_profiles(tenant_id, email);

-- Add comment to table
COMMENT ON TABLE user_profiles IS 'Multi-tenant user profile storage with preferences';

-- Add column comments
COMMENT ON COLUMN user_profiles.user_id IS 'Unique user identifier (sub claim from JWT)';
COMMENT ON COLUMN user_profiles.tenant_id IS 'Tenant the profile belongs to';
COMMENT ON COLUMN user_profiles.email IS 'Primary email address (read-only, sourced from IdP)';
COMMENT ON COLUMN user_profiles.display_name IS 'User-chosen display name; falls back to email prefix';
COMMENT ON COLUMN user_profiles.avatar_url IS 'Optional URL to profile picture';
COMMENT ON COLUMN user_profiles.preferred_language IS 'BCP-47 language tag, e.g. "en-US"';
COMMENT ON COLUMN user_profiles.timezone IS 'IANA timezone ID, e.g. "America/New_York"';
COMMENT ON COLUMN user_profiles.theme IS 'UI theme preference: light, dark, or system';
COMMENT ON COLUMN user_profiles.notifications_enabled IS 'Whether push/email notifications are enabled';
COMMENT ON COLUMN user_profiles.created_at IS 'Profile creation timestamp';
COMMENT ON COLUMN user_profiles.updated_at IS 'Last modification timestamp';

-- Trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_user_profiles_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_user_profiles_updated_at();
