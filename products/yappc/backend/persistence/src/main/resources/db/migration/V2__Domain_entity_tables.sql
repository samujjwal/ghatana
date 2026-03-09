-- YAPPC Domain Entity Tables
-- Migration: V2__Domain_entity_tables.sql
-- Description: Creates tables for yappc-domain JPA entities (cloud security, compliance, incidents)
-- Source of truth: libs/java/yappc-domain model classes

-- ============================================================================
-- DASHBOARDS
-- ============================================================================

CREATE TABLE dashboards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    widget_config JSONB,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_dashboards_workspace ON dashboards(workspace_id);

-- ============================================================================
-- CLOUD ACCOUNTS
-- ============================================================================

CREATE TABLE cloud_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(50),
    external_id VARCHAR(200),
    role_arn VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT true,
    connection_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    last_connected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_cloud_accounts_workspace ON cloud_accounts(workspace_id);
CREATE INDEX idx_cloud_accounts_provider ON cloud_accounts(provider);

-- ============================================================================
-- CLOUD COSTS
-- ============================================================================

CREATE TABLE cloud_costs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    cloud_account_id UUID NOT NULL REFERENCES cloud_accounts(id),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    service_name VARCHAR(100),
    region VARCHAR(50),
    cost_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_cloud_costs_workspace ON cloud_costs(workspace_id);
CREATE INDEX idx_cloud_costs_account ON cloud_costs(cloud_account_id);
CREATE INDEX idx_cloud_costs_date ON cloud_costs(cost_date);

-- ============================================================================
-- CLOUD RESOURCES
-- ============================================================================

CREATE TABLE cloud_resources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    cloud_account_id UUID NOT NULL REFERENCES cloud_accounts(id),
    provider VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    identifier VARCHAR(500) NOT NULL,
    name VARCHAR(255),
    region VARCHAR(50),
    tags JSONB,
    configuration JSONB,
    risk_score INTEGER NOT NULL DEFAULT 0,
    is_public BOOLEAN NOT NULL DEFAULT false,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_cloud_resources_workspace ON cloud_resources(workspace_id);
CREATE INDEX idx_cloud_resources_account ON cloud_resources(cloud_account_id);
CREATE INDEX idx_cloud_resources_type ON cloud_resources(resource_type);

-- ============================================================================
-- COMPLIANCE FRAMEWORKS
-- ============================================================================

CREATE TABLE compliance_frameworks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    description TEXT,
    framework_version VARCHAR(50),
    category VARCHAR(100),
    documentation_url VARCHAR(500),
    enabled_by_default BOOLEAN NOT NULL DEFAULT false,
    is_builtin BOOLEAN NOT NULL DEFAULT false,
    control_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

-- ============================================================================
-- COMPLIANCE ASSESSMENTS
-- ============================================================================

CREATE TABLE compliance_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    framework_id UUID NOT NULL REFERENCES compliance_frameworks(id),
    score INTEGER NOT NULL DEFAULT 0 CHECK (score >= 0 AND score <= 100),
    passed_controls INTEGER NOT NULL DEFAULT 0,
    failed_controls INTEGER NOT NULL DEFAULT 0,
    na_controls INTEGER NOT NULL DEFAULT 0,
    total_controls INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    details JSONB,
    started_at TIMESTAMP WITH TIME ZONE,
    assessed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_compliance_assessments_workspace ON compliance_assessments(workspace_id);
CREATE INDEX idx_compliance_assessments_framework ON compliance_assessments(framework_id);

-- ============================================================================
-- INCIDENTS
-- ============================================================================

CREATE TABLE incidents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority INTEGER NOT NULL DEFAULT 3,
    assignee_id UUID,
    reporter_id UUID,
    category VARCHAR(100),
    root_cause TEXT,
    resolution TEXT,
    detected_at TIMESTAMP WITH TIME ZONE,
    investigation_started_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_incidents_workspace ON incidents(workspace_id);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);

-- ============================================================================
-- SCAN JOBS
-- ============================================================================

