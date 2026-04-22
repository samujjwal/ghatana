package com.ghatana.datacloud.plugins.postgres;

import com.ghatana.datacloud.spi.BatchResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for the PostgreSQL EntityStore SPI provider.
 * @doc.layer product
 * @doc.pattern Testcontainers, IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("PostgresEntityStore Integration Tests [GH-90000]")
class PostgresEntityStoreIntegrationTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer(); // GH-90000

    private PostgresEntityStore entityStore;

    @BeforeAll
    static void migrateSchema() { // GH-90000
        Flyway.configure() // GH-90000
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) // GH-90000
            .locations("filesystem:" + Path.of(System.getProperty("user.dir [GH-90000]"), "products", "data-cloud", "platform-launcher", "src", "main", "resources", "db", "migration"))
            .load() // GH-90000
            .migrate(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (entityStore != null) { // GH-90000
            entityStore.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("CRUD, batch, query, and count work against PostgreSQL [GH-90000]")
    void crudBatchQueryAndCountWorkAgainstPostgreSql() { // GH-90000
        entityStore = store(); // GH-90000
        TenantContext tenant = TenantContext.of("tenant-alpha [GH-90000]");

        EntityStore.Entity saved = runPromise(() -> entityStore.save(tenant, EntityStore.Entity.builder() // GH-90000
            .collection("orders [GH-90000]")
            .id("order-1 [GH-90000]")
            .data(Map.of("status", "open", "amount", 42, "customer", "Ada")) // GH-90000
            .build())); // GH-90000

        Optional<EntityStore.Entity> found = runPromise(() -> entityStore.findById(tenant, saved.id())); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.orElseThrow().data()).containsEntry("customer", "Ada"); // GH-90000

        BatchResult<String> batchResult = runPromise(() -> entityStore.saveBatch(tenant, List.of( // GH-90000
            EntityStore.Entity.builder().collection("orders [GH-90000]").id("order-2 [GH-90000]").data(Map.of("status", "open", "amount", 99)).build(),
            EntityStore.Entity.builder().collection("orders [GH-90000]").id("order-3 [GH-90000]").data(Map.of("status", "closed", "amount", 11)).build()
        )));
        assertThat(batchResult.isFullySuccessful()).isTrue(); // GH-90000

        EntityStore.QueryResult queryResult = runPromise(() -> entityStore.query(tenant, EntityStore.QuerySpec.builder() // GH-90000
            .collection("orders [GH-90000]")
            .filters(List.of(EntityStore.Filter.eq("status", "open"))) // GH-90000
            .sorts(List.of(EntityStore.Sort.asc("customer [GH-90000]")))
            .limit(10) // GH-90000
            .build())); // GH-90000
        assertThat(queryResult.entities()).hasSize(2); // GH-90000
        assertThat(queryResult.totalCount()).isEqualTo(2); // GH-90000

        long count = runPromise(() -> entityStore.count(tenant, EntityStore.QuerySpec.builder() // GH-90000
            .collection("orders [GH-90000]")
            .filters(List.of(EntityStore.Filter.gte("amount", 40))) // GH-90000
            .build())); // GH-90000
        assertThat(count).isEqualTo(2); // GH-90000

        runPromise(() -> entityStore.delete(tenant, EntityStore.EntityId.of("order-1 [GH-90000]")));
        assertThat(runPromise(() -> entityStore.exists(tenant, EntityStore.EntityId.of("order-1 [GH-90000]")))).isFalse();
    }

    @Test
    @DisplayName("tenant isolation prevents tenant-B from reading tenant-A data [GH-90000]")
    void tenantIsolationPreventsCrossTenantReads() { // GH-90000
        entityStore = store(); // GH-90000
        TenantContext tenantA = TenantContext.of("tenant-a [GH-90000]");
        TenantContext tenantB = TenantContext.of("tenant-b [GH-90000]");

        runPromise(() -> entityStore.save(tenantA, EntityStore.Entity.builder() // GH-90000
            .collection("documents [GH-90000]")
            .id("doc-1 [GH-90000]")
            .data(Map.of("title", "A-only", "classification", "internal")) // GH-90000
            .build())); // GH-90000
        runPromise(() -> entityStore.save(tenantB, EntityStore.Entity.builder() // GH-90000
            .collection("documents [GH-90000]")
            .id("doc-2 [GH-90000]")
            .data(Map.of("title", "B-only", "classification", "restricted")) // GH-90000
            .build())); // GH-90000

        EntityStore.QueryResult tenantAResult = runPromise(() -> entityStore.query(tenantA, EntityStore.QuerySpec.builder() // GH-90000
            .collection("documents [GH-90000]")
            .build())); // GH-90000
        EntityStore.QueryResult tenantBResult = runPromise(() -> entityStore.query(tenantB, EntityStore.QuerySpec.builder() // GH-90000
            .collection("documents [GH-90000]")
            .build())); // GH-90000

        assertThat(tenantAResult.entities()).extracting(entity -> entity.id().value()).containsExactly("doc-1 [GH-90000]");
        assertThat(tenantBResult.entities()).extracting(entity -> entity.id().value()).containsExactly("doc-2 [GH-90000]");

        Optional<EntityStore.Entity> crossTenantRead = runPromise(() -> entityStore.findById(tenantB, EntityStore.EntityId.of("doc-1 [GH-90000]")));
        assertThat(crossTenantRead).isEmpty(); // GH-90000

        try (var connection = POSTGRES.createConnection(" [GH-90000]");
             var statement = connection.prepareStatement("SELECT tenant_id, id FROM entities ORDER BY tenant_id, id [GH-90000]")) {
            try (var resultSet = statement.executeQuery()) { // GH-90000
                assertThat(resultSet.next()).isTrue(); // GH-90000
                assertThat(resultSet.getString("tenant_id [GH-90000]")).isEqualTo("tenant-a [GH-90000]");
                assertThat(resultSet.getObject("id [GH-90000]").toString()).isEqualTo("doc-1 [GH-90000]");
                assertThat(resultSet.next()).isTrue(); // GH-90000
                assertThat(resultSet.getString("tenant_id [GH-90000]")).isEqualTo("tenant-b [GH-90000]");
                assertThat(resultSet.getObject("id [GH-90000]").toString()).isEqualTo("doc-2 [GH-90000]");
                assertThat(resultSet.next()).isFalse(); // GH-90000
            }
        } catch (Exception exception) { // GH-90000
            throw new AssertionError("Direct database inspection failed", exception); // GH-90000
        }
    }

    @Test
    @DisplayName("deleteBatch soft-deletes 1000+ entities [GH-90000]")
    void deleteBatchSoftDeletesLargeBatch() { // GH-90000
        entityStore = store(); // GH-90000
        TenantContext tenant = TenantContext.of("tenant-batch [GH-90000]");

        List<EntityStore.Entity> entities = IntStream.range(0, 1_001) // GH-90000
            .mapToObj(index -> EntityStore.Entity.builder() // GH-90000
                .collection("orders [GH-90000]")
                .id("batch-order-" + index) // GH-90000
                .data(Map.of("index", index, "status", "expired")) // GH-90000
                .build()) // GH-90000
            .toList(); // GH-90000
        List<EntityStore.EntityId> ids = entities.stream().map(EntityStore.Entity::id).toList(); // GH-90000

        BatchResult<String> saveResult = runPromise(() -> entityStore.saveBatch(tenant, entities)); // GH-90000
        assertThat(saveResult.isFullySuccessful()).isTrue(); // GH-90000

        BatchResult<String> deleteResult = runPromise(() -> entityStore.deleteBatch(tenant, ids)); // GH-90000

        assertThat(deleteResult.totalCount()).isEqualTo(1_001); // GH-90000
        assertThat(deleteResult.successCount()).isEqualTo(1_001); // GH-90000
        assertThat(deleteResult.failureCount()).isZero(); // GH-90000
        assertThat(runPromise(() -> entityStore.count(tenant, EntityStore.QuerySpec.builder() // GH-90000
            .collection("orders [GH-90000]")
            .build()))).isZero(); // GH-90000
        assertThat(runPromise(() -> entityStore.findById(tenant, EntityStore.EntityId.of("batch-order-0 [GH-90000]")))).isEmpty();
    }

    private PostgresEntityStore store() { // GH-90000
        return new PostgresEntityStore(new PostgresEntityStoreConfig( // GH-90000
            POSTGRES.getJdbcUrl(), // GH-90000
            POSTGRES.getUsername(), // GH-90000
            POSTGRES.getPassword(), // GH-90000
            8,
            1,
            30_000L,
            600_000L,
            1_800_000L
        ));
    }

    @SuppressWarnings("resource [GH-90000]")
    private static PostgreSQLContainer<?> postgresContainer() { // GH-90000
        return new PostgreSQLContainer<>("postgres:16-alpine [GH-90000]")
            .withDatabaseName("datacloud_plugins_it [GH-90000]")
            .withUsername("dc_plugins [GH-90000]")
            .withPassword("dc_plugins_secret [GH-90000]");
    }
}