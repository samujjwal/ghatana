-- V17__repository_snapshots_and_inventory.sql
-- P0: Add repository snapshots and source file inventory tables for canonical durable file identity.
-- Enables tracking file identity independent of snapshot-specific node IDs, supporting
-- drift detection, incremental updates, and cross-snapshot file correlation.

-- Add workspace_id to artifact_nodes table if not already present
ALTER TABLE artifact_nodes
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

-- Add workspace_id to artifact_edges table if not already present
ALTER TABLE artifact_edges
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

-- Create repository_snapshots table to track repository-level snapshots
CREATE TABLE IF NOT EXISTS repository_snapshots (
    snapshot_id              VARCHAR(255) PRIMARY KEY,
    tenant_id               VARCHAR(255) NOT NULL,
    workspace_id            VARCHAR(255) NOT NULL,
    project_id              VARCHAR(255) NOT NULL,
    provider                VARCHAR(100) NOT NULL,
    repository_id           TEXT NOT NULL,
    commit_sha              VARCHAR(255),
    content_hash            VARCHAR(255) NOT NULL,
    materialized_root       TEXT,
    file_count              INTEGER NOT NULL DEFAULT 0,
    total_bytes             BIGINT NOT NULL DEFAULT 0,
    diagnostics_json        JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_repository_snapshot UNIQUE (tenant_id, workspace_id, project_id, repository_id, commit_sha)
);

-- Create source_file_refs table for canonical durable file identity
CREATE TABLE IF NOT EXISTS source_file_refs (
    file_ref_id              VARCHAR(255) PRIMARY KEY,
    repository_id            TEXT NOT NULL,
    relative_path            TEXT NOT NULL,
    content_hash             VARCHAR(255) NOT NULL,
    size_bytes               BIGINT NOT NULL,
    file_type                VARCHAR(100),
    language                 VARCHAR(100),
    last_modified_at         TIMESTAMPTZ,
    first_seen_snapshot_id   VARCHAR(255) NOT NULL,
    last_seen_snapshot_id    VARCHAR(255) NOT NULL,
    metadata_json            JSONB,
    tenant_id                VARCHAR(255) NOT NULL,
    workspace_id             VARCHAR(255) NOT NULL,
    project_id               VARCHAR(255) NOT NULL,
    CONSTRAINT uk_source_file_ref UNIQUE (repository_id, relative_path, content_hash)
);

-- Create repository_inventory table for file classification and inventory tracking
CREATE TABLE IF NOT EXISTS repository_inventory (
    id                       SERIAL PRIMARY KEY,
    snapshot_id              VARCHAR(255) NOT NULL REFERENCES repository_snapshots(snapshot_id) ON DELETE CASCADE,
    file_ref_id              VARCHAR(255) NOT NULL REFERENCES source_file_refs(file_ref_id) ON DELETE CASCADE,
    file_type                VARCHAR(100) NOT NULL,
    classification           VARCHAR(100),
    is_ignored              BOOLEAN NOT NULL DEFAULT FALSE,
    is_binary               BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json            JSONB,
    tenant_id                VARCHAR(255) NOT NULL,
    workspace_id             VARCHAR(255) NOT NULL,
    project_id               VARCHAR(255) NOT NULL
);

-- Update artifact_nodes to include workspace_id in existing records (nullable for existing data)
UPDATE artifact_nodes SET workspace_id = 'default' WHERE workspace_id IS NULL;

-- Update artifact_edges to include workspace_id in existing records (nullable for existing data)
UPDATE artifact_edges SET workspace_id = 'default' WHERE workspace_id IS NULL;

-- Create indexes for repository_snapshots
CREATE INDEX IF NOT EXISTS idx_repository_snapshots_scope
ON repository_snapshots(tenant_id, workspace_id, project_id);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_repository
ON repository_snapshots(repository_id, commit_sha);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_created_at
ON repository_snapshots(created_at DESC);

-- Create indexes for source_file_refs
CREATE INDEX IF NOT EXISTS idx_source_file_refs_scope
ON source_file_refs(tenant_id, workspace_id, project_id);

CREATE INDEX IF NOT EXISTS idx_source_file_refs_repository
ON source_file_refs(repository_id, relative_path);

CREATE INDEX IF NOT EXISTS idx_source_file_refs_content_hash
ON source_file_refs(content_hash);

CREATE INDEX IF NOT EXISTS idx_source_file_refs_first_seen
ON source_file_refs(first_seen_snapshot_id);

CREATE INDEX IF NOT EXISTS idx_source_file_refs_last_seen
ON source_file_refs(last_seen_snapshot_id);

-- Create indexes for repository_inventory
CREATE INDEX IF NOT EXISTS idx_repository_inventory_scope
ON repository_inventory(tenant_id, workspace_id, project_id);

CREATE INDEX IF NOT EXISTS idx_repository_inventory_snapshot
ON repository_inventory(snapshot_id);

CREATE INDEX IF NOT EXISTS idx_repository_inventory_file_ref
ON repository_inventory(file_ref_id);

CREATE INDEX IF NOT EXISTS idx_repository_inventory_file_type
ON repository_inventory(file_type, classification);

-- Update artifact_nodes index to include workspace_id
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_scope
ON artifact_nodes(tenant_id, workspace_id, project_id, is_tombstone);

-- Update artifact_edges index to include workspace_id
CREATE INDEX IF NOT EXISTS idx_artifact_edges_scope
ON artifact_edges(tenant_id, workspace_id, project_id, is_tombstone);

-- Update artifact_unresolved_edges index to include workspace_id
CREATE INDEX IF NOT EXISTS idx_artifact_unresolved_edges_scope
ON artifact_unresolved_edges(tenant_id, workspace_id, project_id, snapshot_id);

-- Update residual_islands index to include workspace_id
CREATE INDEX IF NOT EXISTS idx_residual_islands_scope
ON residual_islands(tenant_id, workspace_id, project_id, snapshot_id);
