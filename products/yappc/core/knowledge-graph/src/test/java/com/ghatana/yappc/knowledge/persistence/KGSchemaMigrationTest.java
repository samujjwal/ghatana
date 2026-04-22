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
@DisplayName("KGSchemaMigration Tests [GH-90000]")
class KGSchemaMigrationTest {

    @Test
    @DisplayName("tenant isolation migration scopes node and edge identity by tenant [GH-90000]")
    void tenantIsolationMigrationScopesIdentityByTenant() throws IOException { // GH-90000
        String migration = readMigration("db/migration/V003__kg_tenant_isolation.sql [GH-90000]");

        assertThat(migration).contains("ADD CONSTRAINT kg_nodes_pkey PRIMARY KEY (tenant_id, node_id) [GH-90000]");
        assertThat(migration).contains("ADD CONSTRAINT kg_edges_pkey PRIMARY KEY (tenant_id, edge_id) [GH-90000]");
    }

    @Test
    @DisplayName("tenant isolation migration rewires edge foreign keys to tenant scoped nodes [GH-90000]")
    void tenantIsolationMigrationRewiresTenantScopedForeignKeys() throws IOException { // GH-90000
        String migration = readMigration("db/migration/V003__kg_tenant_isolation.sql [GH-90000]");

        assertThat(migration).contains("DROP CONSTRAINT IF EXISTS fk_kg_edges_from_node [GH-90000]");
        assertThat(migration).contains("DROP CONSTRAINT IF EXISTS fk_kg_edges_to_node [GH-90000]");
        assertThat(migration).contains("FOREIGN KEY (tenant_id, from_node_id) [GH-90000]");
        assertThat(migration).contains("REFERENCES kg_nodes (tenant_id, node_id) [GH-90000]");
        assertThat(migration).contains("FOREIGN KEY (tenant_id, to_node_id) [GH-90000]");
    }

    @Test
    @DisplayName("tenant isolation migration adds composite indexes for tenant hot paths [GH-90000]")
    void tenantIsolationMigrationAddsCompositeHotPathIndexes() throws IOException { // GH-90000
        String migration = readMigration("db/migration/V003__kg_tenant_isolation.sql [GH-90000]");

        assertThat(migration).contains("idx_kg_nodes_tenant_type_updated [GH-90000]");
        assertThat(migration).contains("ON kg_nodes(tenant_id, node_type, updated_at DESC) [GH-90000]");
        assertThat(migration).contains("idx_kg_edges_tenant_source_relationship [GH-90000]");
        assertThat(migration).contains("ON kg_edges(tenant_id, from_node_id, relationship_type) [GH-90000]");
    }

    private String readMigration(String resourcePath) throws IOException { // GH-90000
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) { // GH-90000
            assertThat(inputStream).isNotNull(); // GH-90000
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8); // GH-90000
        }
    }
}
