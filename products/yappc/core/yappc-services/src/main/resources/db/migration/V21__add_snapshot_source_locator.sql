-- V21__add_snapshot_source_locator.sql
-- P1: Add source_locator_json column to repository_snapshots table
-- Enables tracking the exact source locator (provider, repo, ref, credential) used for snapshot creation
-- This aligns with RepositorySnapshotRepository's source locator ref persistence

-- Add source_locator_json column to repository_snapshots
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS source_locator_json JSONB;

-- Add checksum column if not present (for consistency with RepositorySnapshotRepository)
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS checksum VARCHAR(255);

-- Add created_by column if not present (for audit trail)
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'system';

-- Add index on source_locator_json for querying by source locator
CREATE INDEX IF NOT EXISTS idx_repository_snapshots_source_locator
ON repository_snapshots USING GIN (source_locator_json);

-- Add index on content_hash for deduplication queries
CREATE INDEX IF NOT EXISTS idx_repository_snapshots_content_hash
ON repository_snapshots(content_hash);

-- Ensure repository_snapshot_files has content_checksum and file_type columns
ALTER TABLE repository_snapshot_files
ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(255);

ALTER TABLE repository_snapshot_files
ADD COLUMN IF NOT EXISTS file_type VARCHAR(100);

-- Add index on content_checksum for file deduplication
CREATE INDEX IF NOT EXISTS idx_repository_snapshot_files_content_checksum
ON repository_snapshot_files(content_checksum);

-- Add index on file_type for filtering by file type
CREATE INDEX IF NOT EXISTS idx_repository_snapshot_files_file_type
ON repository_snapshot_files(file_type);
