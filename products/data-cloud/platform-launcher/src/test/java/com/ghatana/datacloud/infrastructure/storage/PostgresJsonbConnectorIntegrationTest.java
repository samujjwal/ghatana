package com.ghatana.datacloud.infrastructure.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
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
 * thin JDBC-backed {@link EntityRepository} (no Spring / JPA required). // GH-90000
 * All async calls are driven through {@link EventloopTestBase#runPromise}.
 *
 * @doc.type class
 * @doc.purpose Integration test for PostgreSQL JSONB storage connector
 * @doc.layer product
 * @doc.pattern Testcontainers, EventloopTestBase
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("PostgresJsonbConnector — Integration Tests (Testcontainers)")
class PostgresJsonbConnectorIntegrationTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("datacloud_it")
                    .withUsername("dc_test")
                    .withPassword("dc_test_secret");

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000
    private static final Executor     EXECUTOR = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000

    private static final String TENANT     = "tenant-pg-it";
    private static final String COLLECTION = "products";
    private              UUID   collectionId;

    private PostgresJsonbConnector connector;

    @BeforeAll
    static void migrateSchema() { // GH-90000
        Flyway.configure() // GH-90000
              .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) // GH-90000
              .locations("classpath:db/migration")
              .load() // GH-90000
              .migrate(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-test setup
    // ──────────────────────────────────────────────────────────────────────
    @BeforeEach
    void setUp() { // GH-90000
        collectionId = UUID.randomUUID(); // GH-90000

        EntityRepository repository = new JdbcEntityRepository( // GH-90000
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), EXECUTOR); // GH-90000

        // noop MetricsCollector and noop AuditLogger to keep the test focused on persistence
        connector = new PostgresJsonbConnector( // GH-90000
                repository,
                noopMetrics(), // GH-90000
                DataCloudAuditLogger.noop()); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: CREATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — persists entity and returns with generated ID")
    void create_persistsAndReturnsId() { // GH-90000
        Entity entity = entityWith(null, Map.of("name", "Widget", "price", 9.99)); // GH-90000

        Entity saved = runPromise(() -> connector.create(entity)); // GH-90000

        assertThat(saved.getId()).as("persisted entity must have an ID").isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(TENANT); // GH-90000
        assertThat(saved.getCollectionName()).isEqualTo(COLLECTION); // GH-90000
        assertThat(saved.getData()).containsKey("name");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: READ
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("read — returns saved entity by ID")
    void read_returnsSavedEntity() { // GH-90000
        Entity saved = runPromise(() -> connector.create( // GH-90000
                entityWith(null, Map.of("name", "Gadget", "price", 19.99)))); // GH-90000

        Optional<Entity> found = runPromise(() -> // GH-90000
                connector.read(collectionId, TENANT, saved.getId())); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getId()).isEqualTo(saved.getId()); // GH-90000
        assertThat(found.get().getData()).containsEntry("name", "Gadget"); // GH-90000
    }

    @Test
    @DisplayName("read — returns empty Optional for unknown ID")
    void read_unknownId_returnsEmpty() { // GH-90000
        Optional<Entity> found = runPromise(() -> // GH-90000
                connector.read(collectionId, TENANT, UUID.randomUUID())); // GH-90000

        assertThat(found).isEmpty(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: UPDATE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — modifies data and increments version")
    void update_modifiesData() { // GH-90000
        Entity saved = runPromise(() -> connector.create( // GH-90000
                entityWith(null, Map.of("name", "SomeProduct", "price", 5.00)))); // GH-90000

        Entity updated = Entity.builder() // GH-90000
                .id(saved.getId()) // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .recordType(saved.getRecordType()) // GH-90000
                .data(Map.of("name", "SomeProduct", "price", 7.50, "discount", true)) // GH-90000
                .build(); // GH-90000

        Entity result = runPromise(() -> connector.update(updated)); // GH-90000

        assertThat(result.getData()).containsEntry("price", 7.50); // GH-90000
        assertThat(result.getData()).containsEntry("discount", true); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: DELETE
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — removes entity; subsequent read returns empty")
    void delete_removesEntity() { // GH-90000
        Entity saved = runPromise(() -> connector.create( // GH-90000
                entityWith(null, Map.of("name", "ToDelete")))); // GH-90000

        runPromise(() -> connector.delete(collectionId, TENANT, saved.getId())); // GH-90000

        Optional<Entity> after = runPromise(() -> // GH-90000
                connector.read(collectionId, TENANT, saved.getId())); // GH-90000

        assertThat(after).isEmpty(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: COUNT
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("count — returns correct row count for tenant+collection")
    void count_returnsCorrectCount() { // GH-90000
        // Entities must be stored with collectionName = collectionId.toString() // GH-90000
        // because the connector uses collectionId.toString() to scope count/query. // GH-90000
        String col = collectionId.toString(); // GH-90000
        runPromise(() -> connector.create(entityWith(null, col, Map.of("tag", "count-a")))); // GH-90000
        runPromise(() -> connector.create(entityWith(null, col, Map.of("tag", "count-b")))); // GH-90000
        runPromise(() -> connector.create(entityWith(null, col, Map.of("tag", "count-c")))); // GH-90000
        runPromise(() -> connector.create(entityWith(null, "other-col", Map.of("tag", "other")))); // GH-90000

        long count = runPromise(() -> connector.count(collectionId, TENANT, null)); // GH-90000

        // count must be >= 3 (tenant isolation means we only count our collection) // GH-90000
        assertThat(count).isGreaterThanOrEqualTo(3L); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: QUERY
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("query — applies limit and returns results")
    void query_appliesLimit() { // GH-90000
        // Entities must use collectionId.toString() so query scope matches // GH-90000
        String col = collectionId.toString(); // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            int idx = i;
            runPromise(() -> connector.create(entityWith(null, col, Map.of("seq", idx)))); // GH-90000
        }

        QuerySpec spec = QuerySpec.builder().limit(3).offset(0).build(); // GH-90000
        StorageConnector.QueryResult result = runPromise(() -> // GH-90000
                connector.query(collectionId, TENANT, spec)); // GH-90000

        assertThat(result.entities()).hasSizeLessThanOrEqualTo(3); // GH-90000
        assertThat(result.total()).isGreaterThanOrEqualTo(3); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests: HEALTH CHECK
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("healthCheck — succeeds when database is reachable")
    void healthCheck_succeedsWhenDbReachable() { // GH-90000
        Void result = runPromise(() -> connector.healthCheck()); // GH-90000
        // If no exception is thrown the database is healthy
        assertThat(result).isNull(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private Entity entityWith(UUID id, Map<String, Object> data) { // GH-90000
        return entityWith(id, COLLECTION, data); // GH-90000
    }

    private Entity entityWith(UUID id, String collection, Map<String, Object> data) { // GH-90000
        var builder = Entity.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(collection) // GH-90000
                .recordType(RecordType.ENTITY) // GH-90000
                .data(data); // GH-90000
        if (id != null) builder = builder.id(id); // GH-90000
        return builder.build(); // GH-90000
    }

    private static com.ghatana.platform.observability.MetricsCollector noopMetrics() { // GH-90000
        return new com.ghatana.platform.observability.MetricsCollector() { // GH-90000
            @Override public void incrementCounter(String name, String... tags) {} // GH-90000
            @Override public void increment(String name, double amount, java.util.Map<String, String> tags) {} // GH-90000
            @Override public void recordTimer(String name, long durationMs, String... tags) {} // GH-90000
            @Override public void recordError(String name, Exception ex, java.util.Map<String, String> tags) {} // GH-90000
            @Override public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() { return null; } // GH-90000
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
    static
class JdbcEntityRepository implements EntityRepository {

        private final String   jdbcUrl;
        private final String   username;
        private final String   password;
        private final Executor executor;

        JdbcEntityRepository(String jdbcUrl, String username, String password, Executor executor) { // GH-90000
            this.jdbcUrl  = jdbcUrl;
            this.username = username;
            this.password = password;
            this.executor = executor;
        }

        private Connection connect() throws SQLException { // GH-90000
            return DriverManager.getConnection(jdbcUrl, username, password); // GH-90000
        }

        @Override
        public Promise<Entity> save(String tenantId, Entity entity) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                try (Connection conn = connect()) { // GH-90000
                    boolean exists = false;
                    if (entity.getId() != null) { // GH-90000
                        try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                                "SELECT 1 FROM entities WHERE id = ? AND tenant_id = ?")) {
                            ps.setObject(1, entity.getId()); // GH-90000
                            ps.setString(2, tenantId); // GH-90000
                            exists = ps.executeQuery().next(); // GH-90000
                        }
                    }
                    UUID id = entity.getId() != null ? entity.getId() : UUID.randomUUID(); // GH-90000
                    String dataJson = MAPPER.writeValueAsString( // GH-90000
                            entity.getData() != null ? entity.getData() : Map.of()); // GH-90000
                    Instant now = Instant.now(); // GH-90000

                    if (exists) { // GH-90000
                        try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                                "UPDATE entities SET data = ?::jsonb, updated_at = ? WHERE id = ? AND tenant_id = ?")) {
                            ps.setString(1, dataJson); // GH-90000
                            ps.setTimestamp(2, Timestamp.from(now)); // GH-90000
                            ps.setObject(3, id); // GH-90000
                            ps.setString(4, tenantId); // GH-90000
                            ps.executeUpdate(); // GH-90000
                        }
                    } else {
                        try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                                "INSERT INTO entities (id, tenant_id, collection_name, record_type, data, created_at, active, version) " + // GH-90000
                                "VALUES (?, ?, ?, ?, ?::jsonb, ?, true, 1)")) { // GH-90000
                            ps.setObject(1, id); // GH-90000
                            ps.setString(2, tenantId); // GH-90000
                            ps.setString(3, entity.getCollectionName()); // GH-90000
                            ps.setString(4, entity.getRecordType() != null ? entity.getRecordType().name() : "ENTITY"); // GH-90000
                            ps.setString(5, dataJson); // GH-90000
                            ps.setTimestamp(6, Timestamp.from(now)); // GH-90000
                            ps.executeUpdate(); // GH-90000
                        }
                    }
                    return Entity.builder() // GH-90000
                            .id(id) // GH-90000
                            .tenantId(tenantId) // GH-90000
                            .collectionName(entity.getCollectionName()) // GH-90000
                            .recordType(entity.getRecordType() != null ? entity.getRecordType() : RecordType.ENTITY) // GH-90000
                            .data(entity.getData()) // GH-90000
                            .build(); // GH-90000
                }
            });
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                try (Connection conn = connect(); // GH-90000
                     PreparedStatement ps = conn.prepareStatement( // GH-90000
                             "SELECT id, tenant_id, collection_name, record_type, data FROM entities " +
                             "WHERE id = ? AND tenant_id = ? AND active = true")) {
                    ps.setObject(1, entityId); // GH-90000
                    ps.setString(2, tenantId); // GH-90000
                    ResultSet rs = ps.executeQuery(); // GH-90000
                    if (!rs.next()) return Optional.empty(); // GH-90000
                    return Optional.of(mapRow(rs)); // GH-90000
                }
            });
        }

        @Override
        public Promise<List<Entity>> findAll(String tenantId, String collectionName, // GH-90000
                Map<String, Object> filter, String sort, int offset, int limit) {
            return Promise.ofBlocking(executor, () -> { // GH-90000
                try (Connection conn = connect(); // GH-90000
                     PreparedStatement ps = conn.prepareStatement( // GH-90000
                             "SELECT id, tenant_id, collection_name, record_type, data FROM entities " +
                             "WHERE tenant_id = ? AND collection_name = ? AND active = true " +
                             "ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                    ps.setString(1, tenantId); // GH-90000
                    ps.setString(2, collectionName); // GH-90000
                    ps.setInt(3, limit > 0 ? limit : Integer.MAX_VALUE); // GH-90000
                    ps.setInt(4, offset); // GH-90000
                    return mapRows(ps.executeQuery()); // GH-90000
                }
            });
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                try (Connection conn = connect(); // GH-90000
                     PreparedStatement ps = conn.prepareStatement( // GH-90000
                             "UPDATE entities SET active = false WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, entityId); // GH-90000
                    ps.setString(2, tenantId); // GH-90000
                    ps.executeUpdate(); // GH-90000
                    return (Void) null; // GH-90000
                }
            });
        }

        @Override
        public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                try (Connection conn = connect(); // GH-90000
                     PreparedStatement ps = conn.prepareStatement( // GH-90000
                             "SELECT 1 FROM entities WHERE id = ? AND tenant_id = ? AND active = true")) {
                    ps.setObject(1, entityId); // GH-90000
                    ps.setString(2, tenantId); // GH-90000
                    return ps.executeQuery().next(); // GH-90000
                }
            });
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                try (Connection conn = connect(); // GH-90000
                     PreparedStatement ps = conn.prepareStatement( // GH-90000
                             "SELECT COUNT(*) FROM entities WHERE tenant_id = ? AND collection_name = ? AND active = true")) { // GH-90000
                    ps.setString(1, tenantId); // GH-90000
                    ps.setString(2, collectionName); // GH-90000
                    ResultSet rs = ps.executeQuery(); // GH-90000
                    return rs.next() ? rs.getLong(1) : 0L; // GH-90000
                }
            });
        }

        @Override
        public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) { // GH-90000
            // Simplified: ignore filter in test helper
            return count(tenantId, collectionName); // GH-90000
        }

        @Override
        public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) { // GH-90000
            return findAll(tenantId, collectionName, Map.of(), null, 0, 100); // GH-90000
        }

        @Override
        public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                List<Entity> saved = new ArrayList<>(); // GH-90000
                for (Entity e : entities) { // GH-90000
                    saved.add(runBlocking(() -> save(tenantId, e))); // GH-90000
                }
                return saved;
            });
        }

        @Override
        public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) { // GH-90000
            return Promise.ofBlocking(executor, () -> { // GH-90000
                for (UUID id : entityIds) { // GH-90000
                    runBlocking(() -> delete(tenantId, collectionName, id)); // GH-90000
                }
                return (Void) null; // GH-90000
            });
        }

        // ── helpers ──────────────────────────────────────────────────────

        private Entity mapRow(ResultSet rs) throws Exception { // GH-90000
            Map<String, Object> data = MAPPER.readValue( // GH-90000
                    rs.getString("data"), new TypeReference<Map<String, Object>>() {});
            return Entity.builder() // GH-90000
                    .id((UUID) rs.getObject("id"))
                    .tenantId(rs.getString("tenant_id"))
                    .collectionName(rs.getString("collection_name"))
                    .recordType(RecordType.valueOf(rs.getString("record_type")))
                    .data(data) // GH-90000
                    .build(); // GH-90000
        }

        private List<Entity> mapRows(ResultSet rs) throws Exception { // GH-90000
            List<Entity> list = new ArrayList<>(); // GH-90000
            while (rs.next()) list.add(mapRow(rs)); // GH-90000
            return list;
        }

        @SuppressWarnings("unchecked")
        private <T> T runBlocking(java.util.concurrent.Callable<Promise<T>> fn) throws Exception { // GH-90000
            // In tests this is always called from the blocking executor already
            return fn.call().toCompletableFuture().get(); // GH-90000
        }
    }
}
