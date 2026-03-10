/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.events.DomainEvent;
import com.ghatana.yappc.api.events.EventRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.inject.annotation.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of the event store.
 *
 * <p>Writes to {@code yappc.domain_events} and {@code yappc.event_outbox}
 * within the same connection (caller manages the outer transaction where needed).
 *
 * @doc.type class
 * @doc.purpose JDBC-backed event store
 * @doc.layer infrastructure
 * @doc.pattern Repository / Event Sourcing
 */
public class JdbcEventRepository implements EventRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcEventRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });


    private static final String INSERT_EVENT =
            "INSERT INTO yappc.domain_events " +
            "(id, event_type, aggregate_id, aggregate_type, tenant_id, user_id, " +
            " sequence_num, payload, metadata, occurred_at, schema_version) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, '{}'::jsonb, ?, ?)";

    private static final String INSERT_OUTBOX =
            "INSERT INTO yappc.event_outbox " +
            "(event_id, tenant_id, event_type, aggregate_id, payload, status, next_retry_at) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', NOW())";

    private static final String SELECT_BY_AGGREGATE =
            "SELECT id, event_type, aggregate_id, aggregate_type, tenant_id, user_id, " +
            "       sequence_num, payload, occurred_at, schema_version " +
            "FROM yappc.domain_events " +
            "WHERE tenant_id = ? AND aggregate_id = ? " +
            "ORDER BY sequence_num ASC";

    private static final String SELECT_BY_TYPE_WINDOW =
            "SELECT id, event_type, aggregate_id, aggregate_type, tenant_id, user_id, " +
            "       sequence_num, payload, occurred_at, schema_version " +
            "FROM yappc.domain_events " +
            "WHERE tenant_id = ? AND event_type = ? " +
            "  AND occurred_at BETWEEN ? AND ? " +
            "ORDER BY occurred_at ASC";

    private static final String SELECT_PENDING_OUTBOX =
            "SELECT id, event_id, tenant_id, event_type, aggregate_id, payload, attempts " +
            "FROM yappc.event_outbox " +
            "WHERE tenant_id = ? AND status IN ('PENDING', 'FAILED') " +
            "  AND next_retry_at <= NOW() " +
            "ORDER BY next_retry_at ASC " +
            "LIMIT ?";

    private static final String UPDATE_OUTBOX_DELIVERED =
            "UPDATE yappc.event_outbox " +
            "SET status = 'DELIVERED', processed_at = NOW() " +
            "WHERE id = ?";

    private static final String UPDATE_OUTBOX_FAILED =
            "UPDATE yappc.event_outbox " +
            "SET status = 'FAILED', attempts = attempts + 1, " +
            "    last_error = ?, next_retry_at = ? " +
            "WHERE id = ?";

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Executor executor;

    @Inject
    public JdbcEventRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper     = mapper;
        this.executor   = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<String> append(DomainEvent event, long sequenceNum) {
        return Promise.ofBlocking(executor, () -> {
            String eventId = event.getEventId();
            String payloadJson;
            try {
                payloadJson = mapper.writeValueAsString(event.toPayload());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialise event payload", e);
            }

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(INSERT_EVENT)) {
                        ps.setObject(1, UUID.fromString(eventId));
                        ps.setString(2, event.getEventType());
                        ps.setString(3, event.getAggregateId());
                        ps.setString(4, event.getAggregateType());
                        ps.setString(5, event.getTenantId());
                        ps.setString(6, event.getUserId());
                        ps.setLong(7, sequenceNum);
                        ps.setString(8, payloadJson);
                        ps.setTimestamp(9, Timestamp.from(event.getOccurredAt()));
                        ps.setInt(10, event.getSchemaVersion());
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement(INSERT_OUTBOX)) {
                        ps.setObject(1, UUID.fromString(eventId));
                        ps.setString(2, event.getTenantId());
                        ps.setString(3, event.getEventType());
                        ps.setString(4, event.getAggregateId());
                        ps.setString(5, payloadJson);
                        ps.executeUpdate();
                    }

                    conn.commit();
                    logger.debug("Appended event {} type={}", eventId, event.getEventType());
                    return eventId;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        });
    }

    @Override
    public Promise<List<StoredEvent>> findByAggregate(String tenantId, String aggregateId) {
        return Promise.ofBlocking(executor, () -> {
            List<StoredEvent> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_AGGREGATE)) {
                ps.setString(1, tenantId);
                ps.setString(2, aggregateId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapStoredEvent(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<StoredEvent>> findByTypeAndWindow(
            String tenantId, String eventType, Instant from, Instant to) {
        return Promise.ofBlocking(executor, () -> {
            List<StoredEvent> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_TYPE_WINDOW)) {
                ps.setString(1, tenantId);
                ps.setString(2, eventType);
                ps.setTimestamp(3, Timestamp.from(from));
                ps.setTimestamp(4, Timestamp.from(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapStoredEvent(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<OutboxEntry>> fetchPendingOutbox(String tenantId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<OutboxEntry> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_PENDING_OUTBOX)) {
                ps.setString(1, tenantId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new OutboxEntry(
                                rs.getString("id"),
                                rs.getString("event_id"),
                                rs.getString("tenant_id"),
                                rs.getString("event_type"),
                                rs.getString("aggregate_id"),
                                rs.getString("payload"),
                                rs.getInt("attempts")));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public Promise<Void> markOutboxDelivered(String outboxId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_OUTBOX_DELIVERED)) {
                ps.setObject(1, UUID.fromString(outboxId));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Void> markOutboxFailed(String outboxId, String error, Instant nextRetry) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_OUTBOX_FAILED)) {
                ps.setString(1, error);
                ps.setTimestamp(2, Timestamp.from(nextRetry));
                ps.setObject(3, UUID.fromString(outboxId));
                ps.executeUpdate();
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------

    private StoredEvent mapStoredEvent(ResultSet rs) throws SQLException {
        return new StoredEvent(
                rs.getString("id"),
                rs.getString("event_type"),
                rs.getString("aggregate_id"),
                rs.getString("aggregate_type"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getLong("sequence_num"),
                rs.getString("payload"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getInt("schema_version"));
    }
}
