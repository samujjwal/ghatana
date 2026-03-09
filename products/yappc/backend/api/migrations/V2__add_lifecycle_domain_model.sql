-- YAPPC Database Migration: V2 - Add Bootstrapping, Projects, Sprints, Stories
-- PostgreSQL 16
-- This migration adds support for the full YAPPC lifecycle domain model

-- ========== Workspaces Table ==========
CREATE TABLE IF NOT EXISTS yappc.workspaces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    owner_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    members JSONB DEFAULT '[]'::jsonb,
    settings JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    
    CONSTRAINT chk_workspace_type CHECK (type IN ('STANDARD', 'PERSONAL', 'TEAM', 'ENTERPRISE')),
    CONSTRAINT chk_workspace_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED', 'SUSPENDED'))
);

-- Indexes for workspaces
CREATE INDEX idx_workspaces_tenant ON yappc.workspaces(tenant_id);
CREATE INDEX idx_workspaces_owner ON yappc.workspaces(tenant_id, owner_id);
CREATE INDEX idx_workspaces_status ON yappc.workspaces(tenant_id, status);
CREATE INDEX idx_workspaces_name ON yappc.workspaces(tenant_id, name);

-- Trigger for updated_at
CREATE TRIGGER trigger_workspaces_updated_at
    BEFORE UPDATE ON yappc.workspaces
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Bootstrapping Sessions Table ==========
CREATE TABLE IF NOT EXISTS yappc.bootstrapping_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    workspace_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    initial_idea TEXT,
    user_profile JSONB,
    project_hints JSONB,
    organization_context JSONB,
    collaboration_settings JSONB,
    project_definition JSONB,
    project_graph JSONB,
    validation_report JSONB,
    conversation_history JSONB DEFAULT '[]'::jsonb,
    transition_data JSONB,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    approved_at TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_bootstrapping_status CHECK (status IN (
        'CREATED', 'CONVERSING', 'PLANNING', 'VALIDATING', 
        'APPROVED', 'REJECTED', 'ABANDONED'
    ))
);

-- Indexes for bootstrapping sessions
CREATE INDEX idx_bootstrapping_tenant ON yappc.bootstrapping_sessions(tenant_id);
CREATE INDEX idx_bootstrapping_user ON yappc.bootstrapping_sessions(tenant_id, user_id);
CREATE INDEX idx_bootstrapping_status ON yappc.bootstrapping_sessions(tenant_id, status);
CREATE INDEX idx_bootstrapping_active ON yappc.bootstrapping_sessions(tenant_id, user_id, status) 
    WHERE status NOT IN ('APPROVED', 'ABANDONED', 'REJECTED');
CREATE INDEX idx_bootstrapping_inactive ON yappc.bootstrapping_sessions(tenant_id, last_activity_at)
    WHERE status NOT IN ('APPROVED', 'ABANDONED', 'REJECTED');

-- Trigger for updated_at
CREATE TRIGGER trigger_bootstrapping_sessions_updated_at
    BEFORE UPDATE ON yappc.bootstrapping_sessions
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Projects Table ==========
CREATE TABLE IF NOT EXISTS yappc.projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id UUID NOT NULL REFERENCES yappc.workspaces(id),
    bootstrapping_session_id UUID REFERENCES yappc.bootstrapping_sessions(id),
    project_key VARCHAR(32) NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNING',
    type VARCHAR(32) NOT NULL DEFAULT 'SOFTWARE',
    start_date DATE,
    target_date DATE,
    owner_id VARCHAR(128) NOT NULL,
    target_users JSONB DEFAULT '[]'::jsonb,
    tech_stack JSONB DEFAULT '{}'::jsonb,
    tags JSONB DEFAULT '[]'::jsonb,
    settings JSONB DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_project_status CHECK (status IN (
        'PLANNING', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED', 'CANCELLED'
    )),
    CONSTRAINT chk_project_type CHECK (type IN (
        'SOFTWARE', 'MOBILE', 'WEB', 'API', 'DATA', 'ML', 'INFRASTRUCTURE', 'OTHER'
    )),
    UNIQUE(tenant_id, project_key)
);