CREATE TABLE scan_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    scan_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    config JSONB,
    error_message TEXT,
    findings_count INTEGER NOT NULL DEFAULT 0,
    critical_count INTEGER NOT NULL DEFAULT 0,
    high_count INTEGER NOT NULL DEFAULT 0,
    medium_count INTEGER NOT NULL DEFAULT 0,
    low_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_scan_jobs_workspace ON scan_jobs(workspace_id);
CREATE INDEX idx_scan_jobs_project ON scan_jobs(project_id);
CREATE INDEX idx_scan_jobs_status ON scan_jobs(status);

-- ============================================================================
-- SCAN FINDINGS
-- ============================================================================

CREATE TABLE scan_findings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    scan_job_id UUID NOT NULL REFERENCES scan_jobs(id),
    finding_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location JSONB,
    remediation TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    false_positive BOOLEAN NOT NULL DEFAULT false,
    cwe_id VARCHAR(20),
    cve_id VARCHAR(30),
    cvss_score DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_scan_findings_workspace ON scan_findings(workspace_id);
CREATE INDEX idx_scan_findings_job ON scan_findings(scan_job_id);
CREATE INDEX idx_scan_findings_severity ON scan_findings(severity);

-- ============================================================================
-- SECURITY ALERTS
-- ============================================================================

CREATE TABLE security_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    source VARCHAR(100),
    resource_id UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    acknowledged_by UUID,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_security_alerts_workspace ON security_alerts(workspace_id);
CREATE INDEX idx_security_alerts_status ON security_alerts(status);
CREATE INDEX idx_security_alerts_severity ON security_alerts(severity);

-- ============================================================================
-- DEPENDENCIES
-- ============================================================================

CREATE TABLE dependencies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    ecosystem VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(100) NOT NULL,
    latest_version VARCHAR(100),
    is_direct BOOLEAN NOT NULL DEFAULT true,
    license VARCHAR(100),
    vulnerability_count INTEGER NOT NULL DEFAULT 0,
    max_severity VARCHAR(50),
    is_outdated BOOLEAN NOT NULL DEFAULT false,
    discovered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version_lock INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_dependencies_workspace ON dependencies(workspace_id);
CREATE INDEX idx_dependencies_project ON dependencies(project_id);
CREATE INDEX idx_dependencies_ecosystem ON dependencies(ecosystem);

-- ============================================================================
-- AI SUGGESTIONS (backing api/domain/AISuggestion POJO)
-- ============================================================================

CREATE TABLE ai_suggestions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(id),
    project_id VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    title VARCHAR(500) NOT NULL,
    content TEXT,
    rationale TEXT,
    source_model VARCHAR(100),
    target_resource_id VARCHAR(255),
    target_resource_type VARCHAR(100),
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    created_by VARCHAR(255),
    reviewed_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_notes TEXT,
    tags JSONB DEFAULT '[]',
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_ai_suggestions_tenant ON ai_suggestions(tenant_id);
CREATE INDEX idx_ai_suggestions_project ON ai_suggestions(project_id);
CREATE INDEX idx_ai_suggestions_status ON ai_suggestions(status);

-- ============================================================================
-- TRIGGERS FOR UPDATED_AT ON NEW TABLES
-- ============================================================================

CREATE TRIGGER update_dashboards_updated_at BEFORE UPDATE ON dashboards
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cloud_accounts_updated_at BEFORE UPDATE ON cloud_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cloud_resources_updated_at BEFORE UPDATE ON cloud_resources
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_frameworks_updated_at BEFORE UPDATE ON compliance_frameworks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_assessments_updated_at BEFORE UPDATE ON compliance_assessments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_incidents_updated_at BEFORE UPDATE ON incidents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_scan_jobs_updated_at BEFORE UPDATE ON scan_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_scan_findings_updated_at BEFORE UPDATE ON scan_findings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_security_alerts_updated_at BEFORE UPDATE ON security_alerts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dependencies_updated_at BEFORE UPDATE ON dependencies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
