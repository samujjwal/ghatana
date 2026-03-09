-- YAPPC Database Migration: V6 - Operations Tables
-- PostgreSQL 16
-- Adds support for Phase 4: Operations (monitoring, incidents, alerts, metrics)

-- ========== Metrics Table ==========
CREATE TABLE IF NOT EXISTS yappc.metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    metric_name VARCHAR(128) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    value NUMERIC(20, 6) NOT NULL,
    unit VARCHAR(32),
    tags JSONB DEFAULT '{}'::jsonb,
    dimensions JSONB DEFAULT '{}'::jsonb,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_metric_type CHECK (metric_type IN ('COUNTER', 'GAUGE', 'HISTOGRAM', 'SUMMARY'))
);

CREATE INDEX idx_metrics_tenant ON yappc.metrics(tenant_id);
CREATE INDEX idx_metrics_project ON yappc.metrics(project_id);
CREATE INDEX idx_metrics_name ON yappc.metrics(tenant_id, metric_name, timestamp DESC);
CREATE INDEX idx_metrics_timestamp ON yappc.metrics(timestamp DESC);
CREATE INDEX idx_metrics_tags ON yappc.metrics USING GIN(tags);

-- Partition by month for better performance
-- CREATE TABLE yappc.metrics_2026_01 PARTITION OF yappc.metrics
--     FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

-- ========== Log Entries Table ==========
CREATE TABLE IF NOT EXISTS yappc.log_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    level VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    source VARCHAR(128),
    service VARCHAR(128),
    trace_id VARCHAR(64),
    span_id VARCHAR(64),
    user_id UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    context JSONB DEFAULT '{}'::jsonb,
    stack_trace TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_log_level CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'))
);

CREATE INDEX idx_log_entries_tenant ON yappc.log_entries(tenant_id);
CREATE INDEX idx_log_entries_project ON yappc.log_entries(project_id);
CREATE INDEX idx_log_entries_level ON yappc.log_entries(tenant_id, level, timestamp DESC);
CREATE INDEX idx_log_entries_timestamp ON yappc.log_entries(timestamp DESC);
CREATE INDEX idx_log_entries_trace ON yappc.log_entries(trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX idx_log_entries_context ON yappc.log_entries USING GIN(context);

-- ========== Incidents Table ==========
CREATE TABLE IF NOT EXISTS yappc.incidents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    assigned_to UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    reported_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    source VARCHAR(64),
    affected_services JSONB DEFAULT '[]'::jsonb,
    impact_description TEXT,
    root_cause TEXT,
    resolution TEXT,
    tags JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_incident_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_incident_priority CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_incidents_tenant ON yappc.incidents(tenant_id);
CREATE INDEX idx_incidents_project ON yappc.incidents(project_id);
CREATE INDEX idx_incidents_status ON yappc.incidents(tenant_id, status);
CREATE INDEX idx_incidents_severity ON yappc.incidents(tenant_id, severity);
CREATE INDEX idx_incidents_assigned ON yappc.incidents(assigned_to) WHERE assigned_to IS NOT NULL;
CREATE INDEX idx_incidents_detected ON yappc.incidents(detected_at DESC);

CREATE TRIGGER trigger_incidents_updated_at 
    BEFORE UPDATE ON yappc.incidents 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Incident Events Table ==========
CREATE TABLE IF NOT EXISTS yappc.incident_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID NOT NULL REFERENCES yappc.incidents(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    user_id UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    description TEXT NOT NULL,
    old_value JSONB,
    new_value JSONB,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_incident_event_type CHECK (event_type IN (
        'CREATED', 'ACKNOWLEDGED', 'ASSIGNED', 'UPDATED', 
        'COMMENT_ADDED', 'STATUS_CHANGED', 'RESOLVED', 'CLOSED', 'REOPENED'
    ))
);

CREATE INDEX idx_incident_events_incident ON yappc.incident_events(incident_id, created_at DESC);
CREATE INDEX idx_incident_events_user ON yappc.incident_events(user_id) WHERE user_id IS NOT NULL;

-- ========== Alerts Table ==========
CREATE TABLE IF NOT EXISTS yappc.alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    alert_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    condition JSONB NOT NULL,
    threshold JSONB,
    notification_channels JSONB DEFAULT '[]'::jsonb,
    enabled BOOLEAN DEFAULT TRUE,
    muted BOOLEAN DEFAULT FALSE,
    muted_until TIMESTAMP WITH TIME ZONE,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    trigger_count INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_alert_type CHECK (alert_type IN ('METRIC', 'LOG', 'UPTIME', 'ERROR_RATE', 'CUSTOM')),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    CONSTRAINT chk_alert_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE INDEX idx_alerts_tenant ON yappc.alerts(tenant_id);
CREATE INDEX idx_alerts_project ON yappc.alerts(project_id);
CREATE INDEX idx_alerts_status ON yappc.alerts(tenant_id, status) WHERE enabled = TRUE;
CREATE INDEX idx_alerts_severity ON yappc.alerts(tenant_id, severity) WHERE enabled = TRUE;

CREATE TRIGGER trigger_alerts_updated_at 
    BEFORE UPDATE ON yappc.alerts 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Alert Events Table ==========
CREATE TABLE IF NOT EXISTS yappc.alert_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id UUID NOT NULL REFERENCES yappc.alerts(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    value NUMERIC(20, 6),
    threshold NUMERIC(20, 6),
    context JSONB DEFAULT '{}'::jsonb,
    incident_id UUID REFERENCES yappc.incidents(id) ON DELETE SET NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_alert_event_type CHECK (event_type IN ('TRIGGERED', 'RESOLVED', 'ACKNOWLEDGED', 'ESCALATED')),
    CONSTRAINT chk_alert_event_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'))
);

CREATE INDEX idx_alert_events_alert ON yappc.alert_events(alert_id, created_at DESC);
CREATE INDEX idx_alert_events_incident ON yappc.alert_events(incident_id) WHERE incident_id IS NOT NULL;
CREATE INDEX idx_alert_events_unacknowledged ON yappc.alert_events(tenant_id, acknowledged) WHERE acknowledged = FALSE;

-- ========== Performance Profiles Table ==========
CREATE TABLE IF NOT EXISTS yappc.performance_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    profile_type VARCHAR(32) NOT NULL,
    endpoint VARCHAR(256),
    operation VARCHAR(128),
    duration_ms INTEGER NOT NULL,
    cpu_usage NUMERIC(5, 2),
    memory_usage_mb INTEGER,
    database_queries INTEGER,
    database_time_ms INTEGER,
    cache_hits INTEGER,
    cache_misses INTEGER,
    trace_id VARCHAR(64),
    span_id VARCHAR(64),
    metadata JSONB DEFAULT '{}'::jsonb,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_profile_type CHECK (profile_type IN ('HTTP', 'DATABASE', 'CACHE', 'BACKGROUND_JOB', 'CUSTOM'))
);

