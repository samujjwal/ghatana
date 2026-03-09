-- Audit Logs Table
-- Stores comprehensive audit trail for security and compliance

CREATE TABLE IF NOT EXISTS audit_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type VARCHAR(100) NOT NULL,
  event_data JSONB,
  ip_address INET,
  user_agent TEXT,
  severity VARCHAR(20) DEFAULT 'info' CHECK (severity IN ('info', 'warning', 'critical')),
  created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_severity ON audit_logs(severity);
CREATE INDEX IF NOT EXISTS idx_audit_logs_composite ON audit_logs(user_id, event_type, created_at DESC);

-- JSONB index for event_data queries
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_data ON audit_logs USING GIN (event_data);

-- Comments for documentation
COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for security events and compliance (SOC 2, GDPR, HIPAA)';
COMMENT ON COLUMN audit_logs.user_id IS 'User who performed the action (NULL for system events)';
COMMENT ON COLUMN audit_logs.event_type IS 'Standardized event type (e.g., auth.login.success, policy.created)';
COMMENT ON COLUMN audit_logs.event_data IS 'Additional context and metadata in JSON format';
COMMENT ON COLUMN audit_logs.ip_address IS 'IP address from which the action was performed';
COMMENT ON COLUMN audit_logs.severity IS 'Event severity: info (normal), warning (security-relevant), critical (immediate attention)';

-- Partition by month for performance (optional, can be enabled later)
-- This will help manage large audit log tables in production

-- Function to clean up old audit logs (retention policy)
CREATE OR REPLACE FUNCTION cleanup_old_audit_logs(retention_days INT DEFAULT 365)
RETURNS INT AS $$
DECLARE
  deleted_count INT;
BEGIN
  DELETE FROM audit_logs
  WHERE created_at < NOW() - INTERVAL '1 day' * retention_days;
  
  GET DIAGNOSTICS deleted_count = ROW_COUNT;
  RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_audit_logs IS 'Cleanup audit logs older than specified days (default 365). Run periodically via cron.';
