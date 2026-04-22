-- V10__create_artifact_graph_tables.sql
-- Artifact Compiler graph tables for extracted code artifacts

CREATE TABLE IF NOT EXISTS artifact_nodes (
    id              BIGSERIAL   PRIMARY KEY,
    node_id         VARCHAR(255) NOT NULL,
    node_type       VARCHAR(100) NOT NULL,
    node_name       VARCHAR(500) NOT NULL,
    file_path       TEXT,
    content_snippet TEXT,
    properties_json  JSONB,
    tags_json        JSONB,
    tenant_id       VARCHAR(255) NOT NULL,
    project_id      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, node_id)
);

CREATE INDEX IF NOT EXISTS idx_artifact_nodes_tenant ON artifact_nodes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_project ON artifact_nodes(project_id);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_type ON artifact_nodes(node_type);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_properties ON artifact_nodes USING GIN (properties_json);
CREATE INDEX IF NOT EXISTS idx_artifact_nodes_tags ON artifact_nodes USING GIN (tags_json);

CREATE TABLE IF NOT EXISTS artifact_edges (
    id                  BIGSERIAL   PRIMARY KEY,
    source_node_id      VARCHAR(255) NOT NULL,
    target_node_id      VARCHAR(255) NOT NULL,
    relationship_type   VARCHAR(100) NOT NULL,
    properties_json     JSONB,
    tenant_id           VARCHAR(255) NOT NULL,
    project_id          VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, source_node_id, target_node_id, relationship_type)
);

CREATE INDEX IF NOT EXISTS idx_artifact_edges_tenant ON artifact_edges(tenant_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_project ON artifact_edges(project_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_source ON artifact_edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_target ON artifact_edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_artifact_edges_type ON artifact_edges(relationship_type);
