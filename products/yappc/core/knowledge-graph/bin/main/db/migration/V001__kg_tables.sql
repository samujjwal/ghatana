CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kg_nodes (
    node_id VARCHAR(255) PRIMARY KEY,
    node_type VARCHAR(64) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    embedding VECTOR(1536),
    properties_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    tags_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    tenant_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255),
    workspace_id VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version VARCHAR(64) NOT NULL,
    labels_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS kg_edges (
    edge_id VARCHAR(255) PRIMARY KEY,
    from_node_id VARCHAR(255) NOT NULL,
    to_node_id VARCHAR(255) NOT NULL,
    relationship_type VARCHAR(64) NOT NULL,
    properties_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    tenant_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255),
    workspace_id VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version VARCHAR(64) NOT NULL,
    labels_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_kg_edges_from_node FOREIGN KEY (from_node_id) REFERENCES kg_nodes(node_id) ON DELETE CASCADE,
    CONSTRAINT fk_kg_edges_to_node FOREIGN KEY (to_node_id) REFERENCES kg_nodes(node_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_kg_nodes_tenant_id ON kg_nodes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kg_nodes_type ON kg_nodes(node_type);
CREATE INDEX IF NOT EXISTS idx_kg_nodes_workspace ON kg_nodes(workspace_id);
CREATE INDEX IF NOT EXISTS idx_kg_nodes_embedding ON kg_nodes USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_kg_edges_tenant_id ON kg_edges(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kg_edges_from_node ON kg_edges(from_node_id);
CREATE INDEX IF NOT EXISTS idx_kg_edges_to_node ON kg_edges(to_node_id);
CREATE INDEX IF NOT EXISTS idx_kg_edges_relationship_type ON kg_edges(relationship_type);