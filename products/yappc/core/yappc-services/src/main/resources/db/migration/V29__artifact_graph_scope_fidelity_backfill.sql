-- Align artifact graph fidelity tables with repository writes.
-- V16/V17 introduced unresolved/residual fidelity tables, but later repository
-- code writes workspace-scoped unresolved/resolution rows and full residual
-- source fidelity fields.

ALTER TABLE artifact_unresolved_edges
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

ALTER TABLE artifact_edge_resolution_records
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);

ALTER TABLE residual_islands
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS original_source TEXT,
    ADD COLUMN IF NOT EXISTS source_location_json JSONB,
    ADD COLUMN IF NOT EXISTS source_span TEXT,
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(255),
    ADD COLUMN IF NOT EXISTS raw_fragment_ref TEXT,
    ADD COLUMN IF NOT EXISTS reason TEXT,
    ADD COLUMN IF NOT EXISTS review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS risk_score DOUBLE PRECISION DEFAULT 0.0;

UPDATE artifact_unresolved_edges
SET workspace_id = 'legacy-workspace'
WHERE workspace_id IS NULL;

UPDATE artifact_edge_resolution_records
SET workspace_id = 'legacy-workspace'
WHERE workspace_id IS NULL;

UPDATE residual_islands
SET workspace_id = 'legacy-workspace'
WHERE workspace_id IS NULL;

UPDATE residual_islands
SET original_source = COALESCE(original_source, summary, '')
WHERE original_source IS NULL;

UPDATE residual_islands
SET source_location_json = COALESCE(source_location_json, '{}'::jsonb)
WHERE source_location_json IS NULL;

UPDATE residual_islands
SET source_span = COALESCE(source_span, '')
WHERE source_span IS NULL;

UPDATE residual_islands
SET checksum = COALESCE(checksum, 'legacy:' || id)
WHERE checksum IS NULL;

UPDATE residual_islands
SET raw_fragment_ref = COALESCE(raw_fragment_ref, 'legacy://residual/' || id)
WHERE raw_fragment_ref IS NULL;

UPDATE residual_islands
SET reason = COALESCE(reason, 'legacy-residual-import')
WHERE reason IS NULL;

ALTER TABLE artifact_unresolved_edges
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE artifact_edge_resolution_records
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE residual_islands
    ALTER COLUMN workspace_id SET NOT NULL;

DROP INDEX IF EXISTS idx_artifact_unresolved_edges_scope;
CREATE INDEX IF NOT EXISTS idx_artifact_unresolved_edges_scope
    ON artifact_unresolved_edges(tenant_id, workspace_id, project_id, snapshot_id);

CREATE INDEX IF NOT EXISTS idx_artifact_edge_resolution_records_scope
    ON artifact_edge_resolution_records(tenant_id, workspace_id, project_id, status);

DROP INDEX IF EXISTS idx_residual_islands_scope;
CREATE INDEX IF NOT EXISTS idx_residual_islands_scope
    ON residual_islands(tenant_id, workspace_id, project_id, snapshot_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_residual_islands_scope_id
    ON residual_islands(tenant_id, workspace_id, project_id, id);
