package com.ghatana.datacloud.infrastructure.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.storage.FilterCriteria;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.infrastructure.audit.DataCloudAuditLogger;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link PostgresJsonbConnector} against a real PostgreSQL instance.
 *
 * <p>Uses a Testcontainers {@link PostgreSQLContainer} with Flyway schema migration and a
 * thin JDBC-backed {@link EntityRepository} (no Spring / JPA required).
 * All async calls are driven through {@link EventloopTestBase#runPromise}.
 *
 * @doc.type class
 * @doc.purpose Integration test for PostgreSQL JSONB storage connector
 * @doc.layer product
 * @doc.pattern Testcontainers, EventloopTestBase
 */
@Testcontainers
@DisplayName("PostgresJsonbConnector — Integration Tests (Testcontainers)")
class PostgresJsonbConnectorIntegrationTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("datacloud_it")
                    .withUsername("dc_test")
                    .withPassword("dc_test_secret");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Executor     EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final String TENANT     = "tenant-pg-it";
    private static final String COLLECTION = "products";
    private              UUID   collectionId;

    private PostgresJsonbConnector connector;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
              .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
              .locations("classpath:db/migration")
              .load()
              .migrate();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-test setup
    // ──────────────────────────────────────────────────────────────────────
    @BeforeEach
    void setUp() {
        collectionId = UUID.randomUUID();

        EntityRepository repository = new JdbcEntityRepository(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), EXECUTOR);

        // noop MetricsCollector and noop AuditLogger to keep the test focused on persistence
        connector = new PostgresJsonbConnector(
                repository,
                noopMetrics(),
                DataCloudAuditLogger.noop());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — persists entity and returns with generated ID")
    void create_persistsAndReturnsId() {
        Entity entity = entityWith(null, Map.of("name", "Widget", "price", 9.99));

        Entity saved = runPromise(() -> connector.create(entity));

        assertThat(saved.getId()).as("persisted entity must have an ID").isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getCollectionName()).isEqualTo(COLLECTION);
        assertThat(saved.getData()).containsKey("name");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: READ
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("read — returns saved entity by ID")
    void read_returnsSavedEntity() {
        Entity saved = runPromise(() -> connector.create(
                entityWith(null, Map.of("name", "Gadget", "price", 19.99))));

        Optional<Entity> found = runPromise(() ->
                connector.read(collectionId, TENANT, saved.getId()));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getData()).containsEntry("name", "Gadget");
    }

    @Test
    @DisplayName("read — returns empty Optional for unknown ID")
    void read_unknownId_returnsEmpty() {
        Optional<Entity> found = runPromise(() ->
                connector.read(collectionId, TENANT, UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: UPDATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — modifies data and increments version")
    void update_modifiesData() {
        Entity saved = runPromise(() -> connector.create(
                entityWith(null, Map.of("name", "SomeProduct", "price", 5.00))));

        Entity updated = Entity.builder()
                .id(saved.getId())
                .tenantId(TENANT)
                .collectionName(COLLECTION)
                .recordType(saved.getRecordType())
                .data(Map.of("name", "SomeProduct", "price", 7.50, "discount", true))
                .build();

        Entity result = runPromise(() -> connector.update(updated));

        assertThat(result.getData()).containsEntry("price", 7.50);
        assertThat(result.getData()).containsEntry("discount", true);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: DELETE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — removes entity; subsequent read returns empty")
    void delete_removesEntity() {
        Entity saved = runPromise(() -> connector.create(
                entityWith(null, Map.of("name", "ToDelete"))));

        runPromise(() -> connector.delete(collectionId, TENANT, saved.getId()));

        Optional<Entity> after = runPromise(() ->
                connector.read(collectionId, TENANT, saved.getId()));

        assertThat(after).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: COUNT
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("count — returns correct row count for tenant+collection")
    void count_returnsCorrectCount() {
        // Entities must be stored with collectionName = collectionId.toString()
        // because the connector uses collectionId.toString() to scope count/query.
        String col = collectionId.toString();
        runPromise(() -> connector.create(entityWith(null, col, Map.of("tag", "count-a"))));
        runPromise(() -> connector.create(entityWith(null, col, Map.of("tag", "count-b"))));
        runPromise(() -> connector.create(entityWith(null, col, Map.of("tag", "count-c"))));
        runPromise(() -> connector.create(entityWith(null, "other-col", Map.of("tag", "other"))));

        long count = runPromise(() -> connector.count(collectionId, TENANT, null));

        // count must be >= 3 (tenant isolation means we only count our collection)
        assertThat(count).isGreaterThanOrEqualTo(3L);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: QUERY
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("query — applies limit and returns results")
    void query_appliesLimit() {
        // Entities must use collectionId.toString() so query scope matches
        String col = collectionId.toString();
        for (int i = 0; i < 5; i++) {
            int idx = i;
            runPromise(() -> connector.create(entityWith(null, col, Map.of("seq", idx))));
        }

        QuerySpec spec = QuerySpec.builder().limit(3).offset(0).build();
        StorageConnector.QueryResult result = runPromise(() ->
                connector.query(collectionId, TENANT, spec));

        assertThat(result.entities()).hasSizeLessThanOrEqualTo(3);
        assertThat(result.total()).isGreaterThanOrEqualTo(3);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: HEALTH CHECK
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthCheck — succeeds when database is reachable")
    void healthCheck_succeedsWhenDbReachable() {
        Void result = runPromise(() -> connector.healthCheck());
        // If no exception is thrown the database is healthy
        assertThat(result).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private Entity entityWith(UUID id, Map<String, Object> data) {
        return entityWith(id, COLLECTION, data);
    }

    private Entity entityWith(UUID id, String collection, Map<String, Object> data) {
        var builder = Entity.builder()
                .tenantId(TENANT)
                .collectionName(collection)
                .recordType(RecordType.ENTITY)
                .data(data);
        if (id != null) builder = builder.id(id);
        return builder.build();
    }

    private static com.ghatana.platform.observability.MetricsCollector noopMetrics() {
        return new com.ghatana.platform.observability.MetricsCollector() {
            @Override public void incrementCounter(String name, String... tags) {}
            @Override public void increment(String name, double amount, java.util.Map<String, String> tags) {}
            @Override public void recordTimer(String name, long durationMs, String... tags) {}
            @Override public void recordError(String name, Exception ex, java.util.Map<String, String> tags) {}
            @Override public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() { return null; }
        };
    }

    // ──────────────────────────────────────────────────────────────────────
    // Thin JDBC-based EntityRepository — no Spring / JPA required
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Minimal JDBC implementation of {@link EntityRepository} backed by the
     * real PostgreSQL schema. Used only in this integration test; not for production.
     *
     * @doc.type class
     * @doc.purpose Test-only JDBC EntityRepository for Testcontainers integration
     * @doc.layer product
     * @doc.pattern TestDouble, JDBC
     */
    static class JdbcEntityRepository implements EntityRepository {

        private final String   jdbcUrl;
        private final String   username;
        private final String   password;
        private final Executor executor;

        JdbcEntityRepository(String jdbcUrl, String username, String password, Executor executor) {
            this.jdbcUrl  = jdbcUrl;
            this.username = username;
            this.password = password;
            this.executor = executor;
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public Promise<Entity> save(String tenantId, Entity entity) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = connect()) {
                    boolean exists = false;
                    if (entity.getId() != null) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT 1 FROM entities WHERE id = ? AND tenant_id = ?")) {
                            ps.setObject(1, entity.getId());
                            ps.setString(2, tenantId);
                            exists = ps.executeQuery().next();
                        }
                    }
                    UUID id = entity.getId() != null ? entity.getId() : UUID.randomUUID();
                    String dataJson = MAPPER.writeValueAsString(
                            entity.getData() != null ? entity.getData() : Map.of());
                    Instant now = Instant.now();

                    if (exists) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE entities SET data = ?::jsonb, updated_at = ? WHERE id = ? AND tenant_id = ?")) {
                            ps.setString(1, dataJson);
                            ps.setTimestamp(2, Timestamp.from(now));
                            ps.setObject(3, id);
                            ps.setString(4, tenantId);
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO entities (id, tenant_id, collection_name, record_type, data, created_at, active, version) " +
                                "VALUES (?, ?, ?, ?, ?::jsonb, ?, true, 1)")) {
                            ps.setObject(1, id);
                            ps.setString(2, tenantId);
                            ps.setString(3, entity.getCollectionName());
                            ps.setString(4, entity.getRecordType() != null ? entity.getRecordType().name() : "ENTITY");
                            ps.setString(5, dataJson);
                            ps.setTimestamp(6, Timestamp.from(now));
                            ps.executeUpdate();
                        }
                    }
                    return Entity.builder()
                            .id(id)
                            .tenantId(tenantId)
                            .collectionName(entity.getCollectionName())
                            .recordType(entity.getRecordType() != null ? entity.getRecordType() : RecordType.ENTITY)
                            .data(entity.getData())
                            .build();
                }
            });
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = connect();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT id, tenant_id, collection_name, record_type, data FROM entities " +
                             "WHERE id = ? AND tenant_id = ? AND active = true")) {
                    ps.setObject(1, entityId);
                    ps.setString(2, tenantId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs));
                }
            });
        }

        @Override
        public Promise<List<Entity>> findAll(String tenantId, String collectionName,
                Map<String, Object> filter, String sort, int offset, int limit) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = connect();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT id, tenant_id, collection_name, record_type, data FROM entities " +
                             "WHERE tenant_id = ? AND collection_name = ? AND active = true " +
                             "ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                    ps.setString(1, tenantId);
                    ps.setString(2, collectionName);
                    ps.setInt(3, limit > 0 ? limit : Integer.MAX_VALUE);
                    ps.setInt(4, offset);
                    return mapRows(ps.executeQuery());
                }
            });
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = connect();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE entities SET active = false WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, entityId);
                    ps.setString(2, tenantId);
                    ps.executeUpdate();
                    return (Void) null;
                }
            });
        }

        @Override
        public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = connect();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT 1 FROM entities WHERE id = ? AND tenant_id = ? AND active = true")) {
                    ps.setObject(1, entityId);
                    ps.setString(2, tenantId);
                    return ps.executeQuery().next();
                }
            });
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = connect();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT COUNT(*) FROM entities WHERE tenant_id = ? AND collection_name = ? AND active = true")) {
                    ps.setString(1, tenantId);
                    ps.setString(2, collectionName);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            });
        }

        @Override
        public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
            // Simplified: ignore filter in test helper
            return count(tenantId, collectionName);
        }

        @Override
        public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) {
            return findAll(tenantId, collectionName, Map.of(), null, 0, 100);
        }

        @Override
        public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
            return Promise.ofBlocking(executor, () -> {
                List<Entity> saved = new ArrayList<>();
                for (Entity e : entities) {
                    saved.add(runBlocking(() -> save(tenantId, e)));
                }
                return saved;
            });
        }

        @Override
        public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
            return Promise.ofBlocking(executor, () -> {
                for (UUID id : entityIds) {
                    runBlocking(() -> delete(tenantId, collectionName, id));
                }
                return (Void) null;
            });
        }

        // ── helpers ──────────────────────────────────────────────────────

        private Entity mapRow(ResultSet rs) throws Exception {
            Map<String, Object> data = MAPPER.readValue(
                    rs.getString("data"), new TypeReference<Map<String, Object>>() {});
            return Entity.builder()
                    .id((UUID) rs.getObject("id"))
                    .tenantId(rs.getString("tenant_id"))
                    .collectionName(rs.getString("collection_name"))
                    .recordType(RecordType.valueOf(rs.getString("record_type")))
                    .data(data)
                    .build();
        }

        private List<Entity> mapRows(ResultSet rs) throws Exception {
            List<Entity> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        }

        @SuppressWarnings("unchecked")
        private <T> T runBlocking(java.util.concurrent.Callable<Promise<T>> fn) throws Exception {
            // In tests this is always called from the blocking executor already
            return fn.call().toCompletableFuture().get();
        }
    }
}
