-- DMOS Approval Snapshot table
-- Schema migration V3: create dmos_approval_snapshots

CREATE TABLE IF NOT EXISTS dmos_approval_snapshots (
    request_id             TEXT        NOT NULL,
    workspace_id           TEXT        NOT NULL,
    target_type            TEXT        NOT NULL,
    target_id              TEXT        NOT NULL,
    target_workspace_id    TEXT        NOT NULL,
    snapshot_summary       TEXT        NOT NULL,
    validation_result_id   TEXT,
    risk_level             SMALLINT    NOT NULL CHECK (risk_level BETWEEN 1 AND 5),
    required_approver_role TEXT        NOT NULL,
    snapshot_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT dmos_approval_snapshots_pkey PRIMARY KEY (request_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_workspace_idx
    ON dmos_approval_snapshots (workspace_id);