CREATE INDEX idx_performance_profiles_tenant ON yappc.performance_profiles(tenant_id);
CREATE INDEX idx_performance_profiles_project ON yappc.performance_profiles(project_id);
CREATE INDEX idx_performance_profiles_endpoint ON yappc.performance_profiles(tenant_id, endpoint, timestamp DESC);
CREATE INDEX idx_performance_profiles_duration ON yappc.performance_profiles(duration_ms DESC);
CREATE INDEX idx_performance_profiles_timestamp ON yappc.performance_profiles(timestamp DESC);

-- ========== Cost Data Table ==========
CREATE TABLE IF NOT EXISTS yappc.cost_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    resource_name VARCHAR(256),
    provider VARCHAR(32) NOT NULL,
    service VARCHAR(64) NOT NULL,
    region VARCHAR(64),
    cost_amount NUMERIC(12, 4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    usage_quantity NUMERIC(20, 6),
    usage_unit VARCHAR(32),
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    tags JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_cost_provider CHECK (provider IN ('AWS', 'GCP', 'AZURE', 'DIGITALOCEAN', 'HEROKU', 'OTHER')),
    CONSTRAINT chk_cost_currency CHECK (currency IN ('USD', 'EUR', 'GBP', 'INR', 'JPY'))
);

CREATE INDEX idx_cost_data_tenant ON yappc.cost_data(tenant_id);
CREATE INDEX idx_cost_data_project ON yappc.cost_data(project_id);
CREATE INDEX idx_cost_data_period ON yappc.cost_data(tenant_id, billing_period_start, billing_period_end);
CREATE INDEX idx_cost_data_provider ON yappc.cost_data(tenant_id, provider, service);
CREATE INDEX idx_cost_data_tags ON yappc.cost_data USING GIN(tags);
