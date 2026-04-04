-- V011__implement_database_level_tenant_isolation.sql
-- Migration to add database-level tenant isolation for enhanced security
-- 
-- This migration implements:
-- 1. Row-Level Security (RLS) policies for all tables
-- 2. Tenant context management functions
-- 3. Audit triggers for cross-tenant access detection
-- 4. Performance optimizations for RLS
-- 
-- Author: Data-Cloud Security Team
-- Date: 2026-04-03
-- Ticket: DC-SEC-001

-- ====================================================================================
-- Tenant Context Management
-- ====================================================================================

-- Create schema for tenant isolation functions
CREATE SCHEMA IF NOT EXISTS tenant_security;

-- Function to set current tenant context
CREATE OR REPLACE FUNCTION tenant_security.set_current_tenant(tenant_id text)
RETURNS void AS $$
BEGIN
    PERFORM set_config('app.current_tenant_id', tenant_id, false);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to get current tenant context
CREATE OR REPLACE FUNCTION tenant_security.get_current_tenant()
RETURNS text AS $$
BEGIN
    RETURN current_setting('app.current_tenant_id', true);
END;
$$ LANGUAGE plpgsql STABLE;

-- Function to check if tenant context is set
CREATE OR REPLACE FUNCTION tenant_security.is_tenant_context_set()
RETURNS boolean AS $$
BEGIN
    RETURN current_setting('app.current_tenant_id', true) IS NOT NULL AND
           current_setting('app.current_tenant_id', true) != '';
END;
$$ LANGUAGE plpgsql STABLE;

-- ====================================================================================
-- Row-Level Security Policies for Events Table
-- ====================================================================================

-- Enable RLS on events table
ALTER TABLE events ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on events
CREATE POLICY tenant_isolation_events ON events
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

-- Create policy for admin access on events (bypass RLS)
CREATE POLICY admin_access_events ON events
    FOR ALL
    TO admin_user
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- Row-Level Security Policies for Entities Table
-- ====================================================================================

-- Enable RLS on entities table
ALTER TABLE entities ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on entities
CREATE POLICY tenant_isolation_entities ON entities
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

-- Create policy for admin access on entities
CREATE POLICY admin_access_entities ON entities
    FOR ALL
    TO admin_user
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- Row-Level Security Policies for Collections Metadata
-- ====================================================================================

-- Enable RLS on collections metadata table
ALTER TABLE collections_metadata ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on collections
CREATE POLICY tenant_isolation_collections ON collections_metadata
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

-- Create policy for admin access on collections
CREATE POLICY admin_access_collections ON collections_metadata
    FOR ALL
    TO admin_user
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- Row-Level Security Policies for Timeseries Table
-- ====================================================================================

-- Enable RLS on timeseries table
ALTER TABLE timeseries ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on timeseries
CREATE POLICY tenant_isolation_timeseries ON timeseries
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

-- Create policy for admin access on timeseries
CREATE POLICY admin_access_timeseries ON timeseries
    FOR ALL
    TO admin_user
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- Cross-Tenant Access Detection and Audit
-- ====================================================================================

-- Create table for cross-tenant access audit log
CREATE TABLE IF NOT EXISTS tenant_security.cross_tenant_access_log (
    id BIGSERIAL PRIMARY KEY,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attempted_tenant_id VARCHAR(255),
    actual_tenant_id VARCHAR(255),
    user_id VARCHAR(255),
    table_name VARCHAR(255),
    operation VARCHAR(50),
    query_text TEXT,
    client_ip INET,
    severity VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_notes TEXT
);

-- Create index for efficient querying
CREATE INDEX idx_cross_tenant_detected_at ON tenant_security.cross_tenant_access_log(detected_at DESC);
CREATE INDEX idx_cross_tenant_severity ON tenant_security.cross_tenant_access_log(severity);
CREATE INDEX idx_cross_tenant_resolved ON tenant_security.cross_tenant_access_log(resolved);

-- Function to detect cross-tenant access attempts
CREATE OR REPLACE FUNCTION tenant_security.detect_cross_tenant_access()
RETURNS TRIGGER AS $$
DECLARE
    attempted_tenant TEXT;
    actual_tenant TEXT;
