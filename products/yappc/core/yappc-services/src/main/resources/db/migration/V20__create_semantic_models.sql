-- V20__create_semantic_models.sql
-- Durable semantic model element storage for compile-back operations.

CREATE TABLE IF NOT EXISTS semantic_models (
    id                    VARCHAR(255) PRIMARY KEY,
    element_id            VARCHAR(255) NOT NULL,
    element_type          VARCHAR(64) NOT NULL,
    name                  VARCHAR(512) NOT NULL,
    qualified_name        TEXT,
    file_path             TEXT NOT NULL,
    source_location_json  JSONB,
    properties_json       JSONB,
    dependencies_json     JSONB,
    dependents_json       JSONB,
    provenance            VARCHAR(255) NOT NULL,
    extracted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    snapshot_id           VARCHAR(255) NOT NULL,
    tenant_id             VARCHAR(255) NOT NULL,
    workspace_id          VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_semantic_models_snapshot
ON semantic_models(snapshot_id);

CREATE INDEX IF NOT EXISTS idx_semantic_models_scope
ON semantic_models(tenant_id, workspace_id, project_id, extracted_at DESC);

CREATE INDEX IF NOT EXISTS idx_semantic_models_element
ON semantic_models(element_id);

CREATE INDEX IF NOT EXISTS idx_semantic_models_type
ON semantic_models(element_type);
