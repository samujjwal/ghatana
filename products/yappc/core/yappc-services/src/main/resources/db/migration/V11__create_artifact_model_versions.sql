-- V11__create_artifact_model_versions.sql
-- Artifact model version history for Git-like versioning and merge provenance

CREATE TABLE IF NOT EXISTS artifact_model_versions (
    id                  BIGSERIAL   PRIMARY KEY,
    version_id          VARCHAR(255) NOT NULL,
    product_id          VARCHAR(255) NOT NULL,
    tenant_id           VARCHAR(255) NOT NULL,
    parent_version_id   VARCHAR(255),
    commit_message      TEXT,
    committed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    committed_by        VARCHAR(255),
    model_snapshot_json JSONB,
    node_count          BIGINT NOT NULL DEFAULT 0,
    edge_count          BIGINT NOT NULL DEFAULT 0,
    merge_provenance_json JSONB,
    UNIQUE (tenant_id, product_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_artifact_versions_tenant ON artifact_model_versions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_artifact_versions_product ON artifact_model_versions(product_id);
CREATE INDEX IF NOT EXISTS idx_artifact_versions_committed ON artifact_model_versions(committed_at DESC);
