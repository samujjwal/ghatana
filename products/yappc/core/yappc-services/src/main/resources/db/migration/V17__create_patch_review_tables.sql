-- V17__create_patch_review_tables.sql
-- Durable patch review lifecycle tables.

CREATE TABLE IF NOT EXISTS change_plans (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    snapshot_id           VARCHAR(255),
    version_id            VARCHAR(255),
    model_checksum        VARCHAR(64),
    change_ops_json       JSONB NOT NULL,
    created_by            VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS patch_sets (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    change_plan_id        VARCHAR(255) REFERENCES change_plans(id) ON DELETE SET NULL,
    snapshot_id           VARCHAR(255),
    version_id            VARCHAR(255),
    base_checksum         VARCHAR(64),
    target_checksum       VARCHAR(64),
    status                VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_by            VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at            TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS file_patches (
    id                    BIGSERIAL PRIMARY KEY,
    patch_set_id          VARCHAR(255) NOT NULL REFERENCES patch_sets(id) ON DELETE CASCADE,
    relative_path         TEXT NOT NULL,
    patch_diff            TEXT NOT NULL,
    ranges_json           JSONB,
    emitter_id            VARCHAR(255),
    source_change_op_id   VARCHAR(255),
    base_checksum         VARCHAR(64),
    target_checksum       VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS review_bundles (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    patch_set_id          VARCHAR(255) NOT NULL REFERENCES patch_sets(id) ON DELETE CASCADE,
    status                VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    review_required       BOOLEAN NOT NULL DEFAULT TRUE,
    reviewed_by           VARCHAR(255),
    reviewed_at           TIMESTAMPTZ,
    metadata_json         JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rollback_metadata (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    patch_set_id          VARCHAR(255) NOT NULL REFERENCES patch_sets(id) ON DELETE CASCADE,
    original_files_json   JSONB NOT NULL,
    checksums_json        JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rolled_back_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_change_plans_scope
ON change_plans(tenant_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_patch_sets_scope
ON patch_sets(tenant_id, project_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_file_patches_patch_set
ON file_patches(patch_set_id);

CREATE INDEX IF NOT EXISTS idx_review_bundles_scope
ON review_bundles(tenant_id, project_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rollback_metadata_patch_set
ON rollback_metadata(patch_set_id);
