-- YAPPC Database Migration: V7 - Extended Collaboration Tables
-- PostgreSQL 16
-- Adds support for Phase 5: Collaboration (activity feed, documents, integrations)

-- ========== Activity Feed Table ==========
CREATE TABLE IF NOT EXISTS yappc.activity_feed (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES yappc.users(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    activity_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    changes JSONB,
    metadata JSONB DEFAULT '{}'::jsonb,
    visibility VARCHAR(16) DEFAULT 'TEAM',
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    team_id UUID REFERENCES yappc.teams(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_activity_action CHECK (action IN (
        'CREATED', 'UPDATED', 'DELETED', 'COMMENTED', 'ASSIGNED', 
        'COMPLETED', 'APPROVED', 'REJECTED', 'MERGED', 'DEPLOYED'
    )),
    CONSTRAINT chk_activity_visibility CHECK (visibility IN ('PRIVATE', 'TEAM', 'PROJECT', 'PUBLIC'))
);

CREATE INDEX idx_activity_feed_tenant ON yappc.activity_feed(tenant_id);
CREATE INDEX idx_activity_feed_user ON yappc.activity_feed(user_id, created_at DESC);
CREATE INDEX idx_activity_feed_actor ON yappc.activity_feed(actor_id, created_at DESC);
CREATE INDEX idx_activity_feed_project ON yappc.activity_feed(project_id, created_at DESC);
CREATE INDEX idx_activity_feed_team ON yappc.activity_feed(team_id, created_at DESC);
CREATE INDEX idx_activity_feed_entity ON yappc.activity_feed(entity_type, entity_id);
CREATE INDEX idx_activity_feed_type ON yappc.activity_feed(tenant_id, activity_type, created_at DESC);

-- ========== Documents Table ==========
CREATE TABLE IF NOT EXISTS yappc.documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    team_id UUID REFERENCES yappc.teams(id) ON DELETE CASCADE,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    content_type VARCHAR(32) NOT NULL DEFAULT 'MARKDOWN',
    version INTEGER DEFAULT 1,
    parent_id UUID REFERENCES yappc.documents(id) ON DELETE SET NULL,
    author_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    last_edited_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    visibility VARCHAR(16) DEFAULT 'TEAM',
    tags JSONB DEFAULT '[]'::jsonb,
    attachments JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_document_content_type CHECK (content_type IN ('MARKDOWN', 'HTML', 'PLAIN_TEXT', 'RICH_TEXT')),
    CONSTRAINT chk_document_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'DELETED')),
    CONSTRAINT chk_document_visibility CHECK (visibility IN ('PRIVATE', 'TEAM', 'PROJECT', 'PUBLIC'))
);

CREATE INDEX idx_documents_tenant ON yappc.documents(tenant_id);
CREATE INDEX idx_documents_project ON yappc.documents(project_id);
CREATE INDEX idx_documents_team ON yappc.documents(team_id);
CREATE INDEX idx_documents_author ON yappc.documents(author_id);
CREATE INDEX idx_documents_status ON yappc.documents(tenant_id, status);
CREATE INDEX idx_documents_title ON yappc.documents(tenant_id, title);
CREATE INDEX idx_documents_tags ON yappc.documents USING GIN(tags);

CREATE TRIGGER trigger_documents_updated_at 
    BEFORE UPDATE ON yappc.documents 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Document Versions Table ==========
CREATE TABLE IF NOT EXISTS yappc.document_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES yappc.documents(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    content TEXT NOT NULL,
    author_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    change_summary TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(document_id, version)
);

CREATE INDEX idx_document_versions_document ON yappc.document_versions(document_id, version DESC);

