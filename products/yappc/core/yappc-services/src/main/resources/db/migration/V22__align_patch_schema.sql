-- V22__align_patch_schema.sql
-- P1: Align patch schema with PatchSetRepository expectations
-- Adds missing columns and tables to support full patch lifecycle persistence

-- Add workspace_id to change_plans if not present
ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

-- Add missing columns to change_plans
ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS base_model_id VARCHAR(255);

ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS target_model_id VARCHAR(255);

ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS operation_count INTEGER DEFAULT 0;

ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS auto_applicable_count INTEGER DEFAULT 0;

ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS review_required_count INTEGER DEFAULT 0;

ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS impact_assessment_json JSONB;

ALTER TABLE change_plans
ADD COLUMN IF NOT EXISTS validation_result_json JSONB;

-- Rename id to plan_id if needed (PostgreSQL doesn't support IF EXISTS for rename)
-- This is handled by using plan_id in queries and keeping id as primary key

-- Add workspace_id to patch_sets if not present
ALTER TABLE patch_sets
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

-- Rename change_plan_id to plan_id if needed
-- This is handled by using plan_id in queries

-- Add missing columns to patch_sets
ALTER TABLE patch_sets
ADD COLUMN IF NOT EXISTS preserved_residuals_json JSONB;

ALTER TABLE patch_sets
ADD COLUMN IF NOT EXISTS review_required_patches_json JSONB;

ALTER TABLE patch_sets
ADD COLUMN IF NOT EXISTS stats_json JSONB;

ALTER TABLE patch_sets
ADD COLUMN IF NOT EXISTS applied_by VARCHAR(255);

-- Rename file_patches to patch_set_patches if needed
-- Create a view for backward compatibility if table name differs

-- Add missing columns to file_patches / patch_set_patches
ALTER TABLE file_patches
ADD COLUMN IF NOT EXISTS is_atomic BOOLEAN DEFAULT TRUE;

ALTER TABLE file_patches
ADD COLUMN IF NOT EXISTS validation_status VARCHAR(64) DEFAULT 'PENDING';

-- Add missing columns to review_bundles
ALTER TABLE review_bundles
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255);

ALTER TABLE review_bundles
ADD COLUMN IF NOT EXISTS version_id VARCHAR(255);

-- Update rollback_metadata schema to match repository expectations
ALTER TABLE rollback_metadata
ADD COLUMN IF NOT EXISTS original_patch_set_id VARCHAR(255);

ALTER TABLE rollback_metadata
ADD COLUMN IF NOT EXISTS rollback_patch_set_id VARCHAR(255);

ALTER TABLE rollback_metadata
ADD COLUMN IF NOT EXISTS rolled_back_by VARCHAR(255);

ALTER TABLE rollback_metadata
ADD COLUMN IF NOT EXISTS reason TEXT;

ALTER TABLE rollback_metadata
ADD COLUMN IF NOT EXISTS success BOOLEAN;

ALTER TABLE rollback_metadata
ADD COLUMN IF NOT EXISTS error TEXT;

-- Create indexes for new columns
CREATE INDEX IF NOT EXISTS idx_change_plans_workspace
ON change_plans(workspace_id);

CREATE INDEX IF NOT EXISTS idx_change_plans_models
ON change_plans(base_model_id, target_model_id);

CREATE INDEX IF NOT EXISTS idx_patch_sets_workspace
ON patch_sets(workspace_id);

CREATE INDEX IF NOT EXISTS idx_patch_sets_plan
ON patch_sets(plan_id);

CREATE INDEX IF NOT EXISTS idx_file_patches_validation
ON file_patches(validation_status);

CREATE INDEX IF NOT EXISTS idx_review_bundles_snapshot
ON review_bundles(snapshot_id, version_id);
