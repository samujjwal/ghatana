-- YAPPC API Database Initialization Script
-- PostgreSQL 16

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ========== Schema ==========
CREATE SCHEMA IF NOT EXISTS yappc;

-- ========== Requirements Table ==========
CREATE TABLE IF NOT EXISTS yappc.requirements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'FUNCTIONAL',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    category VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    assigned_to VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    version_number INTEGER DEFAULT 1,
    tags JSONB DEFAULT '[]'::jsonb,
    dependencies JSONB DEFAULT '[]'::jsonb,
    quality_metrics JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    
    CONSTRAINT chk_type CHECK (type IN ('FUNCTIONAL', 'NON_FUNCTIONAL', 'CONSTRAINT', 'INTERFACE', 'SECURITY', 'PERFORMANCE', 'USABILITY', 'COMPLIANCE')),
    CONSTRAINT chk_status CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED', 'REJECTED', 'IMPLEMENTED', 'VERIFIED', 'DEPRECATED')),
    CONSTRAINT chk_priority CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
);

-- Indexes for requirements
CREATE INDEX idx_requirements_tenant ON yappc.requirements(tenant_id);
CREATE INDEX idx_requirements_project ON yappc.requirements(tenant_id, project_id);
CREATE INDEX idx_requirements_status ON yappc.requirements(tenant_id, status);
CREATE INDEX idx_requirements_type ON yappc.requirements(tenant_id, type);
CREATE INDEX idx_requirements_assigned ON yappc.requirements(tenant_id, assigned_to);
CREATE INDEX idx_requirements_created ON yappc.requirements(tenant_id, created_at DESC);
CREATE INDEX idx_requirements_tags ON yappc.requirements USING GIN (tags);

-- ========== AI Suggestions Table ==========
CREATE TABLE IF NOT EXISTS yappc.ai_suggestions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64),
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    title VARCHAR(500),
    content TEXT NOT NULL,
    rationale TEXT,
    source_model VARCHAR(64),
    target_resource_id VARCHAR(128),
    target_resource_type VARCHAR(64),
    confidence DECIMAL(3,2) DEFAULT 0.0,
    priority VARCHAR(16) DEFAULT 'MEDIUM',
    created_by VARCHAR(128) DEFAULT 'AI-SYSTEM',
    reviewed_by VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_notes TEXT,
    tags JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    
    CONSTRAINT chk_ai_type CHECK (type IN ('REQUIREMENT', 'CLARIFICATION', 'REFINEMENT', 'ALTERNATIVE', 'DECOMPOSITION', 'DEPENDENCY', 'EDGE_CASE', 'SECURITY', 'PERFORMANCE', 'TESTABILITY')),
    CONSTRAINT chk_ai_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'DEFERRED', 'APPLIED', 'EXPIRED')),
    CONSTRAINT chk_ai_priority CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_confidence CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

-- Indexes for AI suggestions
CREATE INDEX idx_ai_suggestions_tenant ON yappc.ai_suggestions(tenant_id);
CREATE INDEX idx_ai_suggestions_pending ON yappc.ai_suggestions(tenant_id, status) WHERE status = 'PENDING';
CREATE INDEX idx_ai_suggestions_project ON yappc.ai_suggestions(tenant_id, project_id);
CREATE INDEX idx_ai_suggestions_target ON yappc.ai_suggestions(tenant_id, target_resource_type, target_resource_id);
CREATE INDEX idx_ai_suggestions_confidence ON yappc.ai_suggestions(tenant_id, confidence DESC);
CREATE INDEX idx_ai_suggestions_created ON yappc.ai_suggestions(tenant_id, created_at DESC);

-- ========== Audit Events Table ==========
CREATE TABLE IF NOT EXISTS yappc.audit_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    principal VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    success BOOLEAN DEFAULT TRUE,
    details JSONB DEFAULT '{}'::jsonb,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ip_address INET,
    user_agent TEXT
);

