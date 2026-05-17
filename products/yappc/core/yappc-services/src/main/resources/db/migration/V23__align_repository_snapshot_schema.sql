-- V23__align_repository_snapshot_schema.sql
-- Fix-forward alignment for repository snapshot persistence schema.
-- Ensures repository_snapshots and repository_snapshot_files match RepositorySnapshotRepository expectations
-- across fresh and upgraded databases.

-- Canonical snapshot key expected by repository code.
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255);

-- Backfill snapshot_id from legacy id where needed.
UPDATE repository_snapshots
SET snapshot_id = id
WHERE snapshot_id IS NULL AND id IS NOT NULL;

-- Canonical repository identifier expected by repository code.
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS repo_id TEXT;

UPDATE repository_snapshots
SET repo_id = repository_id
WHERE repo_id IS NULL AND repository_id IS NOT NULL;

-- Keep both columns in sync for mixed-history environments.
UPDATE repository_snapshots
SET repository_id = repo_id
WHERE repository_id IS NULL AND repo_id IS NOT NULL;

-- Canonical filesystem root field expected by repository code.
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS materialized_root TEXT;

UPDATE repository_snapshots
SET materialized_root = local_root_path
WHERE materialized_root IS NULL AND local_root_path IS NOT NULL;

-- Canonical checksum field expected by repository code.
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS checksum VARCHAR(255);

UPDATE repository_snapshots
SET checksum = content_checksum
WHERE checksum IS NULL AND content_checksum IS NOT NULL;

-- Canonical content hash field used for dedup queries.
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(255);

-- Ensure audit and source-locator columns are present.
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT 'system';

ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS source_locator_json JSONB;

-- Ensure canonical lookup and conflict target are available.
CREATE UNIQUE INDEX IF NOT EXISTS uk_repository_snapshots_snapshot_id
ON repository_snapshots(snapshot_id);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_repo_id
ON repository_snapshots(repo_id);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_content_hash
ON repository_snapshots(content_hash);

-- Snapshot files table alignment.
ALTER TABLE repository_snapshot_files
ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(255);

ALTER TABLE repository_snapshot_files
ADD COLUMN IF NOT EXISTS file_type VARCHAR(100);

-- Ensure legacy foreign key rows always reference canonical snapshot IDs.
UPDATE repository_snapshot_files files
SET snapshot_id = snapshots.snapshot_id
FROM repository_snapshots snapshots
WHERE files.snapshot_id = snapshots.id
  AND snapshots.snapshot_id IS NOT NULL
  AND files.snapshot_id <> snapshots.snapshot_id;
