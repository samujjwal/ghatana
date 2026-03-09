-- YAPPC Database Migration: V5 - Authentication Tables
-- PostgreSQL 16
-- Adds support for user authentication, sessions, and OAuth

-- ========== Users Table ==========
CREATE TABLE IF NOT EXISTS yappc.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(128),
    password_hash VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    avatar_url TEXT,
    phone_number VARCHAR(32),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    preferences JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN', 'SUPER_ADMIN')),
    UNIQUE(tenant_id, email),
    UNIQUE(tenant_id, username)
);

CREATE INDEX idx_users_tenant ON yappc.users(tenant_id);
CREATE INDEX idx_users_email ON yappc.users(tenant_id, email);
CREATE INDEX idx_users_username ON yappc.users(tenant_id, username);
CREATE INDEX idx_users_status ON yappc.users(tenant_id, status);

CREATE TRIGGER trigger_users_updated_at 
    BEFORE UPDATE ON yappc.users 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Sessions Table ==========
CREATE TABLE IF NOT EXISTS yappc.sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    access_token VARCHAR(512) NOT NULL,
    refresh_token VARCHAR(512) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_info JSONB,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(access_token),
    UNIQUE(refresh_token)
);

CREATE INDEX idx_sessions_user ON yappc.sessions(user_id);
CREATE INDEX idx_sessions_tenant ON yappc.sessions(tenant_id);
CREATE INDEX idx_sessions_access_token ON yappc.sessions(access_token) WHERE revoked = FALSE;
CREATE INDEX idx_sessions_refresh_token ON yappc.sessions(refresh_token) WHERE revoked = FALSE;
CREATE INDEX idx_sessions_expires ON yappc.sessions(expires_at) WHERE revoked = FALSE;

-- ========== Email Verifications Table ==========
CREATE TABLE IF NOT EXISTS yappc.email_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(token)
);

CREATE INDEX idx_email_verifications_user ON yappc.email_verifications(user_id);
CREATE INDEX idx_email_verifications_token ON yappc.email_verifications(token) WHERE verified = FALSE;
CREATE INDEX idx_email_verifications_expires ON yappc.email_verifications(expires_at) WHERE verified = FALSE;

-- ========== Password Resets Table ==========
CREATE TABLE IF NOT EXISTS yappc.password_resets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    token VARCHAR(255) NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(token)
);

CREATE INDEX idx_password_resets_user ON yappc.password_resets(user_id);
CREATE INDEX idx_password_resets_token ON yappc.password_resets(token) WHERE used = FALSE;
CREATE INDEX idx_password_resets_expires ON yappc.password_resets(expires_at) WHERE used = FALSE;

-- ========== OAuth Accounts Table ==========
CREATE TABLE IF NOT EXISTS yappc.oauth_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_username VARCHAR(255),
    provider_email VARCHAR(255),
    access_token TEXT,
    refresh_token TEXT,
    token_type VARCHAR(32),
    scope TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    profile_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_oauth_provider CHECK (provider IN ('GOOGLE', 'GITHUB', 'MICROSOFT', 'GITLAB', 'BITBUCKET')),
    UNIQUE(provider, provider_user_id)
);

CREATE INDEX idx_oauth_accounts_user ON yappc.oauth_accounts(user_id);
CREATE INDEX idx_oauth_accounts_provider ON yappc.oauth_accounts(provider, provider_user_id);

CREATE TRIGGER trigger_oauth_accounts_updated_at 
    BEFORE UPDATE ON yappc.oauth_accounts 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Login Attempts Table (Rate Limiting & Security) ==========
CREATE TABLE IF NOT EXISTS yappc.login_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_login_attempts_email ON yappc.login_attempts(tenant_id, email, created_at);
CREATE INDEX idx_login_attempts_ip ON yappc.login_attempts(ip_address, created_at);
CREATE INDEX idx_login_attempts_created ON yappc.login_attempts(created_at);

-- Cleanup old login attempts (keep last 30 days)
CREATE INDEX idx_login_attempts_cleanup ON yappc.login_attempts(created_at) 
    WHERE created_at < NOW() - INTERVAL '30 days';