-- Indexes for projects
CREATE INDEX idx_projects_tenant ON yappc.projects(tenant_id);
CREATE INDEX idx_projects_workspace ON yappc.projects(tenant_id, workspace_id);
CREATE INDEX idx_projects_key ON yappc.projects(tenant_id, project_key);
CREATE INDEX idx_projects_status ON yappc.projects(tenant_id, status);
CREATE INDEX idx_projects_owner ON yappc.projects(tenant_id, owner_id);
CREATE INDEX idx_projects_bootstrapping ON yappc.projects(bootstrapping_session_id) WHERE bootstrapping_session_id IS NOT NULL;
CREATE INDEX idx_projects_tags ON yappc.projects USING GIN (tags);

-- Trigger for updated_at
CREATE TRIGGER trigger_projects_updated_at
    BEFORE UPDATE ON yappc.projects
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Sprints Table ==========
CREATE TABLE IF NOT EXISTS yappc.sprints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID NOT NULL REFERENCES yappc.projects(id),
    sprint_number INTEGER NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNING',
    start_date DATE,
    end_date DATE,
    goals JSONB DEFAULT '[]'::jsonb,
    planned_velocity INTEGER,
    actual_velocity INTEGER,
    retrospective JSONB,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_sprint_status CHECK (status IN (
        'PLANNING', 'ACTIVE', 'COMPLETED', 'CANCELLED'
    )),
    UNIQUE(project_id, sprint_number)
);

-- Indexes for sprints
CREATE INDEX idx_sprints_tenant ON yappc.sprints(tenant_id);
CREATE INDEX idx_sprints_project ON yappc.sprints(tenant_id, project_id);
CREATE INDEX idx_sprints_status ON yappc.sprints(tenant_id, status);
CREATE INDEX idx_sprints_current ON yappc.sprints(project_id, status) WHERE status = 'ACTIVE';

-- Trigger for updated_at
CREATE TRIGGER trigger_sprints_updated_at
    BEFORE UPDATE ON yappc.sprints
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Stories Table ==========
CREATE TABLE IF NOT EXISTS yappc.stories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID NOT NULL REFERENCES yappc.projects(id),
    sprint_id UUID REFERENCES yappc.sprints(id),
    story_key VARCHAR(32) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'FEATURE',
    status VARCHAR(32) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    story_points INTEGER,
    assigned_to JSONB DEFAULT '[]'::jsonb,
    tasks JSONB DEFAULT '[]'::jsonb,
    acceptance_criteria JSONB DEFAULT '[]'::jsonb,
    pull_request JSONB,
    blocked_by JSONB DEFAULT '[]'::jsonb,
    blocks JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_story_type CHECK (type IN (
        'FEATURE', 'BUG', 'TECH_DEBT', 'SPIKE', 'ENHANCEMENT', 'CHORE'
    )),
    CONSTRAINT chk_story_status CHECK (status IN (
        'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'TESTING', 'DONE', 'BLOCKED', 'CANCELLED'
    )),
    CONSTRAINT chk_story_priority CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    UNIQUE(tenant_id, story_key)
);

-- Indexes for stories
CREATE INDEX idx_stories_tenant ON yappc.stories(tenant_id);
CREATE INDEX idx_stories_project ON yappc.stories(tenant_id, project_id);
CREATE INDEX idx_stories_sprint ON yappc.stories(sprint_id) WHERE sprint_id IS NOT NULL;
CREATE INDEX idx_stories_backlog ON yappc.stories(tenant_id, project_id) WHERE sprint_id IS NULL AND status NOT IN ('DONE', 'CANCELLED');
CREATE INDEX idx_stories_key ON yappc.stories(tenant_id, story_key);
CREATE INDEX idx_stories_status ON yappc.stories(tenant_id, status);
CREATE INDEX idx_stories_blocked ON yappc.stories(tenant_id) WHERE status = 'BLOCKED';
CREATE INDEX idx_stories_assigned ON yappc.stories USING GIN (assigned_to);

