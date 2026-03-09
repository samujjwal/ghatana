-- Guardian Database Schema
-- PostgreSQL 14+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table (parents)
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(255),
  photo_url TEXT,
  email_verified BOOLEAN DEFAULT FALSE,
  email_verification_token VARCHAR(255),
  email_verification_expires_at TIMESTAMP,
  password_reset_token VARCHAR(255),
  password_reset_expires_at TIMESTAMP,
  two_factor_enabled BOOLEAN DEFAULT FALSE,
  two_factor_secret VARCHAR(255),
  last_login TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Refresh tokens for JWT
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token VARCHAR(500) UNIQUE NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Children profiles
CREATE TABLE children (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  age INTEGER,
  avatar_url TEXT,
  birth_date DATE,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Devices (child's devices)
CREATE TABLE devices (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  child_id UUID REFERENCES children(id) ON DELETE SET NULL, -- Nullable to support unpaired devices
  device_type VARCHAR(50) NOT NULL, -- 'mobile', 'desktop', 'browser'
  device_name VARCHAR(255) NOT NULL,
  os_type VARCHAR(50), -- 'ios', 'android', 'windows', 'macos', 'linux', 'chrome', 'firefox'
  os_version VARCHAR(50),
  pairing_code VARCHAR(50) UNIQUE,
  pairing_expires TIMESTAMP,
  is_paired BOOLEAN DEFAULT FALSE,
  device_fingerprint VARCHAR(255),
  last_seen_at TIMESTAMP,
  last_ip VARCHAR(50),
  is_active BOOLEAN DEFAULT TRUE,
  status VARCHAR(20) DEFAULT 'active', -- 'active', 'inactive', 'blocked'
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Blocking policies
CREATE TABLE policies (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  child_id UUID REFERENCES children(id) ON DELETE CASCADE,
  device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  policy_type VARCHAR(50) NOT NULL, -- 'website', 'app', 'category', 'schedule'
  enabled BOOLEAN DEFAULT TRUE,
  priority INTEGER DEFAULT 0,
  config JSONB NOT NULL, -- Flexible config storage
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Block events (log of blocked attempts)
CREATE TABLE block_events (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  policy_id UUID REFERENCES policies(id) ON DELETE SET NULL,
  event_type VARCHAR(50) NOT NULL, -- 'website', 'app'
  blocked_item VARCHAR(500) NOT NULL, -- URL or app name
  category VARCHAR(100),
  reason TEXT,
  timestamp TIMESTAMP DEFAULT NOW()
);

-- Usage sessions (app/website usage tracking)
CREATE TABLE usage_sessions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  session_type VARCHAR(50) NOT NULL, -- 'app', 'website'
  item_name VARCHAR(500) NOT NULL, -- App name or domain
  category VARCHAR(100),
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP,
  duration_seconds INTEGER,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Guardian unified events (telemetry, alerts, configuration changes)
CREATE TABLE guardian_events (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  event_id UUID NOT NULL,
  kind VARCHAR(100) NOT NULL,
  subtype VARCHAR(100) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT NOW(),
  source_agent_type VARCHAR(100),
  source_agent_version VARCHAR(100),
  source_device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
  source_child_id UUID REFERENCES children(id) ON DELETE SET NULL,
  source_org_id UUID,
  source_session_id VARCHAR(255),
  context JSONB,
  payload JSONB,
  ai JSONB,
  privacy JSONB,
  metadata JSONB
);

-- User settings
CREATE TABLE settings (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  setting_key VARCHAR(100) NOT NULL,
  setting_value JSONB NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(user_id, setting_key)
);

-- Notifications
CREATE TABLE notifications (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  notification_type VARCHAR(50) NOT NULL, -- 'alert', 'info', 'warning'
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  data JSONB,
  read BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Device command queue (GuardianCommand storage)
CREATE TABLE device_commands (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  child_id UUID REFERENCES children(id) ON DELETE SET NULL,
  org_id UUID,
  kind VARCHAR(100) NOT NULL,
  action VARCHAR(100) NOT NULL,
  params JSONB,
  status VARCHAR(20) NOT NULL DEFAULT 'pending', -- 'pending', 'processed', 'failed', 'expired'
  issued_by_actor_type VARCHAR(50) NOT NULL, -- 'parent', 'child', 'system'
  issued_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  expires_at TIMESTAMP,
  processed_at TIMESTAMP
);

-- Audit log
CREATE TABLE audit_log (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  action VARCHAR(100) NOT NULL,
  resource_type VARCHAR(100),
  resource_id UUID,
  changes JSONB,
  ip_address VARCHAR(50),
  user_agent TEXT,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Device pairing requests
CREATE TABLE device_pairing_requests (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  pairing_code VARCHAR(6) UNIQUE NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(child_id) -- Only one active pairing code per child
);

-- Indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_children_user_id ON children(user_id);
CREATE INDEX idx_devices_child_id ON devices(child_id);
CREATE INDEX idx_devices_pairing_code ON devices(pairing_code);
CREATE INDEX idx_policies_user_id ON policies(user_id);
CREATE INDEX idx_policies_child_id ON policies(child_id);
CREATE INDEX idx_policies_device_id ON policies(device_id);
CREATE INDEX idx_block_events_device_id ON block_events(device_id);
CREATE INDEX idx_block_events_timestamp ON block_events(timestamp);
CREATE INDEX idx_usage_sessions_device_id ON usage_sessions(device_id);
CREATE INDEX idx_usage_sessions_start_time ON usage_sessions(start_time);
CREATE INDEX idx_guardian_events_device_id ON guardian_events(source_device_id);
CREATE INDEX idx_guardian_events_child_id ON guardian_events(source_child_id);
CREATE INDEX idx_guardian_events_kind_occurred_at ON guardian_events(kind, occurred_at);
CREATE INDEX idx_device_commands_device_status ON device_commands(device_id, status);
CREATE INDEX idx_device_commands_created_at ON device_commands(created_at);
CREATE INDEX idx_settings_user_id ON settings(user_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_read ON notifications(read);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_pairing_requests_code ON device_pairing_requests(pairing_code);
CREATE INDEX idx_pairing_requests_child_id ON device_pairing_requests(child_id);
CREATE INDEX idx_pairing_requests_expires ON device_pairing_requests(expires_at);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_children_updated_at BEFORE UPDATE ON children
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_devices_updated_at BEFORE UPDATE ON devices
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_policies_updated_at BEFORE UPDATE ON policies
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_settings_updated_at BEFORE UPDATE ON settings
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
