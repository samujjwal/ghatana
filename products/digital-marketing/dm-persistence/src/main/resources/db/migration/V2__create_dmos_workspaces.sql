-- DMOS Workspace table
-- Schema migration V2: create dmos_workspaces

CREATE TABLE IF NOT EXISTS dmos_workspaces (
    id          TEXT        NOT NULL PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    description TEXT        NOT NULL DEFAULT '',
    status      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  TEXT        NOT NULL
);

CREATE INDEX IF NOT EXISTS dmos_workspaces_tenant_idx ON dmos_workspaces (tenant_id);
