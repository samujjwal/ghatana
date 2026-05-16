-- V11__create_source_import_jobs.sql
-- P2.6: Durable job tables for async source import jobs with progress tracking, audit, and cancellation support
-- NOTE: This migration intentionally overlaps some snapshot/tombstone columns later repeated in V14.
-- Both migrations remain idempotent by design because historical environments may have applied them in different sequences.
-- New schema evolution for artifact graph fidelity should be added in dedicated migrations (for example V16+), not by extending this overlap.

-- Add content_checksum to artifact_nodes for incremental upsert (P4.4)
ALTER TABLE artifact_nodes ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(64);
ALTER TABLE artifact_nodes ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255);
ALTER TABLE artifact_nodes ADD COLUMN IF NOT EXISTS version_id VARCHAR(255);
ALTER TABLE artifact_nodes ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_snapshot ON artifact_nodes(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_version ON artifact_nodes(version_id);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_tombstone ON artifact_nodes(is_tombstone) WHERE is_tombstone = TRUE;

-- Add snapshot/version tracking to artifact_edges (P4.4)
ALTER TABLE artifact_edges ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255);
ALTER TABLE artifact_edges ADD COLUMN IF NOT EXISTS version_id VARCHAR(255);
ALTER TABLE artifact_edges ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_artifact_edges_snapshot ON artifact_edges(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_version ON artifact_edges(version_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_tombstone ON artifact_edges(is_tombstone) WHERE is_tombstone = TRUE;

-- Create source_import_jobs table for durable job storage (P2.6)
CREATE TABLE IF NOT EXISTS source_import_jobs (
    id                      BIGSERIAL   PRIMARY KEY,
    job_id                  VARCHAR(255) NOT NULL UNIQUE,
    project_id              VARCHAR(255) NOT NULL,
    workspace_id            VARCHAR(255) NOT NULL,
    tenant_id               VARCHAR(255) NOT NULL,
    source_url              TEXT        NOT NULL,
    source_type             VARCHAR(100) NOT NULL,
    status                  VARCHAR(50)  NOT NULL,
    current_step            INTEGER     NOT NULL DEFAULT 0,
    total_steps             INTEGER     NOT NULL DEFAULT 5,
    percentage              NUMERIC(5,2) NOT NULL DEFAULT 0,
    current_phase           VARCHAR(100),
    validation_results_json JSONB,
    decompilation_results_json JSONB,
    mapping_results_json    JSONB,
    residual_review_status  VARCHAR(100),
    submitted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    submitted_by            VARCHAR(255) NOT NULL,
    error_message           TEXT,
    metadata_json           JSONB,
    is_cancelled            BOOLEAN     DEFAULT FALSE,
    cancellation_requested_at TIMESTAMPTZ,
    CONSTRAINT valid_status CHECK (
        status IN ('SUBMITTED', 'VALIDATING', 'DECOMPILING', 'MAPPING', 
                   'RESIDUAL_REVIEW_REQUIRED', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT valid_progress CHECK (
        current_step >= 0 AND total_steps >= 1 AND current_step <= total_steps AND
        percentage >= 0 AND percentage <= 100
    )
);

CREATE INDEX IF NOT EXISTS idx_source_jobs_tenant ON source_import_jobs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_source_jobs_project ON source_import_jobs(project_id);
CREATE INDEX IF NOT EXISTS idx_source_jobs_status ON source_import_jobs(status);
CREATE INDEX IF NOT EXISTS idx_source_jobs_submitted ON source_import_jobs(submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_source_jobs_cancelled ON source_import_jobs(is_cancelled) WHERE is_cancelled = TRUE;

-- Create trigger for updated_at on artifact_nodes and artifact_edges
CREATE OR REPLACE FUNCTION update_artifact_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_artifact_nodes_updated_at ON artifact_nodes;
CREATE TRIGGER update_artifact_nodes_updated_at 
    BEFORE UPDATE ON artifact_nodes 
    FOR EACH ROW EXECUTE FUNCTION update_artifact_updated_at();

DROP TRIGGER IF EXISTS update_artifact_edges_updated_at ON artifact_edges;
CREATE TRIGGER update_artifact_edges_updated_at 
    BEFORE UPDATE ON artifact_edges 
    FOR EACH ROW EXECUTE FUNCTION update_artifact_updated_at();
