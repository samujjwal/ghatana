CREATE TABLE IF NOT EXISTS dmos_suppression (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64),
    workspace_id VARCHAR(128) NOT NULL,
    contact_point_hash VARCHAR(64) NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dmos_suppression_active_contact
    ON dmos_suppression(workspace_id, contact_point_hash)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_dmos_suppression_workspace
    ON dmos_suppression(workspace_id, active);
