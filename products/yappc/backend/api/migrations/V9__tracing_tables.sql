-- YAPPC Database Migration: V9 - Distributed Tracing Tables
-- PostgreSQL 16
-- Adds support for distributed tracing (traces and spans)

-- ========== Traces Table ==========
CREATE TABLE IF NOT EXISTS yappc.traces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    trace_id VARCHAR(64) NOT NULL,
    name VARCHAR(256),
    service VARCHAR(128),
    operation VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    spans JSONB DEFAULT '[]'::jsonb,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT DEFAULT 0,
    user_id VARCHAR(128),
    request_id VARCHAR(128),
    tags JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_trace_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ERROR'))
);

CREATE INDEX idx_traces_tenant ON yappc.traces(tenant_id);
CREATE INDEX idx_traces_project ON yappc.traces(tenant_id, project_id);
CREATE INDEX idx_traces_trace_id ON yappc.traces(tenant_id, trace_id);
CREATE INDEX idx_traces_service ON yappc.traces(tenant_id, service);
CREATE INDEX idx_traces_status ON yappc.traces(tenant_id, status);
CREATE INDEX idx_traces_start_time ON yappc.traces(start_time DESC);
CREATE INDEX idx_traces_duration ON yappc.traces(duration_ms DESC);
CREATE INDEX idx_traces_user ON yappc.traces(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_traces_request ON yappc.traces(request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_traces_tags ON yappc.traces USING GIN (tags);

CREATE TRIGGER trigger_traces_updated_at
    BEFORE UPDATE ON yappc.traces
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();
