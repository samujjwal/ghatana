package com.ghatana.yappc.knowledge.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies tenant isolation guarantees encoded in knowledge graph schema migrations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KGSchemaMigration Tests")
class KGSchemaMigrationTest {

    @Test
    @DisplayName("tenant isolation migration scopes node and edge identity by tenant")
    void tenantIsolationMigrationScopesIdentityByTenant() throws IOException {
        String migration = readMigration("db/migration/V003__kg_tenant_isolation.sql");

        assertThat(migration).contains("ADD CONSTRAINT kg_nodes_pkey PRIMARY KEY (tenant_id, node_id)");
        assertThat(migration).contains("ADD CONSTRAINT kg_edges_pkey PRIMARY KEY (tenant_id, edge_id)");
    }

    @Test
    @DisplayName("tenant isolation migration rewires edge foreign keys to tenant scoped nodes")
    void tenantIsolationMigrationRewiresTenantScopedForeignKeys() throws IOException {
        String migration = readMigration("db/migration/V003__kg_tenant_isolation.sql");

        assertThat(migration).contains("DROP CONSTRAINT IF EXISTS fk_kg_edges_from_node");
        assertThat(migration).contains("DROP CONSTRAINT IF EXISTS fk_kg_edges_to_node");
        assertThat(migration).contains("FOREIGN KEY (tenant_id, from_node_id)");
        assertThat(migration).contains("REFERENCES kg_nodes (tenant_id, node_id)");
        assertThat(migration).contains("FOREIGN KEY (tenant_id, to_node_id)");
    }

    @Test
    @DisplayName("tenant isolation migration adds composite indexes for tenant hot paths")
    void tenantIsolationMigrationAddsCompositeHotPathIndexes() throws IOException {
        String migration = readMigration("db/migration/V003__kg_tenant_isolation.sql");

        assertThat(migration).contains("idx_kg_nodes_tenant_type_updated");
        assertThat(migration).contains("ON kg_nodes(tenant_id, node_type, updated_at DESC)");
        assertThat(migration).contains("idx_kg_edges_tenant_source_relationship");
        assertThat(migration).contains("ON kg_edges(tenant_id, from_node_id, relationship_type)");
    }

    private String readMigration(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
