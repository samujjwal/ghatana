package com.ghatana.appplatform.eventstore.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.kafka.KafkaEventOutboxRelay;
import com.ghatana.appplatform.eventstore.kafka.KafkaOutboxCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL JDBC adapter for {@link KafkaOutboxCursor}.
 *
 * <p>Tracks per-event Kafka publish progress via the {@code kafka_outbox_cursor}
 * table (V006 migration). Each method is plain synchronous JDBC — the relay calls
 * them from its own blocking scheduler thread, not the ActiveJ eventloop, so no
 * {@code Promise.ofBlocking} wrapper is needed here.
 *
 * <p>Delivery guarantee: at-least-once. If the relay restarts mid-publish,
 * PENDING events are retried. Duplicate delivery is tolerated because Kafka
 * producer idempotency and consumer-side {@code IdempotencyGuard} together
 * ensure end-to-end exactly-once semantics.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for the Kafka outbox cursor (K05-009)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresKafkaOutboxCursor implements KafkaOutboxCursor {

    private static final Logger log = LoggerFactory.getLogger(PostgresKafkaOutboxCursor.class);

    /** Fetch the oldest PENDING rows and join with event_store for the full event record. */
    private static final String SQL_NEXT_BATCH =
        "SELECT k.cursor_id, e.event_id, e.event_type, e.aggregate_id, " +
        "       e.aggregate_type, e.sequence_number, e.data, e.metadata, " +
        "       e.created_at_utc, e.created_at_bs " +
        "FROM kafka_outbox_cursor k " +
        "JOIN event_store e ON e.event_id = k.event_id " +
        "WHERE k.status = 'PENDING' " +
        "ORDER BY k.created_at_utc ASC " +
        "LIMIT ?";

    private static final String SQL_MARK_PUBLISHED =
        "UPDATE kafka_outbox_cursor " +
        "SET status = 'PUBLISHED', published_at = NOW() " +
        "WHERE cursor_id = ?";

    /** Atomically increments attempt_count and returns the new value via RETURNING. */
    private static final String SQL_INCREMENT_ATTEMPT =
        "UPDATE kafka_outbox_cursor " +
        "SET attempt_count = attempt_count + 1, last_attempt_at = NOW() " +
        "WHERE cursor_id = ? " +
        "RETURNING attempt_count";

    private static final String SQL_MARK_DLQ =
        "UPDATE kafka_outbox_cursor " +
        "SET status = 'DLQ_ROUTED' " +
        "WHERE cursor_id = ?";

    private final DataSource dataSource;
    private final ObjectMapper mapper;

    /**
     * @param dataSource JDBC data source; connection pooling recommended
     */
    public PostgresKafkaOutboxCursor(DataSource dataSource) {
        this.dataSource = dataSource;
        this.mapper     = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns up to {@code limit} PENDING events ordered oldest-first.
     * On JDBC failure, logs the error and returns an empty list so the relay
     * can continue on the next poll cycle rather than crashing.
     */
    @Override
    public List<KafkaEventOutboxRelay.OutboxCandidate> nextBatch(int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_NEXT_BATCH)) {
            ps.setInt(1, limit);
            List<KafkaEventOutboxRelay.OutboxCandidate> results = new ArrayList<>(limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cursorId = rs.getString("cursor_id");
                    AggregateEventRecord record = mapRow(rs);
                    results.add(new KafkaEventOutboxRelay.OutboxCandidate(cursorId, record));
                }
            }
            return results;
        } catch (SQLException e) {
            log.error("nextBatch query failed (limit={}): {}", limit, e.getMessage(), e);
            return List.of();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void markPublished(String cursorId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_MARK_PUBLISHED)) {
            ps.setObject(1, UUID.fromString(cursorId));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("markPublished failed for cursorId={}: {}", cursorId, e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses PostgreSQL's {@code RETURNING} clause to atomically increment and return
     * the new attempt count in a single round-trip.
     *
     * @return new attempt_count, or 0 on JDBC failure (treated as first attempt by relay)
     */
    @Override
    public int incrementAttempt(String cursorId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INCREMENT_ATTEMPT)) {
            ps.setObject(1, UUID.fromString(cursorId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("incrementAttempt failed for cursorId={}: {}", cursorId, e.getMessage(), e);
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void markDlqRouted(String cursorId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_MARK_DLQ)) {
            ps.setObject(1, UUID.fromString(cursorId));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("markDlqRouted failed for cursorId={}: {}", cursorId, e.getMessage(), e);
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private AggregateEventRecord mapRow(ResultSet rs) throws SQLException {
        return AggregateEventRecord.builder()
            .eventId(UUID.fromString(rs.getString("event_id")))
            .eventType(rs.getString("event_type"))
            .aggregateId(UUID.fromString(rs.getString("aggregate_id")))
            .aggregateType(rs.getString("aggregate_type"))
            .sequenceNumber(rs.getLong("sequence_number"))
            .data(fromJson(rs.getString("data")))
            .metadata(fromJson(rs.getString("metadata")))
            .createdAtUtc(rs.getTimestamp("created_at_utc").toInstant())
            .createdAtBs(rs.getString("created_at_bs"))
            .build();
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize JSONB column: " + json, e);
        }
    }
}
