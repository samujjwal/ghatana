-- V27__repository_snapshot_inventory_metadata.sql
-- P1: Add inventory metadata columns to repository_snapshots table
-- This allows the snapshot repository to persist skip reasons, package boundaries, and inventory summary
-- so that ArtifactCompileJobService can use persisted inventory instead of rescanning

-- Add inventory metadata columns
ALTER TABLE repository_snapshots
ADD COLUMN IF NOT EXISTS inventory_metadata_json JSONB;

-- Add comment to document the inventory metadata
COMMENT ON COLUMN repository_snapshots.inventory_metadata_json IS
    'P1: JSONB field containing inventory scan metadata including skip reasons, package boundaries, file counts, and total bytes. 
     Structure: {"skipped": [{"relativePath": "...", "reason": "...", "matchedPattern": "..."}], "packageBoundaries": [...], "fileCounts": {...}, "totalFiles": ..., "totalBytes": ...}';

-- Add index for efficient inventory metadata queries
CREATE INDEX IF NOT EXISTS idx_repository_snapshots_inventory_metadata
ON repository_snapshots USING GIN (inventory_metadata_json);