BEGIN
    -- Only log if tenant context is set
    IF tenant_security.is_tenant_context_set() THEN
        attempted_tenant := tenant_security.get_current_tenant();
        actual_tenant := NEW.tenant_id;
        
        -- If tenants don't match, log the incident
        IF attempted_tenant != actual_tenant THEN
            INSERT INTO tenant_security.cross_tenant_access_log (
                attempted_tenant_id,
                actual_tenant_id,
                user_id,
                table_name,
                operation,
                query_text,
                severity
            ) VALUES (
                attempted_tenant,
                actual_tenant,
                current_user,
                TG_TABLE_NAME,
                TG_OP,
                current_query(),
                'CRITICAL'
            );
            
            -- Raise warning (but don't block - let RLS policy handle blocking)
            RAISE WARNING 'Cross-tenant access detected: Attempted %, Actual %, Table: %, Operation: %',
                attempted_tenant, actual_tenant, TG_TABLE_NAME, TG_OP;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create triggers for cross-tenant access detection
DROP TRIGGER IF EXISTS detect_cross_tenant_events ON events;
CREATE TRIGGER detect_cross_tenant_events
    BEFORE INSERT OR UPDATE ON events
    FOR EACH ROW
    EXECUTE FUNCTION tenant_security.detect_cross_tenant_access();

DROP TRIGGER IF EXISTS detect_cross_tenant_entities ON entities;
CREATE TRIGGER detect_cross_tenant_entities
    BEFORE INSERT OR UPDATE ON entities
    FOR EACH ROW
    EXECUTE FUNCTION tenant_security.detect_cross_tenant_access();

DROP TRIGGER IF EXISTS detect_cross_tenant_collections ON collections_metadata;
CREATE TRIGGER detect_cross_tenant_collections
    BEFORE INSERT OR UPDATE ON collections_metadata
    FOR EACH ROW
    EXECUTE FUNCTION tenant_security.detect_cross_tenant_access();

DROP TRIGGER IF EXISTS detect_cross_tenant_timeseries ON timeseries;
CREATE TRIGGER detect_cross_tenant_timeseries
    BEFORE INSERT OR UPDATE ON timeseries
    FOR EACH ROW
    EXECUTE FUNCTION tenant_security.detect_cross_tenant_access();

-- ====================================================================================
-- Tenant Context Validation Function
-- ====================================================================================

-- Function to validate tenant context is set before operations
CREATE OR REPLACE FUNCTION tenant_security.validate_tenant_context()
RETURNS void AS $$
BEGIN
    IF NOT tenant_security.is_tenant_context_set() THEN
        RAISE EXCEPTION 'Tenant context not set. Operation denied for security reasons.';
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ====================================================================================
-- Performance Optimizations
-- ====================================================================================

-- Create composite indexes for RLS performance
-- These indexes help PostgreSQL optimize RLS policy evaluation

-- Events table optimized indexes for RLS
CREATE INDEX IF NOT EXISTS idx_events_tenant_rls ON events(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_events_tenant_stream_rls ON events(tenant_id, stream_name, partition_id, event_offset);

-- Entities table optimized indexes for RLS
CREATE INDEX IF NOT EXISTS idx_entities_tenant_rls ON entities(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_entities_tenant_collection_rls ON entities(tenant_id, collection_name, created_at DESC);

-- Collections metadata optimized indexes
CREATE INDEX IF NOT EXISTS idx_collections_tenant_rls ON collections_metadata(tenant_id, id);

-- Timeseries table optimized indexes for RLS
CREATE INDEX IF NOT EXISTS idx_timeseries_tenant_rls ON timeseries(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_timeseries_tenant_timestamp_rls ON timeseries(tenant_id, timestamp DESC);

-- ====================================================================================
-- Monitoring Views
-- ====================================================================================

-- View for cross-tenant access summary
CREATE OR REPLACE VIEW tenant_security.cross_tenant_access_summary AS
SELECT 
    DATE_TRUNC('hour', detected_at) as hour,
    attempted_tenant_id,
    table_name,
    operation,
    COUNT(*) as attempt_count,
    MAX(severity) as max_severity
FROM tenant_security.cross_tenant_access_log
WHERE detected_at > NOW() - INTERVAL '24 hours'
GROUP BY 1, 2, 3, 4
ORDER BY hour DESC, attempt_count DESC;

-- View for tenant data distribution
CREATE OR REPLACE VIEW tenant_security.tenant_data_distribution AS
SELECT 
    tenant_id,
    'events' as table_name,
    COUNT(*) as record_count,
    pg_size_pretty(pg_total_relation_size('events')) as storage_size
FROM events
GROUP BY tenant_id

UNION ALL

SELECT 
    tenant_id,
    'entities' as table_name,
    COUNT(*) as record_count,
    pg_size_pretty(pg_total_relation_size('entities')) as storage_size
FROM entities
GROUP BY tenant_id

UNION ALL

SELECT 
    tenant_id,
    'collections' as table_name,
    COUNT(*) as record_count,
    pg_size_pretty(pg_total_relation_size('collections_metadata')) as storage_size
FROM collections_metadata
GROUP BY tenant_id

UNION ALL

SELECT 
    tenant_id,
    'timeseries' as table_name,
    COUNT(*) as record_count,
    pg_size_pretty(pg_total_relation_size('timeseries')) as storage_size
FROM timeseries
GROUP BY tenant_id

ORDER BY tenant_id, table_name;

-- ====================================================================================
-- Cleanup and Maintenance Functions
-- ====================================================================================

-- Function to clean up old cross-tenant access logs
CREATE OR REPLACE FUNCTION tenant_security.cleanup_old_access_logs(retention_days integer DEFAULT 90)
RETURNS integer AS $$
DECLARE
    deleted_count integer;
BEGIN
    DELETE FROM tenant_security.cross_tenant_access_log
    WHERE detected_at < NOW() - (retention_days || ' days')::INTERVAL
      AND resolved = true;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to resolve cross-tenant access incidents
CREATE OR REPLACE FUNCTION tenant_security.resolve_access_incident(
    incident_id bigint,
    resolution_notes text
)
RETURNS boolean AS $$
BEGIN
    UPDATE tenant_security.cross_tenant_access_log
    SET resolved = true,
        resolution_notes = resolution_notes
    WHERE id = incident_id;
    
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

-- ====================================================================================
-- Documentation
-- ====================================================================================

COMMENT ON SCHEMA tenant_security IS 'Schema for tenant isolation security functions and audit logging';

COMMENT ON FUNCTION tenant_security.set_current_tenant(text) IS 
    'Sets the current tenant context for row-level security policies. Must be called before accessing tenant data.';

COMMENT ON FUNCTION tenant_security.get_current_tenant() IS 
    'Returns the current tenant context. Returns NULL if not set.';

COMMENT ON FUNCTION tenant_security.validate_tenant_context() IS 
    'Validates that tenant context is set. Raises exception if not set.';

COMMENT ON TABLE tenant_security.cross_tenant_access_log IS 
    'Audit log for detected cross-tenant access attempts. Review regularly for security incidents.';

COMMENT ON POLICY tenant_isolation_events ON events IS 
    'Row-level security policy ensuring users can only access events from their own tenant';

COMMENT ON POLICY tenant_isolation_entities ON entities IS 
    'Row-level security policy ensuring users can only access entities from their own tenant';

COMMENT ON POLICY tenant_isolation_collections ON collections_metadata IS 
    'Row-level security policy ensuring users can only access collections from their own tenant';

COMMENT ON POLICY tenant_isolation_timeseries ON timeseries IS 
    'Row-level security policy ensuring users can only access timeseries data from their own tenant';

-- ====================================================================================
-- Usage Examples
-- ====================================================================================

/*
-- Example 1: Set tenant context before querying
SELECT tenant_security.set_current_tenant('tenant-12345');
SELECT * FROM entities WHERE collection_name = 'users';

-- Example 2: Using application user (RLS enforced)
SET ROLE application_user;
SELECT tenant_security.set_current_tenant('tenant-12345');
SELECT * FROM entities; -- Only returns rows for tenant-12345

-- Example 3: Check for cross-tenant access attempts
SELECT * FROM tenant_security.cross_tenant_access_summary;

-- Example 4: View tenant data distribution
SELECT * FROM tenant_security.tenant_data_distribution;

-- Example 5: Clean up old resolved incidents
SELECT tenant_security.cleanup_old_access_logs(30);

-- Example 6: Resolve a cross-tenant access incident
SELECT tenant_security.resolve_access_incident(123, 'False positive - legitimate admin operation');
*/

-- ====================================================================================
-- Migration Complete
-- ====================================================================================

SELECT 'Migration V011 complete: Database-level tenant isolation implemented' as status;
