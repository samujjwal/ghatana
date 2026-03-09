-- Migration: 005_notifications
-- Description: Add notifications table for parent alerts
-- Created: 2024-11-30

-- Notifications table for parent alerts
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    child_id UUID REFERENCES children(id) ON DELETE SET NULL,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'medium',
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ,
    
    -- Constraints
    CONSTRAINT notifications_type_check CHECK (type IN (
        'block_event',
        'risk_alert',
        'child_request',
        'request_decision',
        'usage_alert',
        'device_offline',
        'policy_violation',
        'system'
    )),
    CONSTRAINT notifications_priority_check CHECK (priority IN (
        'low',
        'medium',
        'high',
        'critical'
    ))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_child_id ON notifications(child_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_notifications_user_priority ON notifications(user_id, priority, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notifications(type);

-- Comment on table
COMMENT ON TABLE notifications IS 'Parent notifications for Guardian events and alerts';
COMMENT ON COLUMN notifications.type IS 'Type of notification: block_event, risk_alert, child_request, etc.';
COMMENT ON COLUMN notifications.priority IS 'Priority level: low, medium, high, critical';
COMMENT ON COLUMN notifications.metadata IS 'Additional context data as JSON';
