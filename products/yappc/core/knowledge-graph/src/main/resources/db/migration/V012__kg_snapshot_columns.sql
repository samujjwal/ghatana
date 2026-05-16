-- V12: Add snapshot tracking columns to kg_nodes and kg_edges
-- Adds content_checksum, snapshot_id, version_id, and is_tombstone for proper snapshot management

-- Add columns to kg_nodes
ALTER TABLE kg_nodes
ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(64),
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS version_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN NOT NULL DEFAULT FALSE;

-- Add columns to kg_edges
ALTER TABLE kg_edges
ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(64),
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS version_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN NOT NULL DEFAULT FALSE;

-- Create indexes for snapshot-related queries
CREATE INDEX IF NOT EXISTS idx_kg_nodes_snapshot_id ON kg_nodes(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_kg_nodes_version_id ON kg_nodes(version_id);
CREATE INDEX IF NOT EXISTS idx_kg_nodes_is_tombstone ON kg_nodes(is_tombstone);
CREATE INDEX IF NOT EXISTS idx_kg_nodes_content_checksum ON kg_nodes(content_checksum);

CREATE INDEX IF NOT EXISTS idx_kg_edges_snapshot_id ON kg_edges(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_kg_edges_version_id ON kg_edges(version_id);
CREATE INDEX IF NOT EXISTS idx_kg_edges_is_tombstone ON kg_edges(is_tombstone);
CREATE INDEX IF NOT EXISTS idx_kg_edges_content_checksum ON kg_edges(content_checksum);

-- Add comments for documentation
COMMENT ON COLUMN kg_nodes.content_checksum IS 'SHA-256 checksum of the node content for integrity verification';
COMMENT ON COLUMN kg_nodes.snapshot_id IS 'Identifier of the source snapshot this node belongs to';
COMMENT ON COLUMN kg_nodes.version_id IS 'Version identifier for tracking node changes over time';
COMMENT ON COLUMN kg_nodes.is_tombstone IS 'Flag indicating if the node is deleted but retained for history';

COMMENT ON COLUMN kg_edges.content_checksum IS 'SHA-256 checksum of the edge content for integrity verification';
COMMENT ON COLUMN kg_edges.snapshot_id IS 'Identifier of the source snapshot this edge belongs to';
COMMENT ON COLUMN kg_edges.version_id IS 'Version identifier for tracking edge changes over time';
COMMENT ON COLUMN kg_edges.is_tombstone IS 'Flag indicating if the edge is deleted but retained for history';
