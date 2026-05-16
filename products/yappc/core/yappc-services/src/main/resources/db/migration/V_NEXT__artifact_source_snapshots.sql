-- P0: Migration for durable source import jobs and repository snapshots
-- This enables Java-canonical source acquisition with full tenant/workspace/project isolation

-- ============================================================================
-- Source Import Jobs
-- ============================================================================
CREATE TABLE IF NOT EXISTS source_import_jobs (
    -- Primary key
    job_id VARCHAR(255) NOT NULL,

    -- Scope isolation (composite key component)
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,

    -- Source locator fields
    provider VARCHAR(100) NOT NULL,
    repo_id VARCHAR(500) NOT NULL,
    ref VARCHAR(500),
    path VARCHAR(500),
    credential_ref VARCHAR(255),

    -- Job status
    status VARCHAR(50) NOT NULL,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    current_step VARCHAR(255),
    error_message TEXT,

    -- Result
    snapshot_id VARCHAR(255),

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,

    -- Serialized locator for reconstruction
    locator_json TEXT,

    -- Composite primary key
    PRIMARY KEY (tenant_id, job_id),

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING')),
    CONSTRAINT chk_progress CHECK (progress_percent >= 0 AND progress_percent <= 100)
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_source_import_jobs_workspace
    ON source_import_jobs (tenant_id, workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_source_import_jobs_project
    ON source_import_jobs (tenant_id, workspace_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_source_import_jobs_status
    ON source_import_jobs (tenant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_source_import_jobs_snapshot
    ON source_import_jobs (tenant_id, snapshot_id);

-- ============================================================================
-- Repository Snapshots
-- ============================================================================
CREATE TABLE IF NOT EXISTS repository_snapshots (
    -- Primary key
    snapshot_id VARCHAR(255) NOT NULL,

    -- Scope isolation (composite key component)
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,

    -- Source metadata
    provider VARCHAR(100) NOT NULL,
    repo_id VARCHAR(500) NOT NULL,
    commit_sha VARCHAR(255),
    content_hash VARCHAR(255),

    -- Materialization
    materialized_root VARCHAR(1000) NOT NULL,
    checksum VARCHAR(255) NOT NULL,
    file_count INTEGER NOT NULL DEFAULT 0,

    -- Diagnostics (JSON array)
    diagnostics_json TEXT,

    -- Timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Composite primary key
    PRIMARY KEY (tenant_id, snapshot_id)
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_repository_snapshots_workspace
    ON repository_snapshots (tenant_id, workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_project
    ON repository_snapshots (tenant_id, workspace_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_repo
    ON repository_snapshots (tenant_id, provider, repo_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_commit
    ON repository_snapshots (tenant_id, provider, repo_id, commit_sha);

-- ============================================================================
-- Repository Snapshot Files
-- ============================================================================
CREATE TABLE IF NOT EXISTS repository_snapshot_files (
    -- Foreign key to snapshot (partitioned by tenant)
    snapshot_id VARCHAR(255) NOT NULL,

    -- File metadata
    relative_path VARCHAR(1000) NOT NULL,
    absolute_path VARCHAR(1000) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    last_modified TIMESTAMP NOT NULL,
    content_checksum VARCHAR(255),

    -- Tenant for partition pruning
    tenant_id VARCHAR(255) NOT NULL,

    -- Composite primary key
    PRIMARY KEY (snapshot_id, relative_path),

    -- Foreign key (enforced at application level for performance)
    -- Note: ON DELETE CASCADE would require referential integrity
    -- which is handled by the repository layer
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_snapshot_files_tenant
    ON repository_snapshot_files (tenant_id, snapshot_id);

-- ============================================================================
-- Artifact Compile Runs (for tracking full pipeline executions)
-- ============================================================================
CREATE TABLE IF NOT EXISTS artifact_compile_runs (
    -- Primary key
    run_id VARCHAR(255) NOT NULL,

    -- Scope isolation
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,

    -- Source reference
    import_job_id VARCHAR(255),
    snapshot_id VARCHAR(255),

    -- Run metadata
    status VARCHAR(50) NOT NULL,
    trigger_type VARCHAR(50) NOT NULL,
    initiator_id VARCHAR(255),

    -- Results
    graph_version_id VARCHAR(255),
    model_version_id VARCHAR(255),
    error_message TEXT,

    -- Summary statistics (JSON)
    summary_json TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Composite primary key
    PRIMARY KEY (tenant_id, run_id),

    -- Constraints
    CONSTRAINT chk_compile_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_compile_runs_workspace
    ON artifact_compile_runs (tenant_id, workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compile_runs_project
    ON artifact_compile_runs (tenant_id, workspace_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compile_runs_status
    ON artifact_compile_runs (tenant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compile_runs_snapshot
    ON artifact_compile_runs (tenant_id, snapshot_id);

-- ============================================================================
-- Artifact Patch Sets (for compile-back operations)
-- ============================================================================
CREATE TABLE IF NOT EXISTS artifact_patch_sets (
    -- Primary key
    patch_set_id VARCHAR(255) NOT NULL,

    -- Scope isolation
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,

    -- Source references
    compile_run_id VARCHAR(255),
    model_version_id VARCHAR(255),

    -- Patch metadata
    status VARCHAR(50) NOT NULL,
    base_snapshot_id VARCHAR(255),
    target_snapshot_id VARCHAR(255),

    -- Patch content (JSON array of patches)
    patches_json TEXT NOT NULL,

    -- Review state
    review_status VARCHAR(50),
    reviewed_by VARCHAR(255),
    review_notes TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    applied_at TIMESTAMP,

    -- Composite primary key
    PRIMARY KEY (tenant_id, patch_set_id),

    -- Constraints
    CONSTRAINT chk_patch_status CHECK (status IN ('PENDING', 'REVIEW_REQUIRED', 'APPROVED', 'REJECTED', 'APPLIED', 'ROLLED_BACK')),
    CONSTRAINT chk_review_status CHECK (review_status IS NULL OR review_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_patch_sets_workspace
    ON artifact_patch_sets (tenant_id, workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_sets_project
    ON artifact_patch_sets (tenant_id, workspace_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_sets_status
    ON artifact_patch_sets (tenant_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_sets_compile_run
    ON artifact_patch_sets (tenant_id, compile_run_id);

-- ============================================================================
-- Comments
-- ============================================================================
COMMENT ON TABLE source_import_jobs IS 'Durable source import job records with tenant/workspace/project scope';
COMMENT ON TABLE repository_snapshots IS 'Immutable repository snapshot metadata for deterministic inventory';
COMMENT ON TABLE repository_snapshot_files IS 'File-level metadata for repository snapshots';
COMMENT ON TABLE artifact_compile_runs IS 'Full compile pipeline execution records';
COMMENT ON TABLE artifact_patch_sets IS 'Compile-back patch sets for review and application';
