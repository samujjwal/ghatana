-- DMOS Campaign table
-- Schema migration V1: create dmos_campaigns

CREATE TABLE IF NOT EXISTS dmos_campaigns (
    id           TEXT        NOT NULL,
    workspace_id TEXT        NOT NULL,
    name         TEXT        NOT NULL,
    status       TEXT        NOT NULL,
    type         TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    created_by   TEXT        NOT NULL,
    CONSTRAINT dmos_campaigns_pkey PRIMARY KEY (id, workspace_id)
);

CREATE INDEX IF NOT EXISTS dmos_campaigns_workspace_idx ON dmos_campaigns (workspace_id);
