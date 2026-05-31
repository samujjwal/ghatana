/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * PostgreSQL-backed warm-tier implementation of the {@link EventLogStore} SPI.
 *
 * <p>Provides durable, queryable storage for the warm tier of the Data-Cloud
 * event log. Events are stored in the {@code event_log} table (see
 * {@code V005__create_event_log.sql}). All operations are multi-tenant safe:
 * every SQL predicate includes {@code WHERE tenant_id = ?}.
 *
 * <h2>EventLoop Safety</h2>
 * <p>All JDBC calls are dispatched to the {@code executor} via
 * {@code Promise.ofBlocking(executor, …)} so the ActiveJ event loop is never
 * blocked. A virtual-thread executor is used by default (Java 21+), ensuring
 * excellent I/O concurrency at minimal memory overhead.
 *
 * <h2>Tail Subscription</h2>
 * <p>The {@link #tail} method returns a polling subscription that invokes the
 * caller's handler for each new event. Polling is performed on the blocking
 * executor — it never occupies the event loop. Call {@link Subscription#cancel()}
 * to stop the subscription.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL warm-tier EventLogStore SPI implementation
 * @doc.layer product
 * @doc.pattern Repository, EventStore
 * @see EventLogStore
 */
public class WarmTierEventLogStore implements EventLogStore {

    private static final Logger log = LoggerFactory.getLogger(WarmTierEventLogStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private static final String INSERT_SQL = """
            INSERT INTO event_log
                (tenant_id, event_id, event_type, event_version, payload, content_type, headers, idempotency_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            RETURNING offset_value
            """;

    private static final String READ_SQL = """
            SELECT offset_value, event_id, event_type, event_version,
                   payload, content_type, headers, idempotency_key, created_at
              FROM event_log
             WHERE tenant_id = ?
               AND offset_value >= ?
             ORDER BY offset_value ASC
             LIMIT ?
            """;

    private static final String READ_BY_TIME_SQL = """
            SELECT offset_value, event_id, event_type, event_version,
                   payload, content_type, headers, idempotency_key, created_at
              FROM event_log
             WHERE tenant_id = ?
               AND created_at >= ?
               AND created_at <  ?
             ORDER BY created_at ASC, offset_value ASC
             LIMIT ?
            """;

    private static final String READ_BY_TYPE_SQL = """
            SELECT offset_value, event_id, event_type, event_version,
                   payload, content_type, headers, idempotency_key, created_at
              FROM event_log
             WHERE tenant_id = ?
               AND event_type = ?
               AND offset_value >= ?
             ORDER BY offset_value ASC
             LIMIT ?
            """;

    private static final String LATEST_OFFSET_SQL = """
            SELECT MAX(offset_value) FROM event_log WHERE tenant_id = ?
            """;

    private static final String EARLIEST_OFFSET_SQL = """
            SELECT MIN(offset_value) FROM event_log WHERE tenant_id = ?
            """;

    /**
     * Reserved header key used to propagate the assigned {@code offset_value}
     * inside each {@link EventEntry}. Consumers MUST NOT set this header; it is
     * written exclusively by the store on reads.
     */
    public static final String HEADER_OFFSET_KEY = "_x_dc_offset";

    private final DataSource dataSource;
    private final Executor executor;
    private final AtomicLong activeTailSubscribers = new AtomicLong();
    private final AtomicLong totalTailSubscriptions = new AtomicLong();
    private final AtomicLong totalTailPolls = new AtomicLong();
    private final AtomicLong totalTailErrors = new AtomicLong();
    private final AtomicLong totalTailEventsDispatched = new AtomicLong();

    /**
     * Creates a store backed by the given {@link DataSource}.
     *
     * <p>A virtual-thread executor is created automatically. Use
     * {@link #WarmTierEventLogStore(DataSource, Executor)} to supply a custom
     * executor (e.g. for testing with a platform-managed thread pool).
     *
     * @param dataSource JDBC DataSource (HikariCP recommended)
     */
    public WarmTierEventLogStore(DataSource dataSource) {
        this(dataSource, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates a store backed by the given {@link DataSource} and {@link Executor}.
     *
     * @param dataSource JDBC DataSource (HikariCP recommended)
     * @param executor   executor for blocking JDBC calls (must not be the event-loop thread)
     */
    public WarmTierEventLogStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor   = executor;
    }

    // =========================================================================
    //  Append
    // =========================================================================

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        String tenantId = tenant.tenantId();
        return Promise.ofBlocking(executor, () -> doAppend(tenantId, entry));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        if (entries.isEmpty()) return Promise.of(List.of());
        String tenantId = tenant.tenantId();
        return Promise.ofBlocking(executor, () -> doAppendBatchSync(tenantId, entries));
    }

    private List<Offset> doAppendBatchSync(String tenantId, List<EventEntry> entries) throws Exception {
        List<Offset> offsets = new ArrayList<>(entries.size());
        // Use a single connection for the whole batch so they land atomically
        try (Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (EventEntry entry : entries) {
                    offsets.add(doAppendWithConn(conn, tenantId, entry));
                }
                conn.commit();
                return offsets;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    // =========================================================================
    //  Read
    // =========================================================================

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        String tenantId = tenant.tenantId();
        long fromValue = parseLong(from);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_SQL)) {
                ps.setString(1, tenantId);
                ps.setLong(2, fromValue);
                ps.setInt(3, limit);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
            TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        String tenantId = tenant.tenantId();
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_BY_TIME_SQL)) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(startTime));
                ps.setTimestamp(3, Timestamp.from(endTime));
                ps.setInt(4, limit);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> readByType(
            TenantContext tenant, String eventType, Offset from, int limit) {
        String tenantId = tenant.tenantId();
        long fromValue = parseLong(from);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_BY_TYPE_SQL)) {
                ps.setString(1, tenantId);
                ps.setString(2, eventType);
                ps.setLong(3, fromValue);
                ps.setInt(4, limit);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    // =========================================================================
    //  Offset Management
    // =========================================================================

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        String tenantId = tenant.tenantId();
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LATEST_OFFSET_SQL)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long val = rs.getLong(1);
                        return rs.wasNull() ? Offset.zero() : Offset.of(val);
                    }
                }
                return Offset.zero();
            }
        });
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        String tenantId = tenant.tenantId();
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(EARLIEST_OFFSET_SQL)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long val = rs.getLong(1);
                        return rs.wasNull() ? Offset.zero() : Offset.of(val);
                    }
                }
                return Offset.zero();
            }
        });
    }

    // =========================================================================
    //  Streaming (polling tail)
    // =========================================================================

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        return Promise.ofBlocking(executor, () -> {
            long fromOffset = parseLong(from);
            long effectiveOffset = fromOffset < 0 ? readLatestOffsetValue(tenant) + 1 : fromOffset;

            PollingSubscription subscription = new PollingSubscription(
                tenant,
                Offset.of(effectiveOffset),
                handler,
                totalTailPolls,
                totalTailErrors,
                totalTailEventsDispatched,
                activeTailSubscribers::decrementAndGet);

            activeTailSubscribers.incrementAndGet();
            totalTailSubscriptions.incrementAndGet();
            subscription.start(dataSource, executor);
            return (Subscription) subscription;
        });
    }

    /**
     * Runtime telemetry snapshot consumed by Runtime Truth posture reflection.
     */
    public Map<String, Object> tailRuntimeSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("available", true);
        snapshot.put("configurable", false);
        snapshot.put("storeType", getClass().getSimpleName());
        snapshot.put("mode", "polling");
        snapshot.put("pollIntervalMs", PollingSubscription.POLL_DELAY_MS);
        snapshot.put("maxBatchSize", PollingSubscription.POLL_BATCH);
        snapshot.put("activeSubscribers", activeTailSubscribers.get());
        snapshot.put("totalSubscriptions", totalTailSubscriptions.get());
        snapshot.put("totalPolls", totalTailPolls.get());
        snapshot.put("pollErrors", totalTailErrors.get());
        snapshot.put("eventsDispatched", totalTailEventsDispatched.get());
        return Map.copyOf(snapshot);
    }

    // =========================================================================
    //  Internals
    // =========================================================================

    private Offset doAppend(String tenantId, EventEntry entry) throws SQLException, JsonProcessingException {
        try (Connection conn = dataSource.getConnection()) {
            return doAppendWithConn(conn, tenantId, entry);
        }
    }

    private Offset doAppendWithConn(Connection conn, String tenantId, EventEntry entry)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            byte[] payloadBytes = new byte[entry.payload().remaining()];
            entry.payload().duplicate().get(payloadBytes);

            ps.setString(1, tenantId);
            ps.setObject(2, entry.eventId());
            ps.setString(3, entry.eventType());
            ps.setString(4, entry.eventVersion());
            ps.setBytes(5, payloadBytes);
            ps.setString(6, entry.contentType());
            ps.setString(7, MAPPER.writeValueAsString(entry.headers()));
            ps.setString(8, entry.idempotencyKey().orElse(null));
            ps.setTimestamp(9, Timestamp.from(entry.timestamp()));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("INSERT RETURNING returned no rows for event_id=" + entry.eventId());
                }
                return Offset.of(rs.getLong(1));
            }
        }
    }

    private List<EventEntry> mapResultSet(ResultSet rs) throws SQLException, IOException {
        List<EventEntry> result = new ArrayList<>();
        while (rs.next()) {
            result.add(rowToEntry(rs));
        }
        return result;
    }

    /**
     * Maps a ResultSet row to an EventEntry.
     * Embeds the row's {@code offset_value} in the entry headers under key
     * {@value HEADER_OFFSET_KEY} so that consumers can reliably advance their
     * read position even when the IDENTITY sequence has gaps.
     */
    private static EventEntry rowToEntry(ResultSet rs) throws SQLException, IOException {
        UUID eventId         = (UUID) rs.getObject("event_id");
        String eventType     = rs.getString("event_type");
        String eventVersion  = rs.getString("event_version");
        Instant timestamp    = rs.getTimestamp("created_at").toInstant();
        byte[] payloadBytes  = rs.getBytes("payload");
        String contentType   = rs.getString("content_type");
        String headersJson   = rs.getString("headers");
        String idempotencyKey = rs.getString("idempotency_key");
        long offsetValue     = rs.getLong("offset_value");

        Map<String, String> headers = headersJson != null
                ? new HashMap<>(MAPPER.readValue(headersJson, MAP_TYPE))
                : new HashMap<>();
        // Embed the assigned offset so consumers can track position precisely
        headers.put(HEADER_OFFSET_KEY, String.valueOf(offsetValue));

        return new EventEntry(
                eventId,
                eventType,
                eventVersion,
                timestamp,
                ByteBuffer.wrap(payloadBytes != null ? payloadBytes : new byte[0]),
                contentType,
                headers,
                Optional.ofNullable(idempotencyKey),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private static long parseLong(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            // M2: fail-fast — silently resetting to 0 would replay from the beginning
            throw new IllegalArgumentException("Invalid offset value (must be numeric): '" + offset.value() + "'", e);
        }
    }

    private long readLatestOffsetValue(TenantContext tenant) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LATEST_OFFSET_SQL)) {
            ps.setString(1, tenant.tenantId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long val = rs.getLong(1);
                    return rs.wasNull() ? 0L : val;
                }
                return 0L;
            }
        }
    }

    // ==================== Checkpoint Management (P3-03) ====================

    @Override
    public Promise<Optional<Checkpoint>> readCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT stream_name, consumer_group, offset_value, committed_at, idempotency_key " +
                     "FROM event_log_checkpoints " +
                     "WHERE tenant_id = ? AND stream_name = ? AND consumer_group = ?")) {
                ps.setString(1, tenant.tenantId());
                ps.setString(2, stream);
                ps.setString(3, consumerGroup);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Checkpoint(
                            rs.getString("stream_name"),
                            rs.getString("consumer_group"),
                            Offset.of(String.valueOf(rs.getLong("offset_value"))),
                            rs.getTimestamp("committed_at").toInstant(),
                            rs.getString("idempotency_key")
                        ));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<Checkpoint> commitCheckpoint(TenantContext tenant, String stream, String consumerGroup, Offset offset, String idempotencyKey) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    // P3-03: Idempotent checkpoint commit using UPSERT
                    try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO event_log_checkpoints (tenant_id, stream_name, consumer_group, offset_value, committed_at, idempotency_key)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, stream_name, consumer_group)
                        DO UPDATE SET offset_value = EXCLUDED.offset_value, committed_at = EXCLUDED.committed_at, idempotency_key = EXCLUDED.idempotency_key
                        """)) {
                        ps.setString(1, tenant.tenantId());
                        ps.setString(2, stream);
                        ps.setString(3, consumerGroup);
                        ps.setLong(4, Long.parseLong(offset.value()));
                        ps.setTimestamp(5, Timestamp.from(Instant.now()));
                        ps.setString(6, idempotencyKey);
                        ps.executeUpdate();
                    }
                    conn.commit();
                    return new Checkpoint(stream, consumerGroup, offset, Instant.now(), idempotencyKey);
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            }
        });
    }

    @Override
    public Promise<Boolean> deleteCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM event_log_checkpoints WHERE tenant_id = ? AND stream_name = ? AND consumer_group = ?")) {
                ps.setString(1, tenant.tenantId());
                ps.setString(2, stream);
                ps.setString(3, consumerGroup);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        });
    }

    @Override
    public Promise<Map<String, Checkpoint>> getAllCheckpointsWithMetadata(TenantContext tenant) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Checkpoint> result = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT stream_name, consumer_group, offset_value, committed_at, idempotency_key " +
                     "FROM event_log_checkpoints WHERE tenant_id = ?")) {
                ps.setString(1, tenant.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("stream_name") + ":" + rs.getString("consumer_group");
                        result.put(key, new Checkpoint(
                            rs.getString("stream_name"),
                            rs.getString("consumer_group"),
                            Offset.of(String.valueOf(rs.getLong("offset_value"))),
                            rs.getTimestamp("committed_at").toInstant(),
                            rs.getString("idempotency_key")
                        ));
                    }
                }
            }
            return result;
        });
    }

    // ==================== Replay (P3-03) ====================

    @Override
    public Promise<List<EventEntry>> replay(TenantContext tenant, ReplaySpec spec) {
        return Promise.ofBlocking(executor, () -> {
            long fromOffsetValue = Long.parseLong(spec.fromOffset().value());
            long toOffsetValue = spec.toOffset().value().equals("-1") 
                ? Long.MAX_VALUE 
                : Long.parseLong(spec.toOffset().value());
            
            StringBuilder sql = new StringBuilder(
                "SELECT offset_value, event_id, event_type, event_version, payload, content_type, headers, idempotency_key, created_at " +
                "FROM event_log " +
                "WHERE tenant_id = ? AND offset_value >= ? AND offset_value <= ?");
            
            List<Object> params = new ArrayList<>();
            params.add(tenant.tenantId());
            params.add(fromOffsetValue);
            params.add(toOffsetValue);
            
            if (!spec.eventTypes().isEmpty()) {
                sql.append(" AND event_type IN (");
                for (int i = 0; i < spec.eventTypes().size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("?");
                    params.add(spec.eventTypes().get(i));
                }
                sql.append(")");
            }
            
            sql.append(" ORDER BY offset_value ASC");
            
            List<EventEntry> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rowToEntry(rs));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId) {
        // P3-03: For polling subscriptions, cancel is handled via Subscription.cancel()
        // This is a no-op for the polling implementation since subscriptions self-manage
        return Promise.of(null);
    }

    // =========================================================================
    //  Polling Subscription (tail implementation)
    // =========================================================================

    /**
     * Long-poll based subscription: reads new events every 250 ms on a virtual thread.
     * Stops when {@link #cancel()} is called.
     */
    private static final class PollingSubscription implements Subscription {

        static final int POLL_BATCH  = 100;
        static final long POLL_DELAY_MS = 250L;
        private static final Logger log = LoggerFactory.getLogger(PollingSubscription.class);

        private final TenantContext tenant;
        private volatile long nextOffset;
        private final Consumer<EventEntry> handler;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicLong totalTailPolls;
        private final AtomicLong totalTailErrors;
        private final AtomicLong totalTailEventsDispatched;
        private final Runnable onTerminate;

        PollingSubscription(
                TenantContext tenant,
                Offset from,
                Consumer<EventEntry> handler,
                AtomicLong totalTailPolls,
                AtomicLong totalTailErrors,
                AtomicLong totalTailEventsDispatched,
                Runnable onTerminate) {
            this.tenant     = tenant;
            this.nextOffset = parseLong(from);
            this.handler    = handler;
            this.totalTailPolls = totalTailPolls;
            this.totalTailErrors = totalTailErrors;
            this.totalTailEventsDispatched = totalTailEventsDispatched;
            this.onTerminate = onTerminate;
        }

        void start(DataSource dataSource, Executor executor) {
            executor.execute(() -> {
                try {
                    while (!cancelled.get()) {
                        try {
                            totalTailPolls.incrementAndGet();
                            List<EventEntry> batch = poll(dataSource);
                            for (EventEntry entry : batch) {
                                if (cancelled.get()) return;
                                handler.accept(entry);
                                totalTailEventsDispatched.incrementAndGet();
                            }
                            if (batch.isEmpty()) {
                                Thread.sleep(POLL_DELAY_MS);
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (Exception e) {
                            totalTailErrors.incrementAndGet();
                            log.warn("WarmTierEventLogStore tail poll error for tenant={}: {}",
                                    tenant.tenantId(), e.getMessage(), e);
                            try {
                                Thread.sleep(POLL_DELAY_MS * 4); // back-off on error
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                } finally {
                    onTerminate.run();
                }
            });
        }

        private List<EventEntry> poll(DataSource dataSource) throws Exception {
            List<EventEntry> entries = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_SQL)) {
                ps.setString(1, tenant.tenantId());
                ps.setLong(2, nextOffset);
                ps.setInt(3, POLL_BATCH);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(rowToEntry(rs));
                        nextOffset = rs.getLong("offset_value") + 1;
                    }
                }
            }
            return entries;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        private static long parseLong(Offset offset) {
            try {
                return Long.parseLong(offset.value());
            } catch (NumberFormatException e) {
                // M2: fail-fast — silently resetting to 0 would replay from the beginning
                throw new IllegalArgumentException("Invalid offset value (must be numeric): '" + offset.value() + "'", e);
            }
        }
    }
}
