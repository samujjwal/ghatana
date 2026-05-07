/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.StorageBackendType;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CephObjectStorageConnector} against a real MinIO instance.
 *
 * <p>MinIO provides an S3-compatible API with path-style addressing — the same mode
 * required by Ceph RGW. Tests cover the full CRUD lifecycle, metadata correctness,
 * bulk operations, and health-check behaviour.
 *
 * <p>All async calls are resolved via {@link EventloopTestBase#runPromise}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for CephObjectStorageConnector using Testcontainers MinIO
 * @doc.layer product
 * @doc.pattern Testcontainers, EventloopTestBase
 */
@Testcontainers(disabledWithoutDocker = true) 
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) 
@DisplayName("CephObjectStorageConnector — Integration Tests (MinIO via Testcontainers)")
class CephObjectStorageConnectorTest extends EventloopTestBase {

    private static final int    MINIO_PORT = 9000;
    private static final String BUCKET     = "dc-ceph-test";
    private static final String ACCESS     = "minioadmin";
    private static final String SECRET     = "minioadmin";
    private static final String TENANT     = "tenant-ceph-it";
    private static final String COLLECTION = "events";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO =
            new GenericContainer<>("minio/minio:RELEASE.2024-02-26T09-33-48Z")
                    .withExposedPorts(MINIO_PORT, 9001) 
                    .withEnv("MINIO_ROOT_USER",     ACCESS) 
                    .withEnv("MINIO_ROOT_PASSWORD",  SECRET) 
                    .withCommand("server", "/data", "--console-address", ":9001") 
                    .waitingFor(new HttpWaitStrategy() 
                            .forPath("/minio/health/live")
                            .forPort(MINIO_PORT) 
                            .withStartupTimeout(Duration.ofMinutes(2))); 

    private static CephObjectStorageConnector connector;
    private UUID collectionId;

    @BeforeAll
    static void setUpConnector() { 
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(MINIO_PORT); 
        CephObjectStorageConfig config = CephObjectStorageConfig.builder() 
                .endpoint(endpoint) 
                .accessKey(ACCESS) 
                .secretKey(SECRET) 
                .bucket(BUCKET) 
                .region("us-east-1")
                .build(); 
        connector = new CephObjectStorageConnector(config); 
    }

    @BeforeEach
    void setUp() { 
        collectionId = UUID.randomUUID(); 
    }

    // ── Health check ─────────────────────────────────────────────────────────

    @Test
    @Order(1) 
    @DisplayName("healthCheck — bucket accessible")
    void healthCheck_bucketAccessible() { 
        runPromise(() -> connector.healthCheck()); 
        // No exception == healthy
    }

    // ── getMetadata ──────────────────────────────────────────────────────────

    @Test
    @Order(2) 
    @DisplayName("getMetadata — reports BLOB backend type")
    void getMetadata_reportsBlobBackend() { 
        StorageConnector.ConnectorMetadata meta = connector.getMetadata(); 
        assertThat(meta.backendType()).isEqualTo(StorageBackendType.BLOB); 
        assertThat(meta.supportsTimeSeries()).isFalse(); 
        assertThat(meta.supportsFullText()).isFalse(); 
        assertThat(meta.supportsSchemaless()).isTrue(); 
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Test
    @Order(3) 
    @DisplayName("create — stores entity and returns it with same ID")
    void create_storesAndReturnsEntity() { 
        Entity entity = entity(UUID.randomUUID(), Map.of("name", "CephWidget", "price", 7.50)); 

        Entity saved = runPromise(() -> connector.create(entity)); 

        assertThat(saved.getId()).isEqualTo(entity.getId()); 
        assertThat(saved.getTenantId()).isEqualTo(TENANT); 
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    @Test
    @Order(4) 
    @DisplayName("read — retrieves previously stored entity")
    void read_retrievesStoredEntity() { 
        UUID id     = UUID.randomUUID(); 
        Entity entity = entity(id, Map.of("key", "value")); 
        runPromise(() -> connector.create(entity)); 

        Optional<Entity> found = runPromise(() -> connector.read(collectionId, TENANT, id)); 

        assertThat(found).isPresent(); 
    }

    @Test
    @Order(5) 
    @DisplayName("read — returns empty for unknown ID")
    void read_returnsEmptyForUnknownId() { 
        Optional<Entity> found = runPromise(() -> 
                connector.read(collectionId, TENANT, UUID.randomUUID())); 
        assertThat(found).isEmpty(); 
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    @Order(6) 
    @DisplayName("update — overwrites object in place")
    void update_overwritesObject() { 
        UUID id = UUID.randomUUID(); 
        Entity original = entity(id, Map.of("version", 1)); 
        runPromise(() -> connector.create(original)); 

        Entity updated = entity(id, Map.of("version", 2)); 
        Entity result  = runPromise(() -> connector.update(updated)); 

        assertThat(result.getData().get("version")).isEqualTo(2);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @Order(7) 
    @DisplayName("delete — object no longer findable after deletion")
    void delete_objectNoLongerFindable() { 
        UUID id = UUID.randomUUID(); 
        runPromise(() -> connector.create(entity(id, Map.of("x", 1)))); 

        runPromise(() -> connector.delete(collectionId, TENANT, id)); 

        Optional<Entity> found = runPromise(() -> connector.read(collectionId, TENANT, id)); 
        assertThat(found).isEmpty(); 
    }

    // ── BULK CREATE ──────────────────────────────────────────────────────────

    @Test
    @Order(8) 
    @DisplayName("bulkCreate — creates all entities")
    void bulkCreate_createsAllEntities() { 
        List<Entity> batch = List.of( 
                entity(UUID.randomUUID(), Map.of("idx", 0)), 
                entity(UUID.randomUUID(), Map.of("idx", 1)), 
                entity(UUID.randomUUID(), Map.of("idx", 2))); 

        List<Entity> created = runPromise(() -> 
                connector.bulkCreate(collectionId, TENANT, batch)); 

        assertThat(created).hasSize(3); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) { 
        if (id != null) { 
            return Entity.builder() 
                    .id(id) 
                    .tenantId(TENANT) 
                    .collectionName(COLLECTION) 
                    .data(data) 
                    .build(); 
        }
        return Entity.builder() 
                .tenantId(TENANT) 
                .collectionName(COLLECTION) 
                .data(data) 
                .build(); 
    }
}
