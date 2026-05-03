-- DMOS Optimistic Version Columns
-- Schema migration V5: add version columns for optimistic locking

-- Add version column to dmos_approval_snapshots
ALTER TABLE dmos_approval_snapshots
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add version column to dmos_ai_action_log
ALTER TABLE dmos_ai_action_log
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
