-- V14__add_snapshot_tracking_to_artifact_graph.sql
-- P4-4: Add snapshot/version tracking and tombstone support to artifact graph tables
-- Enables incremental upsert, cross-scan diffing, and history preservation

-- Add snapshot tracking columns to artifact_nodes
ALTER TABLE artifact_nodes
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS version_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(64),
ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN NOT NULL DEFAULT FALSE;

-- Add snapshot tracking columns to artifact_edges
ALTER TABLE artifact_edges
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS version_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN NOT NULL DEFAULT FALSE;

-- Create indexes for snapshot-based queries
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_snapshot ON artifact_nodes(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_version ON artifact_nodes(version_id);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_tombstone ON artifact_nodes(is_tombstone);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_checksum ON artifact_nodes(content_checksum);

CREATE INDEX IF NOT EXISTS idx_artifact_edges_snapshot ON artifact_edges(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_version ON artifact_edges(version_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_tombstone ON artifact_edges(is_tombstone);

-- Create composite index for efficient snapshot diff queries
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_snapshot_tenant_project 
ON artifact_nodes(snapshot_id, tenant_id, project_id) 
WHERE is_tombstone = FALSE;

CREATE INDEX IF NOT EXISTS idx_artifact_edges_snapshot_tenant_project 
ON artifact_edges(snapshot_id, tenant_id, project_id) 
WHERE is_tombstone = FALSE;
