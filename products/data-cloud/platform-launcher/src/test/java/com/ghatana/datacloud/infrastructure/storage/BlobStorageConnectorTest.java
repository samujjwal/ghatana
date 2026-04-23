/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * <p>MinIO (Apache 2.0) provides an S3-compatible object store that starts in seconds // GH-90000
 * via Testcontainers. Tests cover the full CRUD lifecycle plus bulk operations and
 * the health-check endpoint.
 *
 * <p>All async calls are driven through
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for BlobStorageConnector using Testcontainers MinIO
 * @doc.layer product
 * @doc.pattern Testcontainers, EventloopTestBase
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
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
                    .withExposedPorts(MINIO_PORT, 9001) // GH-90000
                    .withEnv("MINIO_ROOT_USER",     ACCESS) // GH-90000
                    .withEnv("MINIO_ROOT_PASSWORD",  SECRET) // GH-90000
                    .withCommand("server", "/data", "--console-address", ":9001") // GH-90000
                    .waitingFor(new HttpWaitStrategy() // GH-90000
                            .forPath("/minio/health/live")
                            .forPort(MINIO_PORT) // GH-90000
                            .withStartupTimeout(Duration.ofMinutes(2))); // GH-90000

    private static BlobStorageConnector connector;
    private              UUID collectionId;

    @BeforeAll
    static void setUpConnector() { // GH-90000
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(MINIO_PORT); // GH-90000
        BlobStorageConnectorConfig config = BlobStorageConnectorConfig.builder() // GH-90000
                .endpoint(URI.create(endpoint)) // GH-90000
                .accessKeyId(ACCESS) // GH-90000
                .secretAccessKey(SECRET) // GH-90000
                .bucketName(BUCKET) // GH-90000
                .region("us-east-1")
                .pathStyleAccess(true)     // MinIO requires path-style // GH-90000
                .keyPrefix("dc/")
                .build(); // GH-90000
        connector = new BlobStorageConnector(config, new SimpleMeterRegistry()); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        collectionId = UUID.randomUUID(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Health check
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(1) // GH-90000
    @DisplayName("healthCheck — bucket accessible")
    void healthCheck_bucketAccessible() { // GH-90000
        runPromise(() -> connector.healthCheck()); // GH-90000
        // No exception == healthy
    }

    // ──────────────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(2) // GH-90000
    @DisplayName("create — stores entity and returns it")
    void create_storesAndReturnsEntity() { // GH-90000
        Entity entity = entity(null, Map.of("name", "Widget", "price", 9.99)); // GH-90000

        Entity saved = runPromise(() -> connector.create(entity)); // GH-90000

        assertThat(saved.getId()).as("saved entity must retain ID").isEqualTo(entity.getId());
        assertThat(saved.getTenantId()).isEqualTo(TENANT); // GH-90000
        assertThat(saved.getCollectionName()).isEqualTo(COLLECT); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(3) // GH-90000
    @DisplayName("readByName — retrieves previously stored entity")
    void readByName_returnsSavedEntity() { // GH-90000
        UUID id     = UUID.randomUUID(); // GH-90000
        Entity e    = entity(id, Map.of("sku", "SKU-001", "qty", 42)); // GH-90000
        runPromise(() -> connector.create(e)); // GH-90000

        Optional<Entity> found = runPromise(() -> connector.readByName(TENANT, COLLECT, id)); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(id); // GH-90000
        assertThat(found.get().getData()).containsEntry("sku", "SKU-001"); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("readByName — returns empty for unknown ID")
    void readByName_emptyForUnknown() { // GH-90000
        Optional<Entity> found = runPromise(() -> // GH-90000
                connector.readByName(TENANT, COLLECT, UUID.randomUUID())); // GH-90000

        assertThat(found).isEmpty(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // UPDATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(5) // GH-90000
    @DisplayName("update — overwrites object in place")
    void update_overwritesObject() { // GH-90000
        UUID id  = UUID.randomUUID(); // GH-90000
        runPromise(() -> connector.create(entity(id, Map.of("status", "draft")))); // GH-90000

        Entity updated = entity(id, Map.of("status", "published")); // GH-90000
        runPromise(() -> connector.update(updated)); // GH-90000

        Optional<Entity> found = runPromise(() -> connector.readByName(TENANT, COLLECT, id)); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getData()).containsEntry("status", "published"); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // DELETE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(6) // GH-90000
    @DisplayName("deleteByName — object no longer findable after deletion")
    void deleteByName_removesObject() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        runPromise(() -> connector.create(entity(id, Map.of("temp", true)))); // GH-90000

        runPromise(() -> connector.deleteByName(TENANT, COLLECT, id)); // GH-90000

        Optional<Entity> after = runPromise(() -> connector.readByName(TENANT, COLLECT, id)); // GH-90000
        assertThat(after).isEmpty(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // SCAN / QUERY
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(7) // GH-90000
    @DisplayName("scanByName — lists all entities in a collection")
    void scanByName_returnsAllEntities() { // GH-90000
        // Use a unique collection per test to avoid cross-test pollution
        String col = "scan-" + UUID.randomUUID(); // GH-90000
        runPromise(() -> connector.create(entityInCollection(col, Map.of("idx", 1)))); // GH-90000
        runPromise(() -> connector.create(entityInCollection(col, Map.of("idx", 2)))); // GH-90000
        runPromise(() -> connector.create(entityInCollection(col, Map.of("idx", 3)))); // GH-90000

        List<Entity> all = runPromise(() -> connector.scanByName(TENANT, col, 0, 0)); // GH-90000

        assertThat(all).hasSize(3); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("query — paginated results via QuerySpec")
    void query_paginatesResults() { // GH-90000
        // collectionName must equal collectionId.toString() so that create() // GH-90000
        // and query() agree on the S3 key-path prefix. // GH-90000
        UUID collectionId = UUID.randomUUID(); // GH-90000
        String col = collectionId.toString(); // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> connector.create(entityInCollection(col, Map.of("n", idx)))); // GH-90000
        }
        QuerySpec spec = QuerySpec.builder().limit(2).offset(1).build(); // GH-90000

        StorageConnector.QueryResult result = runPromise(() -> connector.query(collectionId, TENANT, spec)); // GH-90000

        assertThat(result.entities()).hasSize(2); // GH-90000
        assertThat(result.total()).isEqualTo(5); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // COUNT
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(9) // GH-90000
    @DisplayName("count — accurate entity count per collection")
    void count_accurateCount() { // GH-90000
        UUID cid = UUID.randomUUID(); // GH-90000
        // collectionName must equal cid.toString() so create() and count() use the same S3 prefix. // GH-90000
        String col = cid.toString(); // GH-90000
        runPromise(() -> connector.create(entityInCollection(col, Map.of("a", 1)))); // GH-90000
        runPromise(() -> connector.create(entityInCollection(col, Map.of("b", 2)))); // GH-90000

        long count = runPromise(() -> connector.count(cid, TENANT, null)); // GH-90000

        assertThat(count).isEqualTo(2); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // BULK CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(10) // GH-90000
    @DisplayName("bulkCreate — creates all entities")
    void bulkCreate_createsAll() { // GH-90000
        // collectionName must equal cid.toString() so bulkCreate() and count() agree on the S3 prefix. // GH-90000
        UUID   cid  = UUID.randomUUID(); // GH-90000
        String col  = cid.toString(); // GH-90000
        List<Entity> batch = List.of( // GH-90000
                entityInCollection(col, Map.of("k", "a")), // GH-90000
                entityInCollection(col, Map.of("k", "b")), // GH-90000
                entityInCollection(col, Map.of("k", "c"))); // GH-90000

        List<Entity> created = runPromise(() -> connector.bulkCreate(cid, TENANT, batch)); // GH-90000

        assertThat(created).hasSize(3); // GH-90000
        long count = runPromise(() -> connector.count(cid, TENANT, null)); // GH-90000
        assertThat(count).isEqualTo(3); // GH-90000
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) { // GH-90000
        return entityInCollection(COLLECT, id, data); // GH-90000
    }

    private Entity entityInCollection(String collection, Map<String, Object> data) { // GH-90000
        return entityInCollection(collection, UUID.randomUUID(), data); // GH-90000
    }

    private Entity entityInCollection(String collection, UUID id, Map<String, Object> data) { // GH-90000
        var b = Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(collection) // GH-90000
                .recordType(RecordType.ENTITY) // GH-90000
                .data(data); // GH-90000
        if (id != null) b = b.id(id); // GH-90000
        return b.build(); // GH-90000
    }
}
