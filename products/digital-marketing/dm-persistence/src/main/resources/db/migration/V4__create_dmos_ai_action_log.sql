-- DMOS AI Action Log table
-- Schema migration V4: create dmos_ai_action_log

CREATE TABLE IF NOT EXISTS dmos_ai_action_log (
    action_id         TEXT          NOT NULL,
    workspace_id      TEXT          NOT NULL,
    correlation_id    TEXT          NOT NULL,
    action_type       TEXT          NOT NULL,
    status            TEXT          NOT NULL,
    actor             TEXT          NOT NULL,
    initiated_by_ai   BOOLEAN       NOT NULL DEFAULT FALSE,
    confidence        DOUBLE PRECISION,
    evidence_links    TEXT[]        NOT NULL DEFAULT '{}',
    policy_checks     TEXT[]        NOT NULL DEFAULT '{}',
    summary           TEXT          NOT NULL,
    details           TEXT          NOT NULL,
    related_entity_id TEXT,
    occurred_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT dmos_ai_action_log_pkey PRIMARY KEY (action_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS dmos_ai_action_log_workspace_idx
    ON dmos_ai_action_log (workspace_id);

CREATE INDEX IF NOT EXISTS dmos_ai_action_log_correlation_idx
    ON dmos_ai_action_log (workspace_id, correlation_id);

CREATE INDEX IF NOT EXISTS dmos_ai_action_log_entity_idx
    ON dmos_ai_action_log (workspace_id, related_entity_id)
    WHERE related_entity_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS dmos_ai_action_log_occurred_idx
    ON dmos_ai_action_log (workspace_id, occurred_at DESC);
