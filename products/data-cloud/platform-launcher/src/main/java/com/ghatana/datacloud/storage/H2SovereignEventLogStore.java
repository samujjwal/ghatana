package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.h2.jdbcx.JdbcDataSource;

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
            List<Offset> offsets = new ArrayList<>(entries.size());
            for (EventEntry entry : entries) {
                offsets.add(appendSync(tenant.tenantId(), entry));
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
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SHUTDOWN");
        }
    }

    private Offset appendSync(String tenantId, EventEntry entry) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
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

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
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
                """);
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_dc_event_log_tenant_offset ON dc_event_log(tenant_id, offset_value)");
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_dc_event_log_tenant_type ON dc_event_log(tenant_id, event_type, offset_value)");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize sovereign event log schema", exception);
        }
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
        private final String tenantId;
        private final Consumer<EventEntry> handler;
        private final ScheduledExecutorService scheduler;
        private final AtomicBoolean cancelled;
        private volatile long nextOffset;

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
            scheduler.scheduleAtFixedRate(() -> {
                if (cancelled.get()) {
                    return;
                }
                read(TenantContext.of(tenantId, Map.of()), Offset.of(nextOffset), 100)
                    .whenResult(entries -> {
                        for (EventEntry entry : entries) {
                            handler.accept(entry);
                            String headerOffset = entry.headers().get(HEADER_OFFSET_KEY);
                            if (headerOffset != null) {
                                nextOffset = Long.parseLong(headerOffset) + 1L;
                            }
                        }
                    });
            }, 0L, 250L, TimeUnit.MILLISECONDS);
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
