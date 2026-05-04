-- DMOS Budget Recommendations table
-- Schema migration V19: create dmos_budget_recommendations

CREATE TABLE IF NOT EXISTS dmos_budget_recommendations (
    recommendation_id TEXT        NOT NULL,
    workspace_id      TEXT        NOT NULL,
    monthly_budget    INTEGER     NOT NULL,
    channel_split     JSONB       NOT NULL,
    daily_caps        JSONB       NOT NULL,
    risk_level        SMALLINT    NOT NULL CHECK (risk_level BETWEEN 1 AND 5),
    rationale         TEXT        NOT NULL,
    assumptions       TEXT        NOT NULL,
    model_version     TEXT        NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL,
    generated_by      TEXT        NOT NULL,
    CONSTRAINT dmos_budget_recommendations_pkey PRIMARY KEY (recommendation_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS dmos_budget_recommendations_workspace_idx ON dmos_budget_recommendations (workspace_id);
CREATE INDEX IF NOT EXISTS dmos_budget_recommendations_generated_at_idx ON dmos_budget_recommendations (generated_at);
