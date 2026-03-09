package com.ghatana.eventlog.adapters.jdbc;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.GetEventRequestProto;
import com.ghatana.contracts.event.v1.GetEventResponseProto;
import com.ghatana.contracts.event.v1.IngestBatchRequestProto;
import com.ghatana.contracts.event.v1.IngestBatchResponseProto;
import com.ghatana.contracts.event.v1.IngestRequestProto;
import com.ghatana.contracts.event.v1.IngestResponseProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.contracts.event.v1.UuidProto;
import com.ghatana.eventlog.RetentionPolicy;
import com.ghatana.eventlog.ports.EventStorePort;
import com.ghatana.platform.core.exception.DataAccessException;
import com.ghatana.platform.core.exception.EventNotFoundException;
import com.ghatana.platform.core.exception.InvalidQueryException;
import com.ghatana.platform.observability.Metrics;
import io.micrometer.core.instrument.Timer;
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
import java.util.List;
import java.util.Objects;

/**
 * Minimal JDBC-based EventStore implementation for Day 11.
 * Not production-ready; intended to satisfy migration + insert/select tests.
 */
public final class JdbcEventStore implements EventStorePort {
    private static final Logger log = LoggerFactory.getLogger(JdbcEventStore.class);

    private final DataSource dataSource;
    private final Timer appendTimer;
    private final Timer queryTimer;
    private final Timer getTimer;

    public JdbcEventStore(DataSource dataSource, Metrics metrics) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.appendTimer = metrics.timer("eventlog.append.latency");
        this.queryTimer = metrics.timer("eventlog.query.latency");
        this.getTimer = metrics.timer("eventlog.get.latency");
    }

    @Override
    public IngestResponseProto append(IngestRequestProto request) {
        return appendTimer.record(() -> doAppend(request));
    }

    private IngestResponseProto doAppend(IngestRequestProto request) {
        EventProto e = request.getEvent();
        String sql = "INSERT INTO event_log (tenant_id, type, event_id, payload_json, ts) VALUES (?,?,?,?,?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getTenantId());
            ps.setString(2, e.getType());
            String id = e.hasId() ? e.getId().getValue() : null;
            ps.setString(3, id);
            ps.setString(4, e.getPayloadJson());
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
            return IngestResponseProto.newBuilder().setEvent(e).setDuplicate(false).build();
        } catch (SQLException ex) {
            if (isDuplicateKeyError(ex)) {
                return IngestResponseProto.newBuilder().setEvent(request.getEvent()).setDuplicate(true).build();
            }
            throw new DataAccessException("Failed to append event", ex);
        }
    }

    @Override
    public IngestBatchResponseProto appendBatch(IngestBatchRequestProto request) {
        IngestBatchResponseProto.Builder b = IngestBatchResponseProto.newBuilder();
        int success = 0;
        for (EventProto e : request.getEventsList()) {
            append(IngestRequestProto.newBuilder().setEvent(e).build());
            b.addEvents(e);
            success++;
        }
        b.setSuccessCount(success).setDuplicateCount(0).setErrorCount(0);
        return b.build();
    }

    @Override
    public GetEventResponseProto get(GetEventRequestProto request) {
        return getTimer.record(() -> doGet(request));
    }

    private GetEventResponseProto doGet(GetEventRequestProto request) {
        String sql = "SELECT tenant_id, type, event_id, payload_json FROM event_log WHERE event_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, request.getEventId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new EventNotFoundException(request.getEventId());
                }
                EventProto e = EventProto.newBuilder()
                        .setTenantId(rs.getString("tenant_id"))
                        .setType(rs.getString("type"))
                        .setId(UuidProto.newBuilder().setValue(rs.getString("event_id")).build())
                        .setPayloadJson(rs.getString("payload_json"))
                        .build();
                return GetEventResponseProto.newBuilder().setEvent(e).build();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch event " + request.getEventId(), ex);
        }
    }

    @Override
    public QueryEventsResponseProto query(QueryEventsRequestProto request) {
        return queryTimer.record(() -> {
            StringBuilder sql = new StringBuilder("""
                SELECT tenant_id, type, event_id, payload_json, ts 
                FROM events
                """);
                
            List<Object> params = new ArrayList<>();
            List<String> whereClauses = new ArrayList<>();
            
            // Build WHERE clauses
            if (!request.getTenantId().isEmpty()) {
                whereClauses.add("tenant_id = ?");
                params.add(request.getTenantId());
            }
            if (!request.getTypePrefix().isEmpty()) {
                whereClauses.add("type LIKE ?");
                params.add(request.getTypePrefix() + "%");
            }
            if (request.hasStartTime()) {
                whereClauses.add("ts >= ?");
                params.add(Timestamp.from(Instant.ofEpochMilli(request.getStartTime().getSeconds() * 1000)));
            }
            if (request.hasEndTime()) {
                whereClauses.add("ts <= ?");
                params.add(Timestamp.from(Instant.ofEpochMilli(request.getEndTime().getSeconds() * 1000)));
            }
            
            if (!whereClauses.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
            }
            
            // Add sorting
            sql.append(" ORDER BY ts DESC");
            
            QueryEventsResponseProto.Builder response = QueryEventsResponseProto.newBuilder();
            try (Connection c = dataSource.getConnection(); 
                 PreparedStatement ps = c.prepareStatement(sql.toString())) {
                
                // Set all parameters
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        EventProto e = EventProto.newBuilder()
                            .setTenantId(rs.getString("tenant_id"))
                            .setType(rs.getString("type"))
                            .setId(UuidProto.newBuilder().setValue(rs.getString("event_id")).build())
                            .setPayloadJson(rs.getString("payload_json"))
                            .build();
                        response.addEvents(e);
                    }
                }
                return response.build();
            } catch (SQLException ex) {
                throw new InvalidQueryException("Invalid query parameters", ex);
            }
        });
    }

    public void purgeOlderThan(RetentionPolicy policy) {
        if (policy.maxAge().isZero()) {
            return;
        }
        
        Instant cutoff = Instant.now().minus(policy.maxAge());
        String sql = "DELETE FROM events WHERE ts < ?";
        
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            int deleted = ps.executeUpdate();
            log.info("Purged {} events older than {}", deleted, cutoff);
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to purge old events", ex);
        }
    }

    public void purgeOverSize(RetentionPolicy policy) {
        if (policy.maxBytes() <= 0) {
            return;
        }
        
        String countSql = "SELECT COUNT(*) FROM events";
        String purgeSql = """
            DELETE FROM events 
            WHERE id IN (
                SELECT id FROM events 
                ORDER BY ts ASC 
                LIMIT ?
            )""";
        
        try (Connection c = dataSource.getConnection()) {
            // Get current count
            try (PreparedStatement ps = c.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > policy.maxBytes()) {
                    int toDelete = rs.getInt(1) - (int) policy.maxBytes();
                    try (PreparedStatement purgePs = c.prepareStatement(purgeSql)) {
                        purgePs.setInt(1, toDelete);
                        int deleted = purgePs.executeUpdate();
                        log.info("Purged {} events to maintain size limit", deleted);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to enforce size retention", ex);
        }
    }

    private boolean isDuplicateKeyError(SQLException ex) {
        return "23505".equals(ex.getSQLState()); // PostgreSQL duplicate key error code
    }
}
