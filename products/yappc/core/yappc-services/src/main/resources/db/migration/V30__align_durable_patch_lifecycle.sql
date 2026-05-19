-- V30__align_durable_patch_lifecycle.sql
-- Align legacy patch-review tables with the Java durable compile-back repositories.

ALTER TABLE change_plans
    ADD COLUMN IF NOT EXISTS plan_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS base_model_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS target_model_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS operation_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS auto_applicable_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS review_required_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS impact_assessment_json JSONB,
    ADD COLUMN IF NOT EXISTS validation_result_json JSONB;

UPDATE change_plans
SET plan_id = id
WHERE plan_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_change_plans_plan_id'
    ) THEN
        ALTER TABLE change_plans ADD CONSTRAINT uk_change_plans_plan_id UNIQUE (plan_id);
    END IF;
END $$;

ALTER TABLE patch_sets
    ADD COLUMN IF NOT EXISTS patch_set_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS plan_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS preserved_residuals_json JSONB,
    ADD COLUMN IF NOT EXISTS review_required_patches_json JSONB,
    ADD COLUMN IF NOT EXISTS stats_json JSONB,
    ADD COLUMN IF NOT EXISTS applied_by VARCHAR(255);

UPDATE patch_sets
SET patch_set_id = id
WHERE patch_set_id IS NULL;

UPDATE patch_sets
SET plan_id = change_plan_id
WHERE plan_id IS NULL AND change_plan_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_patch_sets_patch_set_id'
    ) THEN
        ALTER TABLE patch_sets ADD CONSTRAINT uk_patch_sets_patch_set_id UNIQUE (patch_set_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS patch_set_patches (
    patch_id              VARCHAR(255) PRIMARY KEY,
    patch_set_id          VARCHAR(255) NOT NULL REFERENCES patch_sets(patch_set_id) ON DELETE CASCADE,
    relative_path         TEXT NOT NULL,
    diff                  TEXT NOT NULL,
    ranges_json           JSONB,
    is_atomic             BOOLEAN NOT NULL DEFAULT TRUE,
    source_change_op_id   VARCHAR(255),
    emitter_id            VARCHAR(255),
    base_checksum         VARCHAR(255),
    target_checksum       VARCHAR(255),
    validation_status     VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE file_patches
    ADD COLUMN IF NOT EXISTS patch_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS diff TEXT,
    ADD COLUMN IF NOT EXISTS ranges_json JSONB,
    ADD COLUMN IF NOT EXISTS is_atomic BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS validation_status VARCHAR(64) DEFAULT 'PENDING';

INSERT INTO patch_set_patches (
    patch_id, patch_set_id, relative_path, diff, ranges_json, is_atomic,
    source_change_op_id, emitter_id, base_checksum, target_checksum, validation_status, created_at
)
SELECT
    COALESCE(fp.patch_id, fp.id::text),
    COALESCE(ps.patch_set_id, fp.patch_set_id),
    fp.relative_path,
    COALESCE(fp.diff, fp.patch_diff),
    fp.ranges_json,
    COALESCE(fp.is_atomic, TRUE),
    fp.source_change_op_id,
    fp.emitter_id,
    fp.base_checksum,
    fp.target_checksum,
    COALESCE(fp.validation_status, 'PENDING'),
    fp.created_at
FROM file_patches fp
LEFT JOIN patch_sets ps ON ps.id = fp.patch_set_id OR ps.patch_set_id = fp.patch_set_id
WHERE COALESCE(fp.diff, fp.patch_diff) IS NOT NULL
ON CONFLICT (patch_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS patch_review_bundles (
    bundle_id       VARCHAR(255) PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    project_id      VARCHAR(255) NOT NULL,
    snapshot_id     VARCHAR(255),
    version_id      VARCHAR(255),
    patch_set_id    VARCHAR(255) REFERENCES patch_sets(patch_set_id) ON DELETE CASCADE,
    status          VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    reviewed_by     VARCHAR(255),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata_json   JSONB
);

INSERT INTO patch_review_bundles (
    bundle_id, tenant_id, project_id, snapshot_id, version_id, patch_set_id,
    status, reviewed_by, reviewed_at, created_at, metadata_json
)
SELECT
    rb.id,
    rb.tenant_id,
    rb.project_id,
    rb.snapshot_id,
    rb.version_id,
    COALESCE(ps.patch_set_id, rb.patch_set_id),
    rb.status,
    rb.reviewed_by,
    rb.reviewed_at,
    rb.created_at,
    rb.metadata_json
FROM review_bundles rb
LEFT JOIN patch_sets ps ON ps.id = rb.patch_set_id OR ps.patch_set_id = rb.patch_set_id
ON CONFLICT (bundle_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS patch_rollback_metadata (
    rollback_id            VARCHAR(255) PRIMARY KEY,
    patch_set_id           VARCHAR(255) NOT NULL REFERENCES patch_sets(patch_set_id) ON DELETE CASCADE,
    original_patch_set_id  VARCHAR(255),
    rollback_patch_set_id  VARCHAR(255),
    rolled_back_by         VARCHAR(255),
    rolled_back_at         TIMESTAMPTZ,
    reason                 TEXT,
    success                BOOLEAN,
    error                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO patch_rollback_metadata (
    rollback_id, patch_set_id, original_patch_set_id, rollback_patch_set_id,
    rolled_back_by, rolled_back_at, reason, success, error, created_at
)
SELECT
    rm.id,
    COALESCE(ps.patch_set_id, rm.patch_set_id),
    rm.original_patch_set_id,
    rm.rollback_patch_set_id,
    rm.rolled_back_by,
    rm.rolled_back_at,
    rm.reason,
    rm.success,
    rm.error,
    rm.created_at
FROM rollback_metadata rm
LEFT JOIN patch_sets ps ON ps.id = rm.patch_set_id OR ps.patch_set_id = rm.patch_set_id
ON CONFLICT (rollback_id) DO NOTHING;

ALTER TABLE patch_jobs
    ADD COLUMN IF NOT EXISTS metadata_json JSONB,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_patch_set_patches_patch_set
ON patch_set_patches(patch_set_id);

CREATE INDEX IF NOT EXISTS idx_patch_review_bundles_scope
ON patch_review_bundles(tenant_id, project_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_rollback_metadata_patch_set
ON patch_rollback_metadata(patch_set_id);
