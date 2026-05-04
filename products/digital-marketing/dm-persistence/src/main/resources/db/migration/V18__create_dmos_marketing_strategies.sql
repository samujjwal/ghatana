-- DMOS Marketing Strategies table
-- Schema migration V18: create dmos_marketing_strategies

CREATE TABLE IF NOT EXISTS dmos_marketing_strategies (
    strategy_id       TEXT        NOT NULL,
    workspace_id      TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    goals             JSONB       NOT NULL,
    channel_plans     JSONB       NOT NULL,
    budget_cap        INTEGER     NOT NULL,
    rationale         TEXT        NOT NULL,
    assumptions       TEXT        NOT NULL,
    measurement_plan  TEXT        NOT NULL,
    content_plan      TEXT        NOT NULL,
    model_version     TEXT        NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL,
    generated_by      TEXT        NOT NULL,
    approved_at       TIMESTAMPTZ,
    approved_by       TEXT,
    CONSTRAINT dmos_marketing_strategies_pkey PRIMARY KEY (strategy_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS dmos_marketing_strategies_workspace_idx ON dmos_marketing_strategies (workspace_id);
CREATE INDEX IF NOT EXISTS dmos_marketing_strategies_status_idx ON dmos_marketing_strategies (status);
CREATE INDEX IF NOT EXISTS dmos_marketing_strategies_generated_at_idx ON dmos_marketing_strategies (generated_at);
