-- V16__harden_artifact_graph_fidelity.sql
-- Persist all node and edge fidelity fields and unresolved/residual graph lifecycle records.

ALTER TABLE artifact_nodes
ADD COLUMN IF NOT EXISTS source_location_json JSONB,
ADD COLUMN IF NOT EXISTS extractor_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS extractor_version VARCHAR(255),
ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS provenance VARCHAR(128),
ADD COLUMN IF NOT EXISTS privacy_security_flags_json JSONB,
ADD COLUMN IF NOT EXISTS residual_fragment_ids_json JSONB,
ADD COLUMN IF NOT EXISTS source_ref TEXT,
ADD COLUMN IF NOT EXISTS symbol_ref TEXT;

ALTER TABLE artifact_edges
ADD COLUMN IF NOT EXISTS edge_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS bidirectional BOOLEAN,
ADD COLUMN IF NOT EXISTS edge_metadata_json JSONB;

CREATE TABLE IF NOT EXISTS artifact_unresolved_edges (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    snapshot_id           VARCHAR(255),
    source_node_id        VARCHAR(255) NOT NULL,
    target_ref            TEXT NOT NULL,
    relationship          VARCHAR(255) NOT NULL,
    target_kind_hint      VARCHAR(255),
    confidence            DOUBLE PRECISION,
    metadata_json         JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS artifact_edge_resolution_records (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    unresolved_edge_id    VARCHAR(255) NOT NULL REFERENCES artifact_unresolved_edges(id) ON DELETE CASCADE,
    status                VARCHAR(100) NOT NULL,
    resolved_target_id    VARCHAR(255),
    candidate_ids_json    JSONB,
    review_required       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS residual_islands (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    snapshot_id           VARCHAR(255),
    island_type           VARCHAR(255) NOT NULL,
    summary               TEXT NOT NULL,
    file_count            INTEGER NOT NULL DEFAULT 0,
    confidence            DOUBLE PRECISION,
    metadata_json         JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_artifact_unresolved_edges_scope
ON artifact_unresolved_edges(tenant_id, project_id, snapshot_id);

CREATE INDEX IF NOT EXISTS idx_artifact_edge_resolution_scope
ON artifact_edge_resolution_records(tenant_id, project_id, status);

CREATE INDEX IF NOT EXISTS idx_residual_islands_scope
ON residual_islands(tenant_id, project_id, snapshot_id);
