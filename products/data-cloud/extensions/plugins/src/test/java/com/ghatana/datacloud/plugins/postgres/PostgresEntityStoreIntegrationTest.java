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
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for the PostgreSQL EntityStore SPI provider.
 * @doc.layer product
 * @doc.pattern Testcontainers, IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true) 
@DisplayName("PostgresEntityStore Integration Tests")
class PostgresEntityStoreIntegrationTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer(); 

    private PostgresEntityStore entityStore;

    @BeforeAll
    static void migrateSchema() { 
        Flyway.configure() 
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) 
            .locations("filesystem:" + Path.of(System.getProperty("user.dir"),
                    "..",
                    "..",
                    "delivery",
                    "runtime-composition",
                    "src",
                    "main",
                    "resources",
                    "db",
                    "migration").toAbsolutePath())
            .load() 
            .migrate(); 
    }

    @AfterEach
    void tearDown() { 
        if (entityStore != null) { 
            entityStore.close(); 
        }
    }

    @Test
    @DisplayName("CRUD, batch, query, and count work against PostgreSQL")
    void crudBatchQueryAndCountWorkAgainstPostgreSql() { 
        entityStore = store(); 
        TenantContext tenant = TenantContext.of("tenant-alpha");

        EntityStore.Entity saved = runPromise(() -> entityStore.save(tenant, EntityStore.Entity.builder() 
            .collection("orders")
            .id(UUID.randomUUID().toString())
            .data(Map.of("status", "open", "amount", 42, "customer", "Ada")) 
            .build())); 

        Optional<EntityStore.Entity> found = runPromise(() -> entityStore.findById(tenant, saved.id())); 
        assertThat(found).isPresent(); 
        assertThat(found.orElseThrow().data()).containsEntry("customer", "Ada"); 

        BatchResult<String> batchResult = runPromise(() -> entityStore.saveBatch(tenant, List.of( 
            EntityStore.Entity.builder().collection("orders").id(UUID.randomUUID().toString()).data(Map.of("status", "open", "amount", 99)).build(),
            EntityStore.Entity.builder().collection("orders").id(UUID.randomUUID().toString()).data(Map.of("status", "closed", "amount", 11)).build()
        )));
        assertThat(batchResult.isFullySuccessful()).isTrue(); 

        EntityStore.QueryResult queryResult = runPromise(() -> entityStore.query(tenant, EntityStore.QuerySpec.builder() 
            .collection("orders")
            .filters(List.of(EntityStore.Filter.eq("status", "open"))) 
            .sorts(List.of(EntityStore.Sort.asc("customer")))
            .limit(10) 
            .build())); 
        assertThat(queryResult.entities()).hasSize(2); 
        assertThat(queryResult.totalCount()).isEqualTo(2); 

        long count = runPromise(() -> entityStore.count(tenant, EntityStore.QuerySpec.builder() 
            .collection("orders")
            .filters(List.of(EntityStore.Filter.gte("amount", 40))) 
            .build())); 
        assertThat(count).isEqualTo(2); 

        runPromise(() -> entityStore.delete(tenant, saved.id()));
        assertThat((Boolean) runPromise(() -> entityStore.exists(tenant, saved.id()))).isFalse();
    }

    @Test
    @DisplayName("tenant isolation prevents tenant-B from reading tenant-A data")
    void tenantIsolationPreventsCrossTenantReads() { 
        entityStore = store(); 
        TenantContext tenantA = TenantContext.of("tenant-a");
        TenantContext tenantB = TenantContext.of("tenant-b");

        runPromise(() -> entityStore.save(tenantA, EntityStore.Entity.builder() 
            .collection("documents")
            .id(UUID.randomUUID().toString())
            .data(Map.of("title", "A-only", "classification", "internal")) 
            .build())); 
        runPromise(() -> entityStore.save(tenantB, EntityStore.Entity.builder() 
            .collection("documents")
            .id(UUID.randomUUID().toString())
            .data(Map.of("title", "B-only", "classification", "restricted")) 
            .build())); 

        EntityStore.QueryResult tenantAResult = runPromise(() -> entityStore.query(tenantA, EntityStore.QuerySpec.builder() 
            .collection("documents")
            .build())); 
        EntityStore.QueryResult tenantBResult = runPromise(() -> entityStore.query(tenantB, EntityStore.QuerySpec.builder() 
            .collection("documents")
            .build())); 

        assertThat(tenantAResult.entities()).hasSize(1);
        assertThat(tenantBResult.entities()).hasSize(1);

    }

    @Test
    @DisplayName("deleteBatch soft-deletes 1000+ entities")
    void deleteBatchSoftDeletesLargeBatch() { 
        entityStore = store(); 
        TenantContext tenant = TenantContext.of("tenant-batch");

        List<EntityStore.Entity> entities = IntStream.range(0, 1_001) 
            .mapToObj(index -> EntityStore.Entity.builder() 
                .collection("orders")
                .id(UUID.randomUUID().toString()) 
                .data(Map.of("index", index, "status", "expired")) 
                .build()) 
            .toList(); 
        List<EntityStore.EntityId> ids = entities.stream().map(EntityStore.Entity::id).toList(); 

        BatchResult<String> saveResult = runPromise(() -> entityStore.saveBatch(tenant, entities)); 
        assertThat(saveResult.isFullySuccessful()).isTrue(); 

        BatchResult<String> deleteResult = runPromise(() -> entityStore.deleteBatch(tenant, ids)); 

        assertThat(deleteResult.totalCount()).isEqualTo(1_001); 
        assertThat(deleteResult.successCount()).isEqualTo(1_001); 
        assertThat(deleteResult.failureCount()).isZero(); 
        assertThat(runPromise(() -> entityStore.count(tenant, EntityStore.QuerySpec.builder() 
            .collection("orders")
            .build()))).isZero(); 
        assertThat(runPromise(() -> entityStore.findById(tenant, ids.get(0)))).isEmpty();
    }

    private PostgresEntityStore store() { 
        return new PostgresEntityStore(new PostgresEntityStoreConfig( 
            POSTGRES.getJdbcUrl(), 
            POSTGRES.getUsername(), 
            POSTGRES.getPassword(), 
            8,
            1,
            30_000L,
            600_000L,
            1_800_000L
        ));
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> postgresContainer() { 
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("datacloud_plugins_it")
            .withUsername("dc_plugins")
            .withPassword("dc_plugins_secret");
    }
}
