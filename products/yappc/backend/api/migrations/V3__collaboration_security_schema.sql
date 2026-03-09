-- YAPPC Database Migration: V3 - Collaboration & Security
-- PostgreSQL 16
-- Adds support for Collaboration (Phase 5) and Security (Phase 6)

-- ========== Collaboration ==========

-- Teams
CREATE TABLE IF NOT EXISTS yappc.teams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    organization_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    description TEXT,
    avatar_url TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'DELIVERY', -- DELIVERY, PRODUCT, PLATFORM, OTHER
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE', -- PUBLIC, PRIVATE
    timezone VARCHAR(64),
    working_hours JSONB DEFAULT '{}'::jsonb,
    settings JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_teams_tenant ON yappc.teams(tenant_id);
CREATE INDEX idx_teams_org ON yappc.teams(organization_id);
CREATE TRIGGER trigger_teams_updated_at BEFORE UPDATE ON yappc.teams FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- Team Members
CREATE TABLE IF NOT EXISTS yappc.team_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    team_id UUID NOT NULL REFERENCES yappc.teams(id),
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER', -- OWNER, ADMIN, MEMBER, VIEWER
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(team_id, user_id)
);

CREATE INDEX idx_team_members_user ON yappc.team_members(user_id);

-- Code Reviews
CREATE TABLE IF NOT EXISTS yappc.code_reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID NOT NULL REFERENCES yappc.projects(id),
    story_id UUID REFERENCES yappc.stories(id),
    author_id UUID NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- OPEN, MERGED, CLOSED, DRAFT
    pr_number INTEGER,
    repo_url TEXT,
    reviewers JSONB DEFAULT '[]'::jsonb,
    comments JSONB DEFAULT '[]'::jsonb,
    checks JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_code_reviews_tenant ON yappc.code_reviews(tenant_id);
CREATE INDEX idx_code_reviews_project ON yappc.code_reviews(project_id);
CREATE TRIGGER trigger_code_reviews_updated_at BEFORE UPDATE ON yappc.code_reviews FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- Notifications
CREATE TABLE IF NOT EXISTS yappc.notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL, -- MENTION, ASSIGNMENT, REVIEW, SYSTEM
    title VARCHAR(256) NOT NULL,
    message TEXT,
    link TEXT,
    read BOOLEAN DEFAULT FALSE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON yappc.notifications(user_id);
CREATE INDEX idx_notifications_unread ON yappc.notifications(user_id) WHERE read = FALSE;

-- ========== Security ==========

-- Vulnerabilities
CREATE TABLE IF NOT EXISTS yappc.vulnerabilities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id),
    cve_id VARCHAR(32),
    title VARCHAR(256) NOT NULL,
    description TEXT,
    severity VARCHAR(16) NOT NULL, -- CRITICAL, HIGH, MEDIUM, LOW
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- OPEN, CONFIRMED, FALSE_POSITIVE, RESOLVED
    package_name VARCHAR(128),
    package_version VARCHAR(64),
    fixed_version VARCHAR(64),
    cvss_score NUMERIC(3,1),
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX idx_vulnerabilities_tenant ON yappc.vulnerabilities(tenant_id);
CREATE INDEX idx_vulnerabilities_project ON yappc.vulnerabilities(project_id);
CREATE INDEX idx_vulnerabilities_severity ON yappc.vulnerabilities(severity);

-- Security Scans
CREATE TABLE IF NOT EXISTS yappc.security_scans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID NOT NULL REFERENCES yappc.projects(id),
    type VARCHAR(32) NOT NULL, -- SAST, DAST, DEPENDENCY, CONTAINER
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    summary JSONB DEFAULT '{}'::jsonb, -- { critical: 1, high: 2 ... }
    findings_count INTEGER DEFAULT 0,
    scan_config JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX idx_security_scans_project ON yappc.security_scans(project_id);

-- Compliance Assessments
CREATE TABLE IF NOT EXISTS yappc.compliance_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id),
    framework VARCHAR(32) NOT NULL, -- SOC2, GDPR, HIPAA
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    score INTEGER DEFAULT 0,
    controls JSONB DEFAULT '[]'::jsonb,
    last_assessment_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_compliance_tenant ON yappc.compliance_assessments(tenant_id);

-- Audit Logs
CREATE TABLE IF NOT EXISTS yappc.audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    user_id UUID,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64),
    details JSONB DEFAULT '{}'::jsonb,
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant ON yappc.audit_logs(tenant_id, timestamp);
CREATE INDEX idx_audit_logs_resource ON yappc.audit_logs(resource_type, resource_id);
