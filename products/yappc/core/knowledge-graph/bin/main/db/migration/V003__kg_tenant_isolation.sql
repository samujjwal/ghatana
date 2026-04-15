ALTER TABLE kg_edges DROP CONSTRAINT IF EXISTS fk_kg_edges_from_node;
ALTER TABLE kg_edges DROP CONSTRAINT IF EXISTS fk_kg_edges_to_node;

ALTER TABLE kg_nodes DROP CONSTRAINT IF EXISTS kg_nodes_pkey;
ALTER TABLE kg_edges DROP CONSTRAINT IF EXISTS kg_edges_pkey;

ALTER TABLE kg_nodes
    ADD CONSTRAINT kg_nodes_pkey PRIMARY KEY (tenant_id, node_id);

ALTER TABLE kg_edges
    ADD CONSTRAINT kg_edges_pkey PRIMARY KEY (tenant_id, edge_id);

ALTER TABLE kg_edges
    ADD CONSTRAINT fk_kg_edges_from_node_tenant
        FOREIGN KEY (tenant_id, from_node_id)
        REFERENCES kg_nodes (tenant_id, node_id)
        ON DELETE CASCADE;

ALTER TABLE kg_edges
    ADD CONSTRAINT fk_kg_edges_to_node_tenant
        FOREIGN KEY (tenant_id, to_node_id)
        REFERENCES kg_nodes (tenant_id, node_id)
        ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_kg_nodes_tenant_type_updated
    ON kg_nodes(tenant_id, node_type, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_kg_edges_tenant_source_relationship
    ON kg_edges(tenant_id, from_node_id, relationship_type);