package com.ghatana.appplatform.eventstore.replay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replays historical events from the event store matching a {@link ReplayFilter}.
 *
 * <p>Events are fetched in batches and delivered to the handler. Progress is tracked
 * in a {@link ReplayProgress} snapshot returned at completion.
 *
 * <p>Replay always runs on the calling thread — use an {@code Executors.newSingleThreadExecutor()}
 * wrapper for background replay jobs.
 *
 * @doc.type class
 * @doc.purpose Event replay engine for rebuilding state and re-processing events (STORY-K05-021)
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventReplayEngine {

    private static final Logger LOG = Logger.getLogger(EventReplayEngine.class.getName());
    private static final int BATCH_SIZE = 500;

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<>() {};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    public EventReplayEngine(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Replay events matching the filter, delivering each batch to the handler.
     *
     * @param filter  Criteria for which events to replay
     * @param handler Called for each matched event record
     * @return completed ReplayProgress snapshot
     */
    public ReplayProgress replay(ReplayFilter filter, Consumer<AggregateEventRecord> handler) {
        String replayId = UUID.randomUUID().toString();
        long totalEstimate = countEvents(filter);
        ReplayProgress progress = ReplayProgress.start(replayId, totalEstimate);

        LOG.info("[EventReplayEngine] Starting replay=" + replayId
            + " tenant=" + filter.tenantId()
            + " estimatedTotal=" + totalEstimate);

        long replayed = 0;
        long failed = 0;
        long lastSequence = filter.fromSequence() != null ? filter.fromSequence() : 0L;
        Instant lastEventAt = progress.startedAt();

        while (true) {
            List<AggregateEventRecord> batch = fetchBatch(filter, lastSequence, BATCH_SIZE);
            if (batch.isEmpty()) break;

            for (AggregateEventRecord record : batch) {
                try {
                    handler.accept(record);
                    replayed++;
                    lastEventAt = record.createdAtUtc();
                    lastSequence = record.sequenceNumber() + 1;
                } catch (Exception e) {
                    failed++;
                    LOG.log(Level.WARNING, "[EventReplayEngine] Handler error for event="
                        + record.eventId() + " replayId=" + replayId, e);
                }
            }
        }

        ReplayProgress completed = progress
            .withReplayed(replayed, lastEventAt)
            .complete();

        LOG.info("[EventReplayEngine] Replay complete id=" + replayId
            + " replayed=" + replayed + " failed=" + failed);
        return completed;
    }

    // ── Private Query Helpers ─────────────────────────────────────────────────

    private long countEvents(ReplayFilter filter) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM event_store WHERE tenant_id = ?");
        List<Object> params = buildParams(filter);
        appendFilterClauses(sql, filter);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepare(conn, sql.toString(), params)) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            LOG.warning("[EventReplayEngine] Count query failed: " + e.getMessage());
            return -1L;
        }
    }

    private List<AggregateEventRecord> fetchBatch(ReplayFilter filter, long fromSequence, int limit) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT event_id, aggregate_id, aggregate_type, event_type, tenant_id,
                   sequence_number, payload, metadata, created_at
              FROM event_store WHERE tenant_id = ?
            """);
        List<Object> params = buildParams(filter);
        appendFilterClauses(sql, filter);
        sql.append(" AND sequence_number >= ?");
        params.add(fromSequence);
        sql.append(" ORDER BY sequence_number ASC LIMIT ");
        sql.append(limit);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepare(conn, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            List<AggregateEventRecord> batch = new ArrayList<>();
            while (rs.next()) batch.add(mapRecord(rs));
            return batch;
        } catch (SQLException e) {
            throw new RuntimeException("[EventReplayEngine] Batch fetch failed", e);
        }
    }

    private List<Object> buildParams(ReplayFilter filter) {
        List<Object> params = new ArrayList<>();
        params.add(filter.tenantId());
        return params;
    }

    private void appendFilterClauses(StringBuilder sql, ReplayFilter filter) {
        if (filter.aggregateType() != null) sql.append(" AND aggregate_type = ?");
        if (filter.aggregateId() != null)   sql.append(" AND aggregate_id = ?");
        if (filter.eventType() != null)     sql.append(" AND event_type = ?");
        if (filter.fromTimestamp() != null) sql.append(" AND created_at >= ?");
        if (filter.toTimestamp() != null)   sql.append(" AND created_at <= ?");
        if (filter.toSequence() != null)    sql.append(" AND sequence_number <= ?");
    }

    private PreparedStatement prepare(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String s)       ps.setString(i + 1, s);
            else if (p instanceof Long l)    ps.setLong(i + 1, l);
            else if (p instanceof Instant ts) ps.setTimestamp(i + 1, Timestamp.from(ts));
        }
        return ps;
    }

    private AggregateEventRecord mapRecord(ResultSet rs) throws SQLException {
        return AggregateEventRecord.builder()
            .eventId(UUID.fromString(rs.getString("event_id")))
            .aggregateId(UUID.fromString(rs.getString("aggregate_id")))
            .aggregateType(rs.getString("aggregate_type"))
            .eventType(rs.getString("event_type"))
            .sequenceNumber(rs.getLong("sequence_number"))
            .data(parseJson(rs.getString("data")))
            .metadata(parseJson(rs.getString("metadata")))
            .createdAtUtc(rs.getTimestamp("created_at_utc").toInstant())
            .createdAtBs(rs.getString("created_at_bs"))
            .build();
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            LOG.warning("[EventReplayEngine] Failed to parse JSON field: " + e.getMessage());
            return Map.of();
        }
    }
}
