package com.ghatana.appplatform.eventstore.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.domain.ConflictError;
import com.ghatana.appplatform.eventstore.port.AggregateEventStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * JDBC adapter for {@link AggregateEventStore} backed by PostgreSQL {@code event_store}.
 *
 * <p>All blocking JDBC calls are wrapped with {@code Promise.ofBlocking(executor, …)} so
 * they do not block the ActiveJ eventloop thread. The caller supplies an off-eventloop
 * {@link Executor} (e.g., a fixed thread-pool) for these operations.
 *
 * <p>Optimistic concurrency is enforced by the {@code UNIQUE (aggregate_id, sequence_number)}
 * DB constraint. A {@code PSQLException} with SQLState {@code 23505} is mapped to
 * {@link ConflictError}.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL JDBC adapter for the aggregate event store
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAggregateEventStore implements AggregateEventStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresAggregateEventStore.class);

    /** PostgreSQL unique-violation SQLState code. */
    private static final String PG_UNIQUE_VIOLATION = "23505";

    private static final String SQL_NEXT_SEQ =
        "SELECT COALESCE(MAX(sequence_number), -1) + 1 FROM event_store WHERE aggregate_id = ?";

    private static final String SQL_INSERT =
        "INSERT INTO event_store "
        + "(event_id, event_type, aggregate_id, aggregate_type, sequence_number, "
        + " data, metadata, created_at_utc, created_at_bs) "
        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)";

    /** Inserted atomically with the event_store row so the relay can find it immediately. */
    private static final String SQL_INSERT_CURSOR =
        "INSERT INTO kafka_outbox_cursor (event_id, aggregate_type, created_at_utc) "
        + "VALUES (?, ?, ?)";

    private static final String SQL_READ_BASE =
        "SELECT event_id, event_type, aggregate_id, aggregate_type, sequence_number, "
        + "data, metadata, created_at_utc, created_at_bs "
        + "FROM event_store "
        + "WHERE aggregate_id = ? AND sequence_number >= ? ";

    private final DataSource dataSource;
    private final Executor blockingExecutor;
    private final ObjectMapper mapper;

    public PostgresAggregateEventStore(DataSource dataSource, Executor blockingExecutor) {
        this.dataSource       = dataSource;
        this.blockingExecutor = blockingExecutor;
        this.mapper           = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Selects the next sequence number, inserts the record, and returns the persisted
     * {@link AggregateEventRecord}. On {@code 23505} unique violation the returned promise
     * completes exceptionally with {@link ConflictError}.
     */
    @Override
    public Promise<AggregateEventRecord> appendEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            Map<String, Object> data,
            Map<String, Object> metadata) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    long seq = nextSequence(conn, aggregateId);
                    UUID eventId = UUID.randomUUID();
                    Instant now  = Instant.now();

                    // Calendar enrichment hook: resolved by the calendar-service kernel when active.
                    // Returns null (degradation mode) until the calendar service is wired.
                    String createdAtBs = resolveCalendarDate(metadata, now);

                    try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                        ps.setObject(1, eventId);
                        ps.setString(2, eventType);
                        ps.setObject(3, aggregateId);
                        ps.setString(4, aggregateType);
                        ps.setLong(5, seq);
                        ps.setString(6, toJson(data));
                        ps.setString(7, toJson(enrichMetadata(metadata, createdAtBs)));
                        ps.setTimestamp(8, Timestamp.from(now));
                        ps.setString(9, createdAtBs);
                        ps.executeUpdate();
                    }

                    // Outbox cursor row in the same transaction so the relay sees
                    // the event the moment appendEvent() commits (K05-009).
                    try (PreparedStatement cursorPs = conn.prepareStatement(SQL_INSERT_CURSOR)) {
                        cursorPs.setObject(1, eventId);
                        cursorPs.setString(2, aggregateType);
                        cursorPs.setTimestamp(3, Timestamp.from(now));
                        cursorPs.executeUpdate();
                    }

                    conn.commit();
                    log.debug("Appended event {} type={} agg={} seq={}", eventId, eventType, aggregateId, seq);

                    return AggregateEventRecord.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .sequenceNumber(seq)
                        .data(data)
                        .metadata(enrichMetadata(metadata, createdAtBs))
                        .createdAtUtc(now)
                        .createdAtBs(createdAtBs)
                        .build();

                } catch (SQLException e) {
                    conn.rollback();
                    if (PG_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                        throw new ConflictError(aggregateId, -1);
                    }
                    throw e;
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<List<AggregateEventRecord>> getEventsByAggregate(
            UUID aggregateId,
            long fromSequence,
            Long toSequence) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = toSequence != null
                ? SQL_READ_BASE + "AND sequence_number <= ? ORDER BY sequence_number ASC"
                : SQL_READ_BASE + "ORDER BY sequence_number ASC";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setObject(1, aggregateId);
                ps.setLong(2, fromSequence);
                if (toSequence != null) {
                    ps.setLong(3, toSequence);
                }

                List<AggregateEventRecord> records = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(mapRow(rs));
                    }
                }
                return records;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private long nextSequence(Connection conn, UUID aggregateId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_NEXT_SEQ)) {
            ps.setObject(1, aggregateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Calendar enrichment hook.
     * Returns null when the calendar service is unavailable (degradation mode).
     */
    private String resolveCalendarDate(Map<String, Object> metadata, Instant utc) {
        // TODO(calendar-service): inject CalendarDateEnricher and call enricher.toBs(utc)
        return null;
    }

    private Map<String, Object> enrichMetadata(Map<String, Object> original, String createdAtBs) {
        if (createdAtBs != null) {
            return original;
        }
        Map<String, Object> enriched = new HashMap<>(original);
        enriched.put("k15_degraded", true);
        return Map.copyOf(enriched);
    }

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

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize to JSON: " + obj, e);
        }
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
