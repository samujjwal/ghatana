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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link OpenSearchConnector} against a real OpenSearch instance.
 *
 * <p>Uses a Testcontainers {@link GenericContainer} running
 * {@code opensearchproject/opensearch:1} with the security plugin disabled for simplicity.
 * All async operations are driven through {@link EventloopTestBase#runPromise}.
 *
 * @doc.type class
 * @doc.purpose Integration test for OpenSearch SEARCH-tier storage connector
 * @doc.layer product
 * @doc.pattern Testcontainers, EventloopTestBase
 */
@Testcontainers
@DisplayName("OpenSearchConnector — Integration Tests (Testcontainers)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenSearchConnectorTest extends EventloopTestBase {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> OPENSEARCH =
            new GenericContainer<>("opensearchproject/opensearch:1")
                    .withEnv("discovery.type",          "single-node")
                    // DISABLE_SECURITY_PLUGIN=true causes the demo installer to add
                    // plugins.security.disabled=true to opensearch.yml automatically.
                    // Do NOT also set plugins.security.disabled via env var — that
                    // creates a duplicate-setting conflict and causes exit code 64.
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS",    "-Xms512m -Xmx512m")
                    .withExposedPorts(9200)
                    .waitingFor(new HttpWaitStrategy()
                            .forPath("/_cluster/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    private OpenSearchConnector connector;

    private static final String TENANT     = "tenant-os-it";
    private static final String COLLECTION = "articles";
    private static final UUID   COLLECTION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        String host = OPENSEARCH.getHost();
        int    port = OPENSEARCH.getMappedPort(9200);

        OpenSearchConfig config = OpenSearchConfig.builder()
                .host(host)
                .port(port)
                .scheme("http")
                .build();

        connector = new OpenSearchConnector(config, new SimpleMeterRegistry());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: HEALTH CHECK
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("healthCheck — succeeds when OpenSearch is reachable")
    void healthCheck_succeeds() {
        Void result = runPromise(() -> connector.healthCheck());
        assertThat(result).isNull(); // no exception thrown
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: CREATE / INDEX
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("create — indexes document and returns entity with ID")
    void create_indexesDocument() {
        Entity entity = entity(null, Map.of(
                "title",   "OpenSearch Integration Test",
                "content", "This document is stored in OpenSearch",
                "score",   42));

        Entity saved = runPromise(() -> connector.create(entity));

        assertThat(saved.getId()).as("saved entity must have an ID").isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getCollectionName()).isEqualTo(COLLECTION);
        assertThat(saved.getData()).containsKey("title");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: READ
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("read — retrieves indexed document by ID")
    void read_retrievesDocument() {
        Entity saved = runPromise(() -> connector.create(
                entity(null, Map.of("title", "Readable Article", "tag", "read-test"))));

        // OpenSearch indexing is near-real-time; refresh to make it visible
        refreshIndex(TENANT);

        Optional<Entity> found = runPromise(() ->
                connector.read(COLLECTION_ID, TENANT, saved.getId()));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getData()).containsEntry("tag", "read-test");
    }

    @Test
    @Order(4)
    @DisplayName("read — returns empty Optional for non-existent ID")
    void read_nonExistentId_returnsEmpty() {
        Optional<Entity> found = runPromise(() ->
                connector.read(COLLECTION_ID, TENANT, UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: UPDATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("update — re-indexes document with new data")
    void update_reIndexesDocument() {
        Entity saved = runPromise(() -> connector.create(
                entity(null, Map.of("title", "Original Title", "version", 1))));

        Entity updated = Entity.builder()
                .id(saved.getId())
                .tenantId(TENANT)
                .collectionName(COLLECTION)
                .recordType(RecordType.ENTITY)
                .data(Map.of("title", "Updated Title", "version", 2))
                .build();

        Entity result = runPromise(() -> connector.update(updated));

        assertThat(result.getData()).containsEntry("title", "Updated Title");
        assertThat(result.getData()).containsEntry("version", 2);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: DELETE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("delete — removes document; subsequent read returns empty")
    void delete_removesDocument() {
        Entity saved = runPromise(() -> connector.create(
                entity(null, Map.of("title", "To Be Deleted"))));

        runPromise(() -> connector.delete(COLLECTION_ID, TENANT, saved.getId()));

        refreshIndex(TENANT);

        Optional<Entity> after = runPromise(() ->
                connector.read(COLLECTION_ID, TENANT, saved.getId()));

        assertThat(after).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: QUERY (full-text search)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("query — returns paginated results within limit")
    void query_returnsPaginatedResults() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            runPromise(() -> connector.create(
                    entity(null, Map.of("title", "Article " + idx, "seq", idx))));
        }
        refreshIndex(TENANT);

        QuerySpec spec = QuerySpec.builder().limit(3).offset(0).build();
        StorageConnector.QueryResult result = runPromise(() ->
                connector.query(COLLECTION_ID, TENANT, spec));

        assertThat(result.entities()).hasSizeLessThanOrEqualTo(3);
        assertThat(result.total()).isGreaterThanOrEqualTo(3);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: COUNT
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("count — returns the number of indexed documents for tenant+collection")
    void count_returnsCorrectCount() {
        // Use a unique tenant per test to avoid interference
        String tenant = "count-tenant-" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            final Entity e = Entity.builder()
                    .tenantId(tenant)
                    .collectionName(COLLECTION)
                    .recordType(RecordType.ENTITY)
                    .data(Map.of("idx", i))
                    .build();
            runPromise(() -> connector.create(e));
        }
        refreshIndex(tenant);

        long count = runPromise(() -> connector.count(COLLECTION_ID, tenant, null));

        assertThat(count).isGreaterThanOrEqualTo(4L);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: BULK CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("bulkCreate — indexes multiple entities in one request")
    void bulkCreate_indexesAll() {
        List<Entity> batch = List.of(
                entity(null, Map.of("title", "Bulk A", "batch", true)),
                entity(null, Map.of("title", "Bulk B", "batch", true)),
                entity(null, Map.of("title", "Bulk C", "batch", true)));

        List<Entity> saved = runPromise(() ->
                connector.bulkCreate(COLLECTION_ID, TENANT, batch));

        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(e -> e.getId() != null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) {
        var builder = Entity.builder()
                .tenantId(TENANT)
                .collectionName(COLLECTION)
                .recordType(RecordType.ENTITY)
                .data(data);
        if (id != null) builder = builder.id(id);
        return builder.build();
    }

    /**
     * Forces an index refresh so immediately-following reads can see new documents.
     * OpenSearch's default refresh interval is 1 s — too slow for fast unit tests.
     */
    @SuppressWarnings("deprecation")
    private void refreshIndex(String tenantId) {
        String indexName = "datacloud-" + tenantId.toLowerCase();
        try {
            // Use REST endpoint directly; the connector's restClient field is
            // not exposed so we make a simple HTTP call via the Java HttpClient.
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(
                            "http://" + OPENSEARCH.getHost() + ":" +
                            OPENSEARCH.getMappedPort(9200) + "/" + indexName + "/_refresh"))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // best-effort — refresh failures don't invalidate the test
        }
    }
}
