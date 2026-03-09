-- ============================================================================
-- V4: Extend projects table for L3 Project entity
-- Adds missing columns needed by yappc-domain Project entity (security-focused)
-- ============================================================================

-- Add columns from L3 Project entity that are not in the V1 projects table
ALTER TABLE projects ADD COLUMN IF NOT EXISTS repository_url VARCHAR(1024);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS default_branch VARCHAR(255) DEFAULT 'main';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS language VARCHAR(100);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS settings JSONB;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS last_scan_at TIMESTAMP;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS scan_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- V3 already adds key and archived_at, but add IF NOT EXISTS guard
ALTER TABLE projects ADD COLUMN IF NOT EXISTS key VARCHAR(20);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

-- Index for archived filter
CREATE INDEX IF NOT EXISTS idx_projects_archived ON projects(workspace_id, archived);

-- Index for key lookup
CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_workspace_key ON projects(workspace_id, key) WHERE key IS NOT NULL;