-- Indexes for audit events
CREATE INDEX idx_audit_tenant_time ON yappc.audit_events(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_resource ON yappc.audit_events(tenant_id, resource_type, resource_id);
CREATE INDEX idx_audit_principal ON yappc.audit_events(tenant_id, principal);
CREATE INDEX idx_audit_event_type ON yappc.audit_events(tenant_id, event_type);

-- Partition by month for large scale (optional)
-- CREATE TABLE yappc.audit_events_partitioned (...) PARTITION BY RANGE (timestamp);

-- ========== Entity Versions Table ==========
CREATE TABLE IF NOT EXISTS yappc.entity_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    snapshot JSONB NOT NULL,
    author VARCHAR(128) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(tenant_id, entity_type, entity_id, version_number)
);

-- Indexes for versions
CREATE INDEX idx_versions_entity ON yappc.entity_versions(tenant_id, entity_type, entity_id);
CREATE INDEX idx_versions_latest ON yappc.entity_versions(tenant_id, entity_type, entity_id, version_number DESC);

-- ========== User Permissions Table ==========
CREATE TABLE IF NOT EXISTS yappc.user_permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    persona VARCHAR(64) NOT NULL,
    roles JSONB DEFAULT '[]'::jsonb,
    additional_permissions JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(tenant_id, user_id)
);

-- Index for user permissions
CREATE INDEX idx_user_permissions_tenant_user ON yappc.user_permissions(tenant_id, user_id);

-- ========== Functions ==========

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION yappc.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER trigger_requirements_updated_at
    BEFORE UPDATE ON yappc.requirements
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

CREATE TRIGGER trigger_user_permissions_updated_at
    BEFORE UPDATE ON yappc.user_permissions
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Seed Data for Development ==========

-- Insert sample requirements
INSERT INTO yappc.requirements (tenant_id, project_id, title, description, type, status, priority, created_by)
VALUES 
    ('dev-tenant', 'proj-001', 'User Authentication', 'System must support OAuth2 and SAML authentication', 'SECURITY', 'APPROVED', 'HIGH', 'admin'),
    ('dev-tenant', 'proj-001', 'API Rate Limiting', 'API must enforce rate limits per tenant', 'NON_FUNCTIONAL', 'REVIEW', 'MEDIUM', 'admin'),
    ('dev-tenant', 'proj-001', 'Dashboard Metrics', 'Dashboard must display real-time metrics', 'FUNCTIONAL', 'DRAFT', 'MEDIUM', 'pm-user'),
    ('dev-tenant', 'proj-001', 'Data Export', 'Users must be able to export data to CSV/Excel', 'FUNCTIONAL', 'IMPLEMENTED', 'LOW', 'pm-user'),
    ('dev-tenant', 'proj-001', 'Audit Logging', 'All user actions must be logged for compliance', 'COMPLIANCE', 'VERIFIED', 'CRITICAL', 'security-user');

-- Insert sample AI suggestions
INSERT INTO yappc.ai_suggestions (tenant_id, project_id, type, title, content, rationale, confidence, priority, target_resource_type, target_resource_id)
VALUES 
    ('dev-tenant', 'proj-001', 'CLARIFICATION', 'Clarify Authentication Scope', 'The requirement mentions OAuth2 and SAML but does not specify which identity providers should be supported.', 'Ambiguous scope may lead to incomplete implementation', 0.85, 'HIGH', 'REQUIREMENT', 'req-001'),
    ('dev-tenant', 'proj-001', 'SECURITY', 'Add MFA Requirement', 'Consider adding multi-factor authentication as a security requirement.', 'Industry best practice for authentication systems', 0.92, 'HIGH', 'REQUIREMENT', 'req-001'),
    ('dev-tenant', 'proj-001', 'TESTABILITY', 'Add Acceptance Criteria', 'The rate limiting requirement lacks specific acceptance criteria for testing.', 'Requirements without testable criteria are harder to verify', 0.78, 'MEDIUM', 'REQUIREMENT', 'req-002');

COMMIT;
