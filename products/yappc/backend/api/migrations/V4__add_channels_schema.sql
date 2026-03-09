-- YAPPC Database Migration: V4 - Channels
-- PostgreSQL 16
-- Adds support for Channels (Collaboration Phase 5 Extension)

CREATE TABLE IF NOT EXISTS yappc.channels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    team_id UUID NOT NULL REFERENCES yappc.teams(id),
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC, PRIVATE, DIRECT_MESSAGE
    description TEXT,
    topic TEXT,
    unread_count INTEGER DEFAULT 0, -- Denormalized for MVP, normally calculated per user
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(team_id, name)
);

CREATE INDEX idx_channels_team ON yappc.channels(team_id);
CREATE TRIGGER trigger_channels_updated_at BEFORE UPDATE ON yappc.channels FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

CREATE TABLE IF NOT EXISTS yappc.channel_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    channel_id UUID NOT NULL REFERENCES yappc.channels(id),
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER', -- ADMIN, MEMBER
    last_read_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(channel_id, user_id)
);

CREATE INDEX idx_channel_members_user ON yappc.channel_members(user_id);
