-- V18__create_artifact_patch_sets.sql
-- Durable patch job orchestration tables for Java-governed patch generation.

-- P1-23: Added patch_jobs table for durable job orchestration
CREATE TABLE IF NOT EXISTS patch_jobs (
    job_id               VARCHAR(255) PRIMARY KEY,
    tenant_id            VARCHAR(255) NOT NULL,
    workspace_id         VARCHAR(255) NOT NULL,
    project_id           VARCHAR(255) NOT NULL,
    plan_id              VARCHAR(255),
    snapshot_id          VARCHAR(255),
    status               VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    progress_percent     INTEGER NOT NULL DEFAULT 0,
    status_message       TEXT,
    patch_set_id         VARCHAR(255),
    metadata_json        JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    CONSTRAINT fk_patch_jobs_patch_set FOREIGN KEY (patch_set_id) REFERENCES patch_sets(id) ON DELETE SET NULL
);

-- P1-23: Added validation_results table for patch validation metadata
CREATE TABLE IF NOT EXISTS validation_results (
    id                   VARCHAR(255) PRIMARY KEY,
    patch_set_id         VARCHAR(255) NOT NULL REFERENCES patch_sets(id) ON DELETE CASCADE,
    validation_type      VARCHAR(100) NOT NULL,
    status               VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    issues_json          JSONB,
    warnings_json        JSONB,
    metrics_json         JSONB,
    validated_by         VARCHAR(255),
    validated_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- P1-23: Added review_decisions table for explicit review decisions
CREATE TABLE IF NOT EXISTS review_decisions (
    id                   VARCHAR(255) PRIMARY KEY,
    review_bundle_id     VARCHAR(255) NOT NULL REFERENCES review_bundles(id) ON DELETE CASCADE,
    reviewer_id          VARCHAR(255) NOT NULL,
    decision             VARCHAR(64) NOT NULL,
    comments             TEXT,
    metadata_json        JSONB,
    decided_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for patch_jobs
CREATE INDEX IF NOT EXISTS idx_patch_jobs_scope
ON patch_jobs(tenant_id, workspace_id, project_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_jobs_plan
ON patch_jobs(plan_id);

CREATE INDEX IF NOT EXISTS idx_patch_jobs_snapshot
ON patch_jobs(snapshot_id);

-- Indexes for validation_results
CREATE INDEX IF NOT EXISTS idx_validation_results_patch_set
ON validation_results(patch_set_id, validation_type);

CREATE INDEX IF NOT EXISTS idx_validation_results_status
ON validation_results(status, validated_at DESC);

-- Indexes for review_decisions
CREATE INDEX IF NOT EXISTS idx_review_decisions_bundle
ON review_decisions(review_bundle_id, decided_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_decisions_reviewer
ON review_decisions(reviewer_id, decided_at DESC);