-- ========== Integrations Table ==========
CREATE TABLE IF NOT EXISTS yappc.integrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    team_id UUID REFERENCES yappc.teams(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    config JSONB NOT NULL,
    credentials JSONB,
    webhook_url TEXT,
    webhook_secret VARCHAR(256),
    enabled BOOLEAN DEFAULT TRUE,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_integration_provider CHECK (provider IN (
        'GITHUB', 'GITLAB', 'BITBUCKET', 'JIRA', 'SLACK', 'DISCORD', 
        'TEAMS', 'ZOOM', 'GOOGLE_WORKSPACE', 'AWS', 'GCP', 'AZURE', 'OTHER'
    )),
    CONSTRAINT chk_integration_type CHECK (type IN (
        'SOURCE_CONTROL', 'ISSUE_TRACKING', 'CHAT', 'VIDEO', 
        'CLOUD_PROVIDER', 'CI_CD', 'MONITORING', 'OTHER'
    )),
    CONSTRAINT chk_integration_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ERROR', 'DELETED'))
);

CREATE INDEX idx_integrations_tenant ON yappc.integrations(tenant_id);
CREATE INDEX idx_integrations_project ON yappc.integrations(project_id);
CREATE INDEX idx_integrations_team ON yappc.integrations(team_id);
CREATE INDEX idx_integrations_provider ON yappc.integrations(tenant_id, provider);
CREATE INDEX idx_integrations_status ON yappc.integrations(tenant_id, status) WHERE enabled = TRUE;

CREATE TRIGGER trigger_integrations_updated_at 
    BEFORE UPDATE ON yappc.integrations 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Integration Events Table ==========
CREATE TABLE IF NOT EXISTS yappc.integration_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    integration_id UUID NOT NULL REFERENCES yappc.integrations(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_source VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP WITH TIME ZONE,
    error TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_integration_events_integration ON yappc.integration_events(integration_id, created_at DESC);
CREATE INDEX idx_integration_events_unprocessed ON yappc.integration_events(tenant_id, processed) WHERE processed = FALSE;
CREATE INDEX idx_integration_events_type ON yappc.integration_events(tenant_id, event_type, created_at DESC);

-- ========== Chat Messages Table (for team chat) ==========
CREATE TABLE IF NOT EXISTS yappc.chat_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    channel_id UUID NOT NULL REFERENCES yappc.channels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    message_type VARCHAR(32) DEFAULT 'TEXT',
    parent_id UUID REFERENCES yappc.chat_messages(id) ON DELETE SET NULL,
    edited BOOLEAN DEFAULT FALSE,
    edited_at TIMESTAMP WITH TIME ZONE,
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    reactions JSONB DEFAULT '[]'::jsonb,
    attachments JSONB DEFAULT '[]'::jsonb,
    mentions JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_message_type CHECK (message_type IN ('TEXT', 'CODE', 'FILE', 'IMAGE', 'SYSTEM'))
);

CREATE INDEX idx_chat_messages_channel ON yappc.chat_messages(channel_id, created_at DESC);
CREATE INDEX idx_chat_messages_user ON yappc.chat_messages(user_id);
CREATE INDEX idx_chat_messages_parent ON yappc.chat_messages(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_chat_messages_mentions ON yappc.chat_messages USING GIN(mentions);

-- ========== Permissions Table ==========
CREATE TABLE IF NOT EXISTS yappc.permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id UUID NOT NULL,
    permission VARCHAR(64) NOT NULL,
    granted BOOLEAN DEFAULT TRUE,
    granted_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_subject_type CHECK (subject_type IN ('USER', 'TEAM', 'ROLE')),
    CONSTRAINT chk_permission CHECK (permission IN (
        'READ', 'WRITE', 'DELETE', 'ADMIN', 
        'MANAGE_MEMBERS', 'MANAGE_SETTINGS', 'MANAGE_INTEGRATIONS'
    )),
    UNIQUE(tenant_id, resource_type, resource_id, subject_type, subject_id, permission)
);

CREATE INDEX idx_permissions_resource ON yappc.permissions(resource_type, resource_id);
CREATE INDEX idx_permissions_subject ON yappc.permissions(subject_type, subject_id);
CREATE INDEX idx_permissions_tenant ON yappc.permissions(tenant_id);
