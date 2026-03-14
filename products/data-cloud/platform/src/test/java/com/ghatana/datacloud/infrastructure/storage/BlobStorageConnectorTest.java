/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BlobStorageConnector} against a real MinIO instance.
 *
 * <p>MinIO (Apache 2.0) provides an S3-compatible object store that starts in seconds
 * via Testcontainers. Tests cover the full CRUD lifecycle plus bulk operations and
 * the health-check endpoint.
 *
 * <p>All async calls are driven through
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for BlobStorageConnector using Testcontainers MinIO
 * @doc.layer product
 * @doc.pattern Testcontainers, EventloopTestBase
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("BlobStorageConnector — Integration Tests (MinIO via Testcontainers)")
class BlobStorageConnectorTest extends EventloopTestBase {

    private static final int MINIO_PORT  = 9000;
    private static final String BUCKET   = "dc-blob-test";
    private static final String ACCESS   = "minioadmin";
    private static final String SECRET   = "minioadmin";
    private static final String TENANT   = "tenant-s3-it";
    private static final String COLLECT  = "products";

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

    private static BlobStorageConnector connector;
    private              UUID collectionId;

    @BeforeAll
    static void setUpConnector() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(MINIO_PORT);
        BlobStorageConnectorConfig config = BlobStorageConnectorConfig.builder()
                .endpoint(URI.create(endpoint))
                .accessKeyId(ACCESS)
                .secretAccessKey(SECRET)
                .bucketName(BUCKET)
                .region("us-east-1")
                .pathStyleAccess(true)     // MinIO requires path-style
                .keyPrefix("dc/")
                .build();
        connector = new BlobStorageConnector(config, new SimpleMeterRegistry());
    }

    @BeforeEach
    void setUp() {
        collectionId = UUID.randomUUID();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Health check
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("healthCheck — bucket accessible")
    void healthCheck_bucketAccessible() {
        runPromise(() -> connector.healthCheck());
        // No exception == healthy
    }

    // ──────────────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("create — stores entity and returns it")
    void create_storesAndReturnsEntity() {
        Entity entity = entity(null, Map.of("name", "Widget", "price", 9.99));

        Entity saved = runPromise(() -> connector.create(entity));

        assertThat(saved.getId()).as("saved entity must retain ID").isEqualTo(entity.getId());
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getCollectionName()).isEqualTo(COLLECT);
    }

    // ──────────────────────────────────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("readByName — retrieves previously stored entity")
    void readByName_returnsSavedEntity() {
        UUID id     = UUID.randomUUID();
        Entity e    = entity(id, Map.of("sku", "SKU-001", "qty", 42));
        runPromise(() -> connector.create(e));

        Optional<Entity> found = runPromise(() -> connector.readByName(TENANT, COLLECT, id));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getData()).containsEntry("sku", "SKU-001");
    }

    @Test
    @Order(4)
    @DisplayName("readByName — returns empty for unknown ID")
    void readByName_emptyForUnknown() {
        Optional<Entity> found = runPromise(() ->
                connector.readByName(TENANT, COLLECT, UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // UPDATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("update — overwrites object in place")
    void update_overwritesObject() {
        UUID id  = UUID.randomUUID();
        runPromise(() -> connector.create(entity(id, Map.of("status", "draft"))));

        Entity updated = entity(id, Map.of("status", "published"));
        runPromise(() -> connector.update(updated));

        Optional<Entity> found = runPromise(() -> connector.readByName(TENANT, COLLECT, id));
        assertThat(found).isPresent();
        assertThat(found.get().getData()).containsEntry("status", "published");
    }

    // ──────────────────────────────────────────────────────────────────────
    // DELETE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("deleteByName — object no longer findable after deletion")
    void deleteByName_removesObject() {
        UUID id = UUID.randomUUID();
        runPromise(() -> connector.create(entity(id, Map.of("temp", true))));

        runPromise(() -> connector.deleteByName(TENANT, COLLECT, id));

        Optional<Entity> after = runPromise(() -> connector.readByName(TENANT, COLLECT, id));
        assertThat(after).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // SCAN / QUERY
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("scanByName — lists all entities in a collection")
    void scanByName_returnsAllEntities() {
        // Use a unique collection per test to avoid cross-test pollution
        String col = "scan-" + UUID.randomUUID();
        runPromise(() -> connector.create(entityInCollection(col, Map.of("idx", 1))));
        runPromise(() -> connector.create(entityInCollection(col, Map.of("idx", 2))));
        runPromise(() -> connector.create(entityInCollection(col, Map.of("idx", 3))));

        List<Entity> all = runPromise(() -> connector.scanByName(TENANT, col, 0, 0));

        assertThat(all).hasSize(3);
    }

    @Test
    @Order(8)
    @DisplayName("query — paginated results via QuerySpec")
    void query_paginatesResults() {
        // collectionName must equal collectionId.toString() so that create()
        // and query() agree on the S3 key-path prefix.
        UUID collectionId = UUID.randomUUID();
        String col = collectionId.toString();
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            runPromise(() -> connector.create(entityInCollection(col, Map.of("n", idx))));
        }
        QuerySpec spec = QuerySpec.builder().limit(2).offset(1).build();

        StorageConnector.QueryResult result = runPromise(() -> connector.query(collectionId, TENANT, spec));

        assertThat(result.entities()).hasSize(2);
        assertThat(result.total()).isEqualTo(5);
    }

    // ──────────────────────────────────────────────────────────────────────
    // COUNT
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("count — accurate entity count per collection")
    void count_accurateCount() {
        UUID cid = UUID.randomUUID();
        // collectionName must equal cid.toString() so create() and count() use the same S3 prefix.
        String col = cid.toString();
        runPromise(() -> connector.create(entityInCollection(col, Map.of("a", 1))));
        runPromise(() -> connector.create(entityInCollection(col, Map.of("b", 2))));

        long count = runPromise(() -> connector.count(cid, TENANT, null));

        assertThat(count).isEqualTo(2);
    }

    // ──────────────────────────────────────────────────────────────────────
    // BULK CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("bulkCreate — creates all entities")
    void bulkCreate_createsAll() {
        // collectionName must equal cid.toString() so bulkCreate() and count() agree on the S3 prefix.
        UUID   cid  = UUID.randomUUID();
        String col  = cid.toString();
        List<Entity> batch = List.of(
                entityInCollection(col, Map.of("k", "a")),
                entityInCollection(col, Map.of("k", "b")),
                entityInCollection(col, Map.of("k", "c")));

        List<Entity> created = runPromise(() -> connector.bulkCreate(cid, TENANT, batch));

        assertThat(created).hasSize(3);
        long count = runPromise(() -> connector.count(cid, TENANT, null));
        assertThat(count).isEqualTo(3);
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) {
        return entityInCollection(COLLECT, id, data);
    }

    private Entity entityInCollection(String collection, Map<String, Object> data) {
        return entityInCollection(collection, UUID.randomUUID(), data);
    }

    private Entity entityInCollection(String collection, UUID id, Map<String, Object> data) {
        var b = Entity.builder()
                .tenantId(TENANT)
                .collectionName(collection)
                .recordType(RecordType.ENTITY)
                .data(data);
        if (id != null) b = b.id(id);
        return b.build();
    }
}
