-- Artifact Compiler Database Schema Migration
-- Version: 15.0.0
-- P1-11: Creates tables for artifact graph persistence, snapshots, inventory, and patch review

-- Repository snapshots table
CREATE TABLE IF NOT EXISTS repository_snapshots (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    provider VARCHAR(50) NOT NULL,
    repo_id VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(255),
    branch VARCHAR(255),
    repository_root TEXT NOT NULL,
    scanned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_checksum VARCHAR(64),
    workspace_id VARCHAR(36) REFERENCES workspaces(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, repo_id, commit_sha)
);

CREATE INDEX idx_repo_snapshots_provider ON repository_snapshots(provider);
CREATE INDEX idx_repo_snapshots_workspace ON repository_snapshots(workspace_id);
CREATE INDEX idx_repo_snapshots_scanned_at ON repository_snapshots(scanned_at DESC);

-- Artifact inventory items table
CREATE TABLE IF NOT EXISTS artifact_inventory_items (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    snapshot_id VARCHAR(36) REFERENCES repository_snapshots(id) ON DELETE CASCADE,
    artifact_id VARCHAR(255) NOT NULL,
    relative_path TEXT NOT NULL,
    absolute_path TEXT NOT NULL,
    kind VARCHAR(100) NOT NULL,
    language VARCHAR(50),
    framework VARCHAR(50),
    is_generated BOOLEAN DEFAULT FALSE,
    is_binary BOOLEAN DEFAULT FALSE,
    checksum VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    last_modified_at TIMESTAMP WITH TIME ZONE,
    source_file_ref TEXT,
    content_checksum VARCHAR(64),
    classification_confidence DECIMAL(3,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(snapshot_id, artifact_id)
);

CREATE INDEX idx_inventory_snapshot ON artifact_inventory_items(snapshot_id);
CREATE INDEX idx_inventory_kind ON artifact_inventory_items(kind);
CREATE INDEX idx_inventory_language ON artifact_inventory_items(language);
CREATE INDEX idx_inventory_checksum ON artifact_inventory_items(checksum);

-- Artifact unresolved edges table
CREATE TABLE IF NOT EXISTS artifact_unresolved_edges (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    snapshot_id VARCHAR(36) REFERENCES repository_snapshots(id) ON DELETE CASCADE,
    source_node_id VARCHAR(255) NOT NULL,
    target_ref TEXT NOT NULL,
    relationship VARCHAR(100) NOT NULL,
    target_kind_hint VARCHAR(100),
    confidence DECIMAL(3,2),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_unresolved_snapshot ON artifact_unresolved_edges(snapshot_id);
CREATE INDEX idx_unresolved_source ON artifact_unresolved_edges(source_node_id);
CREATE INDEX idx_unresolved_relationship ON artifact_unresolved_edges(relationship);

-- Artifact edge resolution records table
CREATE TABLE IF NOT EXISTS artifact_edge_resolution_records (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    unresolved_edge_id VARCHAR(36) REFERENCES artifact_unresolved_edges(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    resolved_target_id VARCHAR(255),
    candidate_ids TEXT[],
    review_required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_resolution_unresolved ON artifact_edge_resolution_records(unresolved_edge_id);
CREATE INDEX idx_resolution_status ON artifact_edge_resolution_records(status);
CREATE INDEX idx_resolution_review ON artifact_edge_resolution_records(review_required) WHERE review_required = TRUE;

-- Residual islands table
CREATE TABLE IF NOT EXISTS residual_islands (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    snapshot_id VARCHAR(36) REFERENCES repository_snapshots(id) ON DELETE CASCADE,
    island_type VARCHAR(100) NOT NULL,
    summary TEXT NOT NULL,
    file_count INT NOT NULL DEFAULT 0,
    confidence DECIMAL(3,2),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_residual_snapshot ON residual_islands(snapshot_id);
CREATE INDEX idx_residual_type ON residual_islands(island_type);

-- Patch sets table
CREATE TABLE IF NOT EXISTS patch_sets (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    snapshot_id VARCHAR(36) REFERENCES repository_snapshots(id) ON DELETE CASCADE,
    version_id VARCHAR(36),
    content_checksum VARCHAR(64),
    patch_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    applied_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'PENDING'
);

CREATE INDEX idx_patch_snapshot ON patch_sets(snapshot_id);
CREATE INDEX idx_patch_version ON patch_sets(version_id);
CREATE INDEX idx_patch_status ON patch_sets(status);
CREATE INDEX idx_patch_created_at ON patch_sets(created_at DESC);

-- Review bundles table
CREATE TABLE IF NOT EXISTS review_bundles (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    patch_set_id VARCHAR(36) REFERENCES patch_sets(id) ON DELETE CASCADE,
    bundle_type VARCHAR(100) NOT NULL,
    summary TEXT NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    review_required BOOLEAN DEFAULT TRUE,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    approved BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_patch_set ON review_bundles(patch_set_id);
CREATE INDEX idx_review_type ON review_bundles(bundle_type);
CREATE INDEX idx_review_required ON review_bundles(review_required) WHERE review_required = TRUE;
CREATE INDEX idx_review_status ON review_bundles(approved);
