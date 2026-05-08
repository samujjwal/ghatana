package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @doc.type class
 * @doc.purpose File-backed H2 event log store for sovereign Data Cloud deployments
 * @doc.layer product
 * @doc.pattern EventStore
 */
public final class H2SovereignEventLogStore implements EventLogStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(H2SovereignEventLogStore.class);

    public static final String HEADER_OFFSET_KEY = "_x_dc_offset";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() { };

    private final Path databasePath;
    private final DataSource dataSource;
    private final Executor executor;

    public H2SovereignEventLogStore(Path sovereignDirectory) {
        this(sovereignDirectory, Executors.newVirtualThreadPerTaskExecutor());
    }

    public H2SovereignEventLogStore(Path sovereignDirectory, Executor executor) {
        this.databasePath = Objects.requireNonNull(sovereignDirectory, "sovereignDirectory").resolve("events");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.dataSource = createDataSource(databasePath);
        initializeSchema();
    }

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        return Promise.ofBlocking(executor, () -> appendSync(tenant.tenantId(), entry));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        return Promise.ofBlocking(executor, () -> {
            // DC-P0-005: Wrap all appends in a single transaction for atomicity.
            // A failure on any entry rolls back the entire batch so no partial writes occur.
            List<Offset> offsets = new ArrayList<>(entries.size());
            try (Connection connection = dataSource.getConnection()) {
                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    for (EventEntry entry : entries) {
                        offsets.add(appendSync(connection, tenant.tenantId(), entry));
                    }
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
            return offsets;
        });
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        return Promise.ofBlocking(executor, () -> readWithPredicate(
            tenant.tenantId(),
            " AND offset_value >= ? ORDER BY offset_value ASC LIMIT ?",
            statement -> {
                statement.setLong(2, parseOffset(from));
                statement.setInt(3, limit);
            }));
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        return Promise.ofBlocking(executor, () -> readWithPredicate(
            tenant.tenantId(),
            " AND created_at >= ? AND created_at < ? ORDER BY created_at ASC, offset_value ASC LIMIT ?",
            statement -> {
                statement.setTimestamp(2, Timestamp.from(startTime));
                statement.setTimestamp(3, Timestamp.from(endTime));
                statement.setInt(4, limit);
            }));
    }

    @Override
    public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
        return Promise.ofBlocking(executor, () -> readWithPredicate(
            tenant.tenantId(),
            " AND event_type = ? AND offset_value >= ? ORDER BY offset_value ASC LIMIT ?",
            statement -> {
                statement.setString(2, eventType);
                statement.setLong(3, parseOffset(from));
                statement.setInt(4, limit);
            }));
    }

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        return Promise.ofBlocking(executor, () -> queryOffset(tenant.tenantId(), "SELECT MAX(offset_value) FROM dc_event_log WHERE tenant_id = ?"));
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        return Promise.ofBlocking(executor, () -> queryOffset(tenant.tenantId(), "SELECT MIN(offset_value) FROM dc_event_log WHERE tenant_id = ?"));
    }

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        PollingSubscription subscription = new PollingSubscription(tenant.tenantId(), from, handler);
        subscription.start();
        return Promise.of(subscription);
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public void close() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement shutdown = connection.createStatement()) {
            shutdown.execute("SHUTDOWN");
        }
    }

    private Offset appendSync(String tenantId, EventEntry entry) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return appendSync(connection, tenantId, entry);
        }
    }

    /**
     * DC-P0-004: Insert the event. If the idempotency key is already present for the tenant,
     * returns the existing offset rather than inserting a duplicate.
     */
    private Offset appendSync(Connection connection, String tenantId, EventEntry entry) throws Exception {
        // DC-P0-004: Check idempotency key before insert if one is provided.
        if (entry.idempotencyKey().isPresent()) {
            Optional<Offset> existing = findByIdempotencyKey(connection, tenantId, entry.idempotencyKey().get());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO dc_event_log (
                     tenant_id, event_id, event_type, event_version, payload, content_type, headers_json, idempotency_key, created_at
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, tenantId);
            statement.setString(2, entry.eventId().toString());
            statement.setString(3, entry.eventType());
            statement.setString(4, entry.eventVersion());
            statement.setBytes(5, toByteArray(entry.payload()));
            statement.setString(6, entry.contentType());
            statement.setString(7, OBJECT_MAPPER.writeValueAsString(entry.headers()));
            statement.setString(8, entry.idempotencyKey().orElse(null));
            statement.setTimestamp(9, Timestamp.from(entry.timestamp()));
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return Offset.of(generatedKeys.getLong(1));
            }
        }
    }

    /** DC-P0-004: Lookup an existing event by idempotency key scoped to the tenant. */
    private Optional<Offset> findByIdempotencyKey(Connection connection, String tenantId, String idempotencyKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT offset_value FROM dc_event_log WHERE tenant_id = ? AND idempotency_key = ?")) {
            statement.setString(1, tenantId);
            statement.setString(2, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(Offset.of(resultSet.getLong("offset_value")));
                }
                return Optional.empty();
            }
        }
    }

    private List<EventEntry> readWithPredicate(String tenantId, String suffix, SqlBinder binder) throws Exception {
        String sql = """
            SELECT offset_value, event_id, event_type, event_version, payload, content_type, headers_json, idempotency_key, created_at
              FROM dc_event_log
             WHERE tenant_id = ?
            """ + suffix;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EventEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, String> headers = new LinkedHashMap<>(OBJECT_MAPPER.readValue(resultSet.getString("headers_json"), MAP_TYPE));
                    headers.put(HEADER_OFFSET_KEY, Long.toString(resultSet.getLong("offset_value")));
                    entries.add(new EventEntry(
                        UUID.fromString(resultSet.getString("event_id")),
                        resultSet.getString("event_type"),
                        resultSet.getString("event_version"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        ByteBuffer.wrap(resultSet.getBytes("payload")),
                        resultSet.getString("content_type"),
                        headers,
                        Optional.ofNullable(resultSet.getString("idempotency_key"))
                    ));
                }
                return entries;
            }
        }
    }

    private Offset queryOffset(String tenantId, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    long value = resultSet.getLong(1);
                    return resultSet.wasNull() ? Offset.zero() : Offset.of(value);
                }
                return Offset.zero();
            }
        }
    }

    // DC-P2-006: Ordered list of schema migrations. Each entry is applied once and tracked in
    // dc_h2_schema_migrations. Migration IDs are 1-based and must be appended only — never reordered.
    private static final List<String> SCHEMA_MIGRATIONS = List.of(
        // Migration 1: initial dc_event_log schema (DC-P0-004 idempotency index note preserved).
        """
        CREATE TABLE IF NOT EXISTS dc_event_log (
            offset_value BIGINT AUTO_INCREMENT PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL,
            event_id VARCHAR(64) NOT NULL,
            event_type VARCHAR(255) NOT NULL,
            event_version VARCHAR(64) NOT NULL,
            payload BLOB NOT NULL,
            content_type VARCHAR(255) NOT NULL,
            headers_json CLOB NOT NULL,
            idempotency_key VARCHAR(255),
            created_at TIMESTAMP NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_dc_event_log_tenant_offset ON dc_event_log(tenant_id, offset_value)",
        "CREATE INDEX IF NOT EXISTS idx_dc_event_log_tenant_type ON dc_event_log(tenant_id, event_type, offset_value)",
        // DC-P0-004: H2 does not support partial indexes; null idempotency_key values are treated
        // as distinct in unique indexes. Use PostgreSQL for stricter partial-index semantics in production.
        "CREATE INDEX IF NOT EXISTS idx_dc_event_log_idempotency ON dc_event_log(tenant_id, idempotency_key)"
    );

    private static final int INITIAL_MIGRATION_VERSION = 1;

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // DC-P2-006: Create the schema-version tracking table first so all subsequent
                // migrations are idempotent across restarts.
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS dc_h2_schema_migrations (
                            migration_id INTEGER PRIMARY KEY,
                            applied_at TIMESTAMP NOT NULL
                        )
                        """);
                }
                applyMigrations(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize sovereign event log schema", exception);
        }
    }

    /**
     * DC-P2-006: Applies any unapplied schema migrations in order.
     * Migration 1 covers all statements in {@link #SCHEMA_MIGRATIONS}.
     * Future schema changes should add new migration entries to {@link #SCHEMA_MIGRATIONS}
     * and update the version check accordingly.
     */
    private void applyMigrations(Connection connection) throws Exception {
        int maxApplied;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(migration_id) FROM dc_h2_schema_migrations")) {
            maxApplied = rs.next() && rs.getObject(1) != null ? rs.getInt(1) : 0;
        }

        if (maxApplied < INITIAL_MIGRATION_VERSION) {
            try (Statement stmt = connection.createStatement()) {
                for (String sql : SCHEMA_MIGRATIONS) {
                    stmt.execute(sql);
                }
                stmt.execute(
                    "INSERT INTO dc_h2_schema_migrations (migration_id, applied_at) VALUES ("
                    + INITIAL_MIGRATION_VERSION + ", CURRENT_TIMESTAMP())");
            }
        }
        // Future migrations: add `if (maxApplied < 2) { ... }` blocks here for each new version.
    }

    private DataSource createDataSource(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare sovereign event store directory", exception);
        }
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:file:" + path.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        return jdbcDataSource;
    }

    private byte[] toByteArray(ByteBuffer payload) {
        ByteBuffer duplicate = payload.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private long parseOffset(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws Exception;
    }

    private final class PollingSubscription implements Subscription {
        /** DC-PERF-003: configurable base poll interval in ms. */
        private static final long BASE_POLL_MS = 250L;
        /** DC-PERF-003: maximum backoff cap when errors are sustained. */
        private static final long MAX_BACKOFF_MS = 30_000L;

        private final String tenantId;
        private final Consumer<EventEntry> handler;
        private final ScheduledExecutorService scheduler;
        private final AtomicBoolean cancelled;
        private volatile long nextOffset;
        /** Consecutive error count used to compute exponential backoff. */
        private volatile int consecutiveErrors = 0;

        private PollingSubscription(String tenantId, Offset from, Consumer<EventEntry> handler) {
            this.tenantId = tenantId;
            this.handler = handler;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dc-sovereign-tail-" + tenantId);
                thread.setDaemon(true);
                return thread;
            });
            this.cancelled = new AtomicBoolean(false);
            this.nextOffset = Math.max(0L, parseOffset(from));
        }

        private void start() {
            schedulePoll(BASE_POLL_MS);
        }

        private void schedulePoll(long delayMs) {
            if (cancelled.get()) return;
            scheduler.schedule(this::doPoll, delayMs, TimeUnit.MILLISECONDS);
        }

        private void doPoll() {
            if (cancelled.get()) return;
            read(TenantContext.of(tenantId, Map.of()), Offset.of(nextOffset), 100)
                .whenComplete((entries, error) -> {
                    if (error != null) {
                        consecutiveErrors++;
                        long backoffMs = Math.min(BASE_POLL_MS * (1L << Math.min(consecutiveErrors, 7)), MAX_BACKOFF_MS);
                        log.warn("DC-PERF-003: tail poll error for tenant={} (attempt {}), backing off {}ms: {}",
                            tenantId, consecutiveErrors, backoffMs, error.getMessage());
                        schedulePoll(backoffMs);
                        return;
                    }
                    consecutiveErrors = 0;
                    if (entries != null) {
                        for (EventEntry entry : entries) {
                            handler.accept(entry);
                            String headerOffset = entry.headers().get(HEADER_OFFSET_KEY);
                            if (headerOffset != null) {
                                nextOffset = Long.parseLong(headerOffset) + 1L;
                            }
                        }
                    }
                    schedulePoll(BASE_POLL_MS);
                });
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            scheduler.shutdownNow();
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
