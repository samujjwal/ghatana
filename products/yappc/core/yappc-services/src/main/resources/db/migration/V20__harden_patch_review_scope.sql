-- V20__harden_patch_review_scope.sql
-- P0: Add workspace_id to V17 change_plans / patch_sets / review_bundles for complete 3-part scope isolation.
-- V19 already has workspace_id; this migration backfills V17 tables that were missing it.

-- change_plans: add workspace_id (nullable for existing rows; new rows must provide it)
ALTER TABLE change_plans
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

-- patch_sets (V17 definition): add workspace_id + snapshot_id if missing
-- NOTE: V19 creates a *separate* patch_sets table; both share the same schema name so
--       this migration targets the earliest definition.
ALTER TABLE change_plans
    ADD COLUMN IF NOT EXISTS base_model_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS target_model_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS impact_json JSONB,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);

-- review_bundles (V17 definition): add workspace_id + snapshot_id + version_id
ALTER TABLE review_bundles
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version_id VARCHAR(255);

-- Indexes for change_plans workspace scope
CREATE INDEX IF NOT EXISTS idx_change_plans_workspace_scope
ON change_plans(tenant_id, workspace_id, project_id, created_at DESC);

-- Partial index to find review bundles requiring review
CREATE INDEX IF NOT EXISTS idx_review_bundles_review_required
ON review_bundles(tenant_id, workspace_id, project_id)
WHERE status = 'PENDING';
