CREATE INDEX IF NOT EXISTS idx_kg_nodes_tenant_project_updated
    ON kg_nodes(tenant_id, project_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_kg_nodes_tenant_type_updated
    ON kg_nodes(tenant_id, node_type, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_kg_nodes_tenant_node
    ON kg_nodes(tenant_id, node_id);

CREATE INDEX IF NOT EXISTS idx_kg_edges_tenant_project_updated
    ON kg_edges(tenant_id, project_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_kg_edges_tenant_target_relationship
    ON kg_edges(tenant_id, to_node_id, relationship_type);

CREATE INDEX IF NOT EXISTS idx_kg_edges_tenant_workspace_relationship
    ON kg_edges(tenant_id, workspace_id, relationship_type);