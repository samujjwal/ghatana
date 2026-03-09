-- Performance Optimization Indexes for Flashit
-- Created: 2026-01-11
-- Purpose: P1.3 - Database index optimization for common query patterns

-- ============================================================================
-- MOMENT QUERIES
-- ============================================================================

-- User's moments with sphere filtering (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_moments_user_sphere_deleted 
ON moments(user_id, sphere_id, deleted_at) 
WHERE deleted_at IS NULL;

-- Chronological listing of moments (timeline view)
CREATE INDEX IF NOT EXISTS idx_moments_captured_desc 
ON moments(captured_at DESC, deleted_at) 
WHERE deleted_at IS NULL;

-- Full-text search on moment content
CREATE INDEX IF NOT EXISTS idx_moments_content_search 
ON moments USING gin(to_tsvector('english', content_text));

-- Moments by sphere (for sphere detail pages)
CREATE INDEX IF NOT EXISTS idx_moments_sphere_time 
ON moments(sphere_id, captured_at DESC, deleted_at) 
WHERE deleted_at IS NULL;

-- ============================================================================
-- SPHERE ACCESS QUERIES
-- ============================================================================

-- User's accessible spheres (primary navigation query)
CREATE INDEX IF NOT EXISTS idx_sphere_access_user_active 
ON sphere_access(user_id, revoked_at, role) 
WHERE revoked_at IS NULL;

-- Sphere members list (for collaboration features)
CREATE INDEX IF NOT EXISTS idx_sphere_access_sphere_active 
ON sphere_access(sphere_id, role, revoked_at) 
WHERE revoked_at IS NULL;

-- Composite for permission checks
CREATE INDEX IF NOT EXISTS idx_sphere_access_user_sphere 
ON sphere_access(user_id, sphere_id, revoked_at) 
WHERE revoked_at IS NULL;

-- ============================================================================
-- AUDIT EVENTS
-- ============================================================================

-- User's audit trail (chronological)
CREATE INDEX IF NOT EXISTS idx_audit_events_user_time 
ON audit_events(user_id, created_at DESC);

-- Event type filtering for analytics
CREATE INDEX IF NOT EXISTS idx_audit_events_type_time 
ON audit_events(event_type, created_at DESC);

-- Sphere-specific audit log
CREATE INDEX IF NOT EXISTS idx_audit_events_resource 
ON audit_events(resource_type, resource_id, created_at DESC);

-- ============================================================================
-- USER SESSIONS & AUTHENTICATION
-- ============================================================================

-- Active session lookups
CREATE INDEX IF NOT EXISTS idx_user_sessions_token 
ON user_sessions(session_token, revoked_at) 
WHERE revoked_at IS NULL;

-- User's active sessions
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active 
ON user_sessions(user_id, revoked_at, created_at DESC) 
WHERE revoked_at IS NULL;

-- Refresh token lookups
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_active 
ON refresh_tokens(token_hash, revoked_at) 
WHERE revoked_at IS NULL;

-- ============================================================================
-- TWO-FACTOR AUTHENTICATION
-- ============================================================================

-- 2FA lookup by user
CREATE INDEX IF NOT EXISTS idx_two_factor_auth_user 
ON two_factor_auth(user_id, enabled);

-- ============================================================================
-- SPHERES
-- ============================================================================

-- User's owned spheres
CREATE INDEX IF NOT EXISTS idx_spheres_owner 
ON spheres(owner_id, deleted_at) 
WHERE deleted_at IS NULL;

-- Sphere type filtering
CREATE INDEX IF NOT EXISTS idx_spheres_type 
ON spheres(type, deleted_at) 
WHERE deleted_at IS NULL;

-- ============================================================================
-- MOMENT LINKS (Connections between moments)
-- ============================================================================

-- Links from a moment
CREATE INDEX IF NOT EXISTS idx_moment_links_from 
ON moment_links(from_moment_id, link_type, deleted_at) 
WHERE deleted_at IS NULL;

-- Links to a moment
CREATE INDEX IF NOT EXISTS idx_moment_links_to 
ON moment_links(to_moment_id, link_type, deleted_at) 
WHERE deleted_at IS NULL;

-- ============================================================================
-- USER TIER SETTINGS (Billing)
-- ============================================================================

-- Tier usage lookups
CREATE INDEX IF NOT EXISTS idx_user_tier_settings_user 
ON user_tier_settings(user_id);

-- ============================================================================
-- SECURITY AUDIT LOGS
-- ============================================================================

-- Security event timeline
CREATE INDEX IF NOT EXISTS idx_security_audit_user_time 
ON security_audit_logs(user_id, created_at DESC);

-- Event type analysis
CREATE INDEX IF NOT EXISTS idx_security_audit_event_time 
ON security_audit_logs(event_type, created_at DESC);

-- IP-based analysis
CREATE INDEX IF NOT EXISTS idx_security_audit_ip 
ON security_audit_logs(ip_address, created_at DESC);

-- ============================================================================
-- PASSWORD RESET TOKENS
-- ============================================================================

-- Token validation lookups
CREATE INDEX IF NOT EXISTS idx_password_reset_token 
ON password_reset_tokens(token, used, expires_at);

-- User's reset tokens
CREATE INDEX IF NOT EXISTS idx_password_reset_user 
ON password_reset_tokens(user_id, created_at DESC);

-- ============================================================================
-- USAGE NOTES
-- ============================================================================

/*
These indexes optimize the following query patterns:

1. User timeline: moments for a user across all spheres
2. Sphere timeline: moments within a specific sphere
3. Permission checks: sphere access validation
4. Authentication: session and token lookups
5. Audit trails: security and business event logs
6. Full-text search: content-based moment discovery
7. Collaboration: sphere member lists and access control
8. Analytics: event aggregation by type and time

Performance expectations:
- Moment queries: <50ms for 10k moments per user
- Permission checks: <10ms (indexed foreign keys)
- Session lookups: <5ms (unique token indexes)
- Audit queries: <100ms for 1M events (time-series indexes)
- Full-text search: <200ms on 100k moments

Monitor these indexes with:
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

Drop unused indexes after production analysis:
-- SELECT 'DROP INDEX ' || indexrelname || ';'
-- FROM pg_stat_user_indexes
-- WHERE idx_scan = 0 AND schemaname = 'public';
*/
