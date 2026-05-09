-- Add provenance metadata columns to AI action log for traceability

ALTER TABLE dmos_ai_action_log
    ADD COLUMN provider TEXT,
    ADD COLUMN model_version TEXT,
    ADD COLUMN human_edited BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS dmos_ai_action_log_provider_idx
    ON dmos_ai_action_log (workspace_id, provider)
    WHERE provider IS NOT NULL;