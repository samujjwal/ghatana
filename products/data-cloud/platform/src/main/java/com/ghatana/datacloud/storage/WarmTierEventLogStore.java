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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
 * <p>All JDBC calls are wrapped in {@code Promise.ofBlocking(executor, …)} so
 * the ActiveJ event loop is never blocked. A virtual-thread executor is used
 * by default (Java 21+), ensuring excellent I/O concurrency at minimal
 * memory overhead.
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

    private final DataSource dataSource;
    private final Executor executor;

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
        return Promise.ofBlocking(executor, () -> doAppend(tenant.tenantId(), entry));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        return Promise.ofBlocking(executor, () -> {
            List<Offset> offsets = new ArrayList<>(entries.size());
            // Use a single connection for the whole batch so they land atomically
            try (Connection conn = dataSource.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    for (EventEntry entry : entries) {
                        offsets.add(doAppendWithConn(conn, tenant.tenantId(), entry));
                    }
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }
            return offsets;
        });
    }

    // =========================================================================
    //  Read
    // =========================================================================

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        long fromValue = parseLong(from);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_SQL)) {
                ps.setString(1, tenant.tenantId());
                ps.setLong(2, fromValue);
                ps.setInt(3, limit);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
            TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_BY_TIME_SQL)) {
                ps.setString(1, tenant.tenantId());
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
        long fromValue = parseLong(from);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(READ_BY_TYPE_SQL)) {
                ps.setString(1, tenant.tenantId());
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
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(LATEST_OFFSET_SQL)) {
                ps.setString(1, tenant.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long val = rs.getLong(1);
                        return rs.wasNull() ? Offset.zero() : Offset.of(val);
                    }
                }
            }
            return Offset.zero();
        });
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(EARLIEST_OFFSET_SQL)) {
                ps.setString(1, tenant.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long val = rs.getLong(1);
                        return rs.wasNull() ? Offset.zero() : Offset.of(val);
                    }
                }
            }
            return Offset.zero();
        });
    }

    // =========================================================================
    //  Streaming (polling tail)
    // =========================================================================

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        PollingSubscription subscription = new PollingSubscription(tenant, from, handler);
        return Promise.ofBlocking(executor, () -> {
            subscription.start(dataSource, executor);
            return subscription;
        });
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

    private EventEntry rowToEntry(ResultSet rs) throws SQLException, IOException {
        UUID eventId         = (UUID) rs.getObject("event_id");
        String eventType     = rs.getString("event_type");
        String eventVersion  = rs.getString("event_version");
        Instant timestamp    = rs.getTimestamp("created_at").toInstant();
        byte[] payloadBytes  = rs.getBytes("payload");
        String contentType   = rs.getString("content_type");
        String headersJson   = rs.getString("headers");
        String idempotencyKey = rs.getString("idempotency_key");

        Map<String, String> headers = headersJson != null
                ? MAPPER.readValue(headersJson, MAP_TYPE)
                : Map.of();

        return new EventEntry(
                eventId,
                eventType,
                eventVersion,
                timestamp,
                ByteBuffer.wrap(payloadBytes != null ? payloadBytes : new byte[0]),
                contentType,
                headers,
                Optional.ofNullable(idempotencyKey)
        );
    }

    private static long parseLong(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // =========================================================================
    //  Polling Subscription (tail implementation)
    // =========================================================================

    /**
     * Long-poll based subscription: reads new events every 250 ms on a virtual thread.
     * Stops when {@link #cancel()} is called.
     */
    private static final class PollingSubscription implements Subscription {

        private static final int POLL_BATCH  = 100;
        private static final long POLL_DELAY_MS = 250L;
        private static final Logger log = LoggerFactory.getLogger(PollingSubscription.class);

        private final TenantContext tenant;
        private volatile long nextOffset;
        private final Consumer<EventEntry> handler;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        PollingSubscription(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
            this.tenant     = tenant;
            this.nextOffset = parseLong(from);
            this.handler    = handler;
        }

        void start(DataSource dataSource, Executor executor) {
            executor.execute(() -> {
                while (!cancelled.get()) {
                    try {
                        List<EventEntry> batch = poll(dataSource);
                        for (EventEntry entry : batch) {
                            if (cancelled.get()) return;
                            handler.accept(entry);
                        }
                        if (batch.isEmpty()) {
                            Thread.sleep(POLL_DELAY_MS);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
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
            });
        }

        private List<EventEntry> poll(DataSource dataSource) throws Exception {
            WarmTierEventLogStore dummy = new WarmTierEventLogStore(dataSource);
            List<EventEntry> entries = dummy.read(tenant, Offset.of(nextOffset), POLL_BATCH)
                    .getResult(); // safe: this runs off event-loop on a blocking thread
            if (!entries.isEmpty()) {
                nextOffset = parseLong(
                    dummy.getLatestOffset(tenant).getResult()) + 1;
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
                return 0L;
            }
        }
    }
}
