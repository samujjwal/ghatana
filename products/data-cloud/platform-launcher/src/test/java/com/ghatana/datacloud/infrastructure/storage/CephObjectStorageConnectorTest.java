/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
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
                    .withExposedPorts(MINIO_PORT, 9001) // GH-90000
                    .withEnv("MINIO_ROOT_USER",     ACCESS) // GH-90000
                    .withEnv("MINIO_ROOT_PASSWORD",  SECRET) // GH-90000
                    .withCommand("server", "/data", "--console-address", ":9001") // GH-90000
                    .waitingFor(new HttpWaitStrategy() // GH-90000
                            .forPath("/minio/health/live")
                            .forPort(MINIO_PORT) // GH-90000
                            .withStartupTimeout(Duration.ofMinutes(2))); // GH-90000

    private static CephObjectStorageConnector connector;
    private UUID collectionId;

    @BeforeAll
    static void setUpConnector() { // GH-90000
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(MINIO_PORT); // GH-90000
        CephObjectStorageConfig config = CephObjectStorageConfig.builder() // GH-90000
                .endpoint(endpoint) // GH-90000
                .accessKey(ACCESS) // GH-90000
                .secretKey(SECRET) // GH-90000
                .bucket(BUCKET) // GH-90000
                .region("us-east-1")
                .build(); // GH-90000
        connector = new CephObjectStorageConnector(config); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        collectionId = UUID.randomUUID(); // GH-90000
    }

    // ── Health check ─────────────────────────────────────────────────────────

    @Test
    @Order(1) // GH-90000
    @DisplayName("healthCheck — bucket accessible")
    void healthCheck_bucketAccessible() { // GH-90000
        runPromise(() -> connector.healthCheck()); // GH-90000
        // No exception == healthy
    }

    // ── getMetadata ──────────────────────────────────────────────────────────

    @Test
    @Order(2) // GH-90000
    @DisplayName("getMetadata — reports BLOB backend type")
    void getMetadata_reportsBlobBackend() { // GH-90000
        StorageConnector.ConnectorMetadata meta = connector.getMetadata(); // GH-90000
        assertThat(meta.backendType()).isEqualTo(StorageBackendType.BLOB); // GH-90000
        assertThat(meta.supportsTimeSeries()).isFalse(); // GH-90000
        assertThat(meta.supportsFullText()).isFalse(); // GH-90000
        assertThat(meta.supportsSchemaless()).isTrue(); // GH-90000
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Test
    @Order(3) // GH-90000
    @DisplayName("create — stores entity and returns it with same ID")
    void create_storesAndReturnsEntity() { // GH-90000
        Entity entity = entity(UUID.randomUUID(), Map.of("name", "CephWidget", "price", 7.50)); // GH-90000

        Entity saved = runPromise(() -> connector.create(entity)); // GH-90000

        assertThat(saved.getId()).isEqualTo(entity.getId()); // GH-90000
        assertThat(saved.getTenantId()).isEqualTo(TENANT); // GH-90000
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    @Test
    @Order(4) // GH-90000
    @DisplayName("read — retrieves previously stored entity")
    void read_retrievesStoredEntity() { // GH-90000
        UUID id     = UUID.randomUUID(); // GH-90000
        Entity entity = entity(id, Map.of("key", "value")); // GH-90000
        runPromise(() -> connector.create(entity)); // GH-90000

        Optional<Entity> found = runPromise(() -> connector.read(collectionId, TENANT, id)); // GH-90000

        assertThat(found).isPresent(); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("read — returns empty for unknown ID")
    void read_returnsEmptyForUnknownId() { // GH-90000
        Optional<Entity> found = runPromise(() -> // GH-90000
                connector.read(collectionId, TENANT, UUID.randomUUID())); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    @Order(6) // GH-90000
    @DisplayName("update — overwrites object in place")
    void update_overwritesObject() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        Entity original = entity(id, Map.of("version", 1)); // GH-90000
        runPromise(() -> connector.create(original)); // GH-90000

        Entity updated = entity(id, Map.of("version", 2)); // GH-90000
        Entity result  = runPromise(() -> connector.update(updated)); // GH-90000

        assertThat(result.getData().get("version")).isEqualTo(2);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @Order(7) // GH-90000
    @DisplayName("delete — object no longer findable after deletion")
    void delete_objectNoLongerFindable() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        runPromise(() -> connector.create(entity(id, Map.of("x", 1)))); // GH-90000

        runPromise(() -> connector.delete(collectionId, TENANT, id)); // GH-90000

        Optional<Entity> found = runPromise(() -> connector.read(collectionId, TENANT, id)); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    // ── BULK CREATE ──────────────────────────────────────────────────────────

    @Test
    @Order(8) // GH-90000
    @DisplayName("bulkCreate — creates all entities")
    void bulkCreate_createsAllEntities() { // GH-90000
        List<Entity> batch = List.of( // GH-90000
                entity(UUID.randomUUID(), Map.of("idx", 0)), // GH-90000
                entity(UUID.randomUUID(), Map.of("idx", 1)), // GH-90000
                entity(UUID.randomUUID(), Map.of("idx", 2))); // GH-90000

        List<Entity> created = runPromise(() -> // GH-90000
                connector.bulkCreate(collectionId, TENANT, batch)); // GH-90000

        assertThat(created).hasSize(3); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) { // GH-90000
        if (id != null) { // GH-90000
            return Entity.builder() // GH-90000
                    .id(id) // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .data(data) // GH-90000
                    .build(); // GH-90000
        }
        return Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .data(data) // GH-90000
                .build(); // GH-90000
    }
}
