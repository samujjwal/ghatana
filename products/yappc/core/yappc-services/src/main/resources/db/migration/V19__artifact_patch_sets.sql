-- V19__artifact_patch_sets.sql
-- P1-23: Add database tables for artifact patch sets and patch jobs
-- Enables durable storage for patch generation, validation, review, and application

-- Create patch_sets table for storing generated patch sets
CREATE TABLE IF NOT EXISTS patch_sets (
    patch_set_id            VARCHAR(255) PRIMARY KEY,
    tenant_id               VARCHAR(255) NOT NULL,
    workspace_id            VARCHAR(255) NOT NULL,
    project_id              VARCHAR(255) NOT NULL,
    plan_id                 VARCHAR(255),
    snapshot_id             VARCHAR(255),
    status                  VARCHAR(100) NOT NULL DEFAULT 'PENDING',
    preserved_residuals      TEXT[],
    review_required_patches  TEXT[],
    total_patches           INTEGER NOT NULL DEFAULT 0,
    auto_applicable         INTEGER NOT NULL DEFAULT 0,
    requires_review         INTEGER NOT NULL DEFAULT 0,
    conflicted              INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255),
    applied_at              TIMESTAMPTZ,
    applied_by              VARCHAR(255),
    metadata_json           JSONB,
    CONSTRAINT uk_patch_set UNIQUE (tenant_id, workspace_id, project_id, plan_id)
);

-- Create file_patches table for individual file patches
CREATE TABLE IF NOT EXISTS file_patches (
    patch_id                VARCHAR(255) PRIMARY KEY,
    patch_set_id            VARCHAR(255) NOT NULL REFERENCES patch_sets(patch_set_id) ON DELETE CASCADE,
    relative_path           TEXT NOT NULL,
    diff                    TEXT NOT NULL,
    is_atomic               BOOLEAN NOT NULL DEFAULT FALSE,
    source_change_op_id     VARCHAR(255),
    emitter_id              VARCHAR(255),
    base_checksum           VARCHAR(255),
    target_checksum         VARCHAR(255),
    validation_status       VARCHAR(100) NOT NULL DEFAULT 'PENDING',
    start_line              INTEGER,
    start_column            INTEGER,
    end_line                INTEGER,
    end_column              INTEGER,
    node_type               VARCHAR(255),
    metadata_json           JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create patch_jobs table for durable patch job orchestration
CREATE TABLE IF NOT EXISTS patch_jobs (
    job_id                  VARCHAR(255) PRIMARY KEY,
    tenant_id               VARCHAR(255) NOT NULL,
    workspace_id            VARCHAR(255) NOT NULL,
    project_id              VARCHAR(255) NOT NULL,
    plan_id                 VARCHAR(255),
    snapshot_id             VARCHAR(255),
    status                  VARCHAR(100) NOT NULL DEFAULT 'PENDING',
    progress_percent        INTEGER NOT NULL DEFAULT 0,
    status_message           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    patch_set_id            VARCHAR(255) REFERENCES patch_sets(patch_set_id),
    metadata_json           JSONB
);

-- Create review_bundles table for patch review workflow
CREATE TABLE IF NOT EXISTS review_bundles (
    bundle_id               VARCHAR(255) PRIMARY KEY,
    tenant_id               VARCHAR(255) NOT NULL,
    workspace_id            VARCHAR(255) NOT NULL,
    project_id              VARCHAR(255) NOT NULL,
    snapshot_id             VARCHAR(255),
    version_id              VARCHAR(255),
    patch_set_id            VARCHAR(255) REFERENCES patch_sets(patch_set_id),
    status                  VARCHAR(100) NOT NULL DEFAULT 'PENDING',
    reviewed_by             VARCHAR(255),
    reviewed_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata_json           JSONB
);

-- Create indexes for patch_sets
CREATE INDEX IF NOT EXISTS idx_patch_sets_scope
ON patch_sets(tenant_id, workspace_id, project_id);

CREATE INDEX IF NOT EXISTS idx_patch_sets_status
ON patch_sets(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_sets_plan
ON patch_sets(plan_id);

-- Create indexes for file_patches
CREATE INDEX IF NOT EXISTS idx_file_patches_patch_set
ON file_patches(patch_set_id);

CREATE INDEX IF NOT EXISTS idx_file_patches_path
ON file_patches(relative_path);

CREATE INDEX IF NOT EXISTS idx_file_patches_validation
ON file_patches(validation_status);

-- Create indexes for patch_jobs
CREATE INDEX IF NOT EXISTS idx_patch_jobs_scope
ON patch_jobs(tenant_id, workspace_id, project_id);

CREATE INDEX IF NOT EXISTS idx_patch_jobs_status
ON patch_jobs(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_jobs_plan
ON patch_jobs(plan_id);

-- Create indexes for review_bundles
CREATE INDEX IF NOT EXISTS idx_review_bundles_scope
ON review_bundles(tenant_id, workspace_id, project_id);

CREATE INDEX IF NOT EXISTS idx_review_bundles_status
ON review_bundles(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_bundles_patch_set
ON review_bundles(patch_set_id);
