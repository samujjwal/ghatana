package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.observability.NoopMetricsCollector;
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
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("OpenSearchConnector — Integration Tests (Testcontainers)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class OpenSearchConnectorTest extends EventloopTestBase {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> OPENSEARCH =
            new GenericContainer<>("opensearchproject/opensearch:1")
                    .withEnv("discovery.type",          "single-node") // GH-90000
                    // DISABLE_SECURITY_PLUGIN=true causes the demo installer to add
                    // plugins.security.disabled=true to opensearch.yml automatically.
                    // Do NOT also set plugins.security.disabled via env var — that
                    // creates a duplicate-setting conflict and causes exit code 64.
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true") // GH-90000
                    .withEnv("OPENSEARCH_JAVA_OPTS",    "-Xms512m -Xmx512m") // GH-90000
                    .withExposedPorts(9200) // GH-90000
                    .waitingFor(new HttpWaitStrategy() // GH-90000
                            .forPath("/_cluster/health")
                            .forStatusCode(200) // GH-90000
                            .withStartupTimeout(Duration.ofMinutes(3))); // GH-90000

    private OpenSearchConnector connector;

    private static final String TENANT     = "tenant-os-it";
    private static final String COLLECTION = "articles";
    private static final UUID   COLLECTION_ID = UUID.randomUUID(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        String host = OPENSEARCH.getHost(); // GH-90000
        int    port = OPENSEARCH.getMappedPort(9200); // GH-90000

        OpenSearchConfig config = OpenSearchConfig.builder() // GH-90000
                .host(host) // GH-90000
                .port(port) // GH-90000
                .scheme("http")
                .build(); // GH-90000

        connector = new OpenSearchConnector(config, NoopMetricsCollector.getInstance()); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: HEALTH CHECK
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(1) // GH-90000
    @DisplayName("healthCheck — succeeds when OpenSearch is reachable")
    void healthCheck_succeeds() { // GH-90000
        Void result = runPromise(() -> connector.healthCheck()); // GH-90000
        assertThat(result).isNull(); // no exception thrown // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: CREATE / INDEX
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(2) // GH-90000
    @DisplayName("create — indexes document and returns entity with ID")
    void create_indexesDocument() { // GH-90000
        Entity entity = entity(null, Map.of( // GH-90000
                "title",   "OpenSearch Integration Test",
                "content", "This document is stored in OpenSearch",
                "score",   42));

        Entity saved = runPromise(() -> connector.create(entity)); // GH-90000

        assertThat(saved.getId()).as("saved entity must have an ID").isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(TENANT); // GH-90000
        assertThat(saved.getCollectionName()).isEqualTo(COLLECTION); // GH-90000
        assertThat(saved.getData()).containsKey("title");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: READ
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(3) // GH-90000
    @DisplayName("read — retrieves indexed document by ID")
    void read_retrievesDocument() { // GH-90000
        Entity saved = runPromise(() -> connector.create( // GH-90000
                entity(null, Map.of("title", "Readable Article", "tag", "read-test")))); // GH-90000

        // OpenSearch indexing is near-real-time; refresh to make it visible
        refreshIndex(TENANT); // GH-90000

        Optional<Entity> found = runPromise(() -> // GH-90000
                connector.read(COLLECTION_ID, TENANT, saved.getId())); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(saved.getId()); // GH-90000
        assertThat(found.get().getData()).containsEntry("tag", "read-test"); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("read — returns empty Optional for non-existent ID")
    void read_nonExistentId_returnsEmpty() { // GH-90000
        Optional<Entity> found = runPromise(() -> // GH-90000
                connector.read(COLLECTION_ID, TENANT, UUID.randomUUID())); // GH-90000

        assertThat(found).isEmpty(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: UPDATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(5) // GH-90000
    @DisplayName("update — re-indexes document with new data")
    void update_reIndexesDocument() { // GH-90000
        Entity saved = runPromise(() -> connector.create( // GH-90000
                entity(null, Map.of("title", "Original Title", "version", 1)))); // GH-90000

        Entity updated = Entity.builder() // GH-90000
                .id(saved.getId()) // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .recordType(RecordType.ENTITY) // GH-90000
                .data(Map.of("title", "Updated Title", "version", 2)) // GH-90000
                .build(); // GH-90000

        Entity result = runPromise(() -> connector.update(updated)); // GH-90000

        assertThat(result.getData()).containsEntry("title", "Updated Title"); // GH-90000
        assertThat(result.getData()).containsEntry("version", 2); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: DELETE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(6) // GH-90000
    @DisplayName("delete — removes document; subsequent read returns empty")
    void delete_removesDocument() { // GH-90000
        Entity saved = runPromise(() -> connector.create( // GH-90000
                entity(null, Map.of("title", "To Be Deleted")))); // GH-90000

        runPromise(() -> connector.delete(COLLECTION_ID, TENANT, saved.getId())); // GH-90000

        refreshIndex(TENANT); // GH-90000

        Optional<Entity> after = runPromise(() -> // GH-90000
                connector.read(COLLECTION_ID, TENANT, saved.getId())); // GH-90000

        assertThat(after).isEmpty(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: QUERY (full-text search) // GH-90000
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(7) // GH-90000
    @DisplayName("query — returns paginated results within limit")
    void query_returnsPaginatedResults() { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> connector.create( // GH-90000
                    entity(null, Map.of("title", "Article " + idx, "seq", idx)))); // GH-90000
        }
        refreshIndex(TENANT); // GH-90000

        QuerySpec spec = QuerySpec.builder().limit(3).offset(0).build(); // GH-90000
        StorageConnector.QueryResult result = runPromise(() -> // GH-90000
                connector.query(COLLECTION_ID, TENANT, spec)); // GH-90000

        assertThat(result.entities()).hasSizeLessThanOrEqualTo(3); // GH-90000
        assertThat(result.total()).isGreaterThanOrEqualTo(3); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: COUNT
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(8) // GH-90000
    @DisplayName("count — returns the number of indexed documents for tenant+collection")
    void count_returnsCorrectCount() { // GH-90000
        // Use a unique tenant per test to avoid interference
        String tenant = "count-tenant-" + UUID.randomUUID(); // GH-90000
        for (int i = 0; i < 4; i++) { // GH-90000
            final Entity e = Entity.builder() // GH-90000
                    .tenantId(tenant) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .recordType(RecordType.ENTITY) // GH-90000
                    .data(Map.of("idx", i)) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> connector.create(e)); // GH-90000
        }
        refreshIndex(tenant); // GH-90000

        long count = runPromise(() -> connector.count(COLLECTION_ID, tenant, null)); // GH-90000

        assertThat(count).isGreaterThanOrEqualTo(4L); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: BULK CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @Order(9) // GH-90000
    @DisplayName("bulkCreate — indexes multiple entities in one request")
    void bulkCreate_indexesAll() { // GH-90000
        List<Entity> batch = List.of( // GH-90000
                entity(null, Map.of("title", "Bulk A", "batch", true)), // GH-90000
                entity(null, Map.of("title", "Bulk B", "batch", true)), // GH-90000
                entity(null, Map.of("title", "Bulk C", "batch", true))); // GH-90000

        List<Entity> saved = runPromise(() -> // GH-90000
                connector.bulkCreate(COLLECTION_ID, TENANT, batch)); // GH-90000

        assertThat(saved).hasSize(3); // GH-90000
        assertThat(saved).allMatch(e -> e.getId() != null); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private Entity entity(UUID id, Map<String, Object> data) { // GH-90000
        var builder = Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .recordType(RecordType.ENTITY) // GH-90000
                .data(data); // GH-90000
        if (id != null) builder = builder.id(id); // GH-90000
        return builder.build(); // GH-90000
    }

    /**
     * Forces an index refresh so immediately-following reads can see new documents.
     * OpenSearch's default refresh interval is 1 s — too slow for fast unit tests.
     */
    @SuppressWarnings("deprecation")
    private void refreshIndex(String tenantId) { // GH-90000
        String indexName = "datacloud-" + tenantId.toLowerCase(); // GH-90000
        try {
            // Use REST endpoint directly; the connector's restClient field is
            // not exposed so we make a simple HTTP call via the Java HttpClient.
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient(); // GH-90000
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder() // GH-90000
                    .uri(java.net.URI.create( // GH-90000
                            "http://" + OPENSEARCH.getHost() + ":" + // GH-90000
                            OPENSEARCH.getMappedPort(9200) + "/" + indexName + "/_refresh")) // GH-90000
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody()) // GH-90000
                    .build(); // GH-90000
            http.send(req, java.net.http.HttpResponse.BodyHandlers.discarding()); // GH-90000
        } catch (Exception e) { // GH-90000
            // best-effort — refresh failures don't invalidate the test
        }
    }
}
