-- Add durable page artifact operation log storage for replay/export.

ALTER TABLE page_artifacts
    ADD COLUMN IF NOT EXISTS operation_log JSONB;

ALTER TABLE page_artifact_versions
    ADD COLUMN IF NOT EXISTS operation_log JSONB;