-- Trigger for updated_at
CREATE TRIGGER trigger_stories_updated_at
    BEFORE UPDATE ON yappc.stories
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Personas Table ==========
CREATE TABLE IF NOT EXISTS yappc.personas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id),
    name VARCHAR(128) NOT NULL,
    role VARCHAR(64) NOT NULL,
    description TEXT,
    goals JSONB DEFAULT '[]'::jsonb,
    pain_points JSONB DEFAULT '[]'::jsonb,
    behaviors JSONB DEFAULT '[]'::jsonb,
    demographics JSONB DEFAULT '{}'::jsonb,
    is_template BOOLEAN DEFAULT FALSE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for personas
CREATE INDEX idx_personas_tenant ON yappc.personas(tenant_id);
CREATE INDEX idx_personas_project ON yappc.personas(tenant_id, project_id);
CREATE INDEX idx_personas_template ON yappc.personas(tenant_id) WHERE is_template = TRUE;

-- Trigger for updated_at
CREATE TRIGGER trigger_personas_updated_at
    BEFORE UPDATE ON yappc.personas
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Workflows Table ==========
CREATE TABLE IF NOT EXISTS yappc.workflows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id),
    name VARCHAR(256) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'USER_FLOW',
    steps JSONB DEFAULT '[]'::jsonb,
    is_template BOOLEAN DEFAULT FALSE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for workflows
CREATE INDEX idx_workflows_tenant ON yappc.workflows(tenant_id);
CREATE INDEX idx_workflows_project ON yappc.workflows(tenant_id, project_id);
CREATE INDEX idx_workflows_type ON yappc.workflows(tenant_id, type);
CREATE INDEX idx_workflows_template ON yappc.workflows(tenant_id) WHERE is_template = TRUE;

-- Trigger for updated_at
CREATE TRIGGER trigger_workflows_updated_at
    BEFORE UPDATE ON yappc.workflows
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Views for Common Queries ==========

-- Active projects with sprint counts
CREATE OR REPLACE VIEW yappc.v_active_projects AS
SELECT 
    p.id,
    p.tenant_id,
    p.project_key,
    p.name,
    p.status,
    p.owner_id,
    COUNT(DISTINCT s.id) FILTER (WHERE s.status = 'ACTIVE') AS active_sprints,
    COUNT(DISTINCT s.id) AS total_sprints,
    COUNT(DISTINCT st.id) FILTER (WHERE st.status NOT IN ('DONE', 'CANCELLED')) AS open_stories,
    p.created_at
FROM yappc.projects p
LEFT JOIN yappc.sprints s ON s.project_id = p.id
LEFT JOIN yappc.stories st ON st.project_id = p.id
WHERE p.status IN ('PLANNING', 'ACTIVE')
GROUP BY p.id;

-- Sprint progress summary
CREATE OR REPLACE VIEW yappc.v_sprint_progress AS
SELECT 
    s.id AS sprint_id,
    s.tenant_id,
    s.project_id,
    s.name AS sprint_name,
    s.status,
    s.planned_velocity,
    COALESCE(SUM(st.story_points) FILTER (WHERE st.status = 'DONE'), 0) AS completed_points,
    COALESCE(SUM(st.story_points), 0) AS total_points,
    COUNT(st.id) FILTER (WHERE st.status = 'DONE') AS completed_stories,
    COUNT(st.id) AS total_stories,
    COUNT(st.id) FILTER (WHERE st.status = 'BLOCKED') AS blocked_stories
FROM yappc.sprints s
LEFT JOIN yappc.stories st ON st.sprint_id = s.id
GROUP BY s.id;

-- ========== Seed Data for Development ==========

-- Insert sample workspace
INSERT INTO yappc.workspaces (tenant_id, name, description, type, owner_id)
VALUES 
    ('dev-tenant', 'Development Workspace', 'Default development workspace', 'STANDARD', 'admin')
ON CONFLICT DO NOTHING;

-- Note: Further seed data can be added via application-level seeding

COMMIT;
