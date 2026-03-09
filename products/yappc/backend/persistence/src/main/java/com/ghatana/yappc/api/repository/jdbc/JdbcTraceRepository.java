/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Trace;
import com.ghatana.yappc.api.domain.Trace.*;
import com.ghatana.yappc.api.repository.TraceRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of TraceRepository.
  *
 * @doc.type class
 * @doc.purpose jdbc trace repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcTraceRepository implements TraceRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTraceRepository.class);
    private static final String TABLE = "yappc.traces";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcTraceRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = Executors.newCachedThreadPool();
        this.mapper = mapper;
    }

    @Override
    public Promise<Trace> save(Trace trace) {
        return Promise.ofBlocking(executor, () -> {
            if (trace.getId() == null) {
                trace.setId(UUID.randomUUID());
            }
            String sql = "INSERT INTO " + TABLE +
                " (id, tenant_id, project_id, trace_id, name, service, operation, status, spans, " +
                "start_time, end_time, duration_ms, user_id, request_id, tags, metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "status = EXCLUDED.status, spans = EXCLUDED.spans, end_time = EXCLUDED.end_time, " +
                "duration_ms = EXCLUDED.duration_ms, tags = EXCLUDED.tags, metadata = EXCLUDED.metadata, " +
                "updated_at = EXCLUDED.updated_at";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, trace.getId());
                ps.setString(i++, trace.getTenantId());
                ps.setString(i++, trace.getProjectId());
                ps.setString(i++, trace.getTraceId());
                ps.setString(i++, trace.getName());
                ps.setString(i++, trace.getService());
                ps.setString(i++, trace.getOperation());
                ps.setString(i++, trace.getStatus() != null ? trace.getStatus().name() : "IN_PROGRESS");
                ps.setString(i++, mapper.writeValueAsString(trace.getSpans()));
                ps.setTimestamp(i++, Timestamp.from(trace.getStartTime() != null ? trace.getStartTime() : Instant.now()));
                ps.setTimestamp(i++, trace.getEndTime() != null ? Timestamp.from(trace.getEndTime()) : null);
                ps.setLong(i++, trace.getDurationMs());
                ps.setString(i++, trace.getUserId());
                ps.setString(i++, trace.getRequestId());
                ps.setString(i++, mapper.writeValueAsString(trace.getTags()));
                ps.setString(i++, mapper.writeValueAsString(trace.getMetadata()));
                ps.setTimestamp(i++, Timestamp.from(Instant.now()));
                ps.setTimestamp(i++, Timestamp.from(Instant.now()));
                ps.executeUpdate();
                return trace;
            }
        });
    }

    @Override
    public Promise<Trace> findById(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            }
            return null;
        });
    }

    @Override
    public Promise<Trace> findByTraceId(String tenantId, String traceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND trace_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, traceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            }
            return null;
        });
    }

    @Override
    public Promise<List<Trace>> findByProject(String tenantId, String projectId, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND project_id = ?::uuid ORDER BY start_time DESC LIMIT ?", limit, tenantId, projectId);
    }

    @Override
    public Promise<List<Trace>> findByService(String tenantId, String service, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND service = ? ORDER BY start_time DESC LIMIT ?", limit, tenantId, service);
    }

    @Override
    public Promise<List<Trace>> findByOperation(String tenantId, String service, String operation, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND service = ? AND operation = ? ORDER BY start_time DESC LIMIT ?",
            limit, tenantId, service, operation);
    }

    @Override
    public Promise<List<Trace>> findByStatus(String tenantId, TraceStatus status, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND status = ? ORDER BY start_time DESC LIMIT ?", limit, tenantId, status.name());
    }

    @Override
    public Promise<List<Trace>> findErrors(String tenantId, String projectId, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND project_id = ?::uuid AND status = 'ERROR' ORDER BY start_time DESC LIMIT ?",
            limit, tenantId, projectId);
    }

    @Override
    public Promise<List<Trace>> findByUser(String tenantId, String userId, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND user_id = ? ORDER BY start_time DESC LIMIT ?", limit, tenantId, userId);
    }

    @Override
    public Promise<List<Trace>> findByRequestId(String tenantId, String requestId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND request_id = ? ORDER BY start_time",
            tenantId, requestId);
    }

    @Override
    public Promise<List<Trace>> findSlowTraces(String tenantId, String projectId, long minDurationMs, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE +
                " WHERE tenant_id = ? AND project_id = ?::uuid AND duration_ms >= ? ORDER BY duration_ms DESC LIMIT ?";
            List<Trace> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setLong(3, minDurationMs);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<Trace>> findByTimeRange(String tenantId, String projectId, Instant start, Instant end, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE +
                " WHERE tenant_id = ? AND project_id = ?::uuid AND start_time BETWEEN ? AND ? ORDER BY start_time DESC LIMIT ?";
            List<Trace> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setTimestamp(3, Timestamp.from(start));
                ps.setTimestamp(4, Timestamp.from(end));
                ps.setInt(5, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<Trace>> findByTag(String tenantId, String tagKey, String tagValue, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND tags->>? = ? ORDER BY start_time DESC LIMIT ?";
            List<Trace> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, tagKey);
                ps.setString(3, tagValue);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<Long> countByStatus(String tenantId, String projectId, TraceStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?::uuid AND status = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setString(3, status.name());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    @Override
    public Promise<Double> getAverageDuration(String tenantId, String projectId, String service) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT AVG(duration_ms) FROM " + TABLE +
                " WHERE tenant_id = ? AND project_id = ?::uuid AND service = ? AND status = 'COMPLETED'";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setString(3, service);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getDouble(1);
                }
            }
        });
    }

    @Override
    public Promise<Integer> deleteBefore(String tenantId, Instant before) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND start_time < ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(before));
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Promise<Void> delete(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM " + TABLE + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Promise<List<Trace>> queryList(String sql, String... params) {
        return Promise.ofBlocking(executor, () -> {
            List<Trace> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    private Promise<List<Trace>> queryListWithLimit(String sql, int limit, String... params) {
        return Promise.ofBlocking(executor, () -> {
            List<Trace> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
                ps.setInt(params.length + 1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @SuppressWarnings("unchecked")
    private Trace mapRow(ResultSet rs) throws SQLException, IOException {
        Trace t = new Trace();
        t.setId(rs.getObject("id", UUID.class));
        t.setTenantId(rs.getString("tenant_id"));
        t.setProjectId(rs.getString("project_id"));
        t.setTraceId(rs.getString("trace_id"));
        t.setName(rs.getString("name"));
        t.setService(rs.getString("service"));
        t.setOperation(rs.getString("operation"));
        try { t.setStatus(TraceStatus.valueOf(rs.getString("status"))); } catch (Exception ignored) {}
        String spansJson = rs.getString("spans");
        if (spansJson != null) t.setSpans(mapper.readValue(spansJson, new TypeReference<List<Span>>() {}));
        Timestamp start = rs.getTimestamp("start_time");
        if (start != null) t.setStartTime(start.toInstant());
        Timestamp end = rs.getTimestamp("end_time");
        if (end != null) t.setEndTime(end.toInstant());
        t.setDurationMs(rs.getLong("duration_ms"));
        t.setUserId(rs.getString("user_id"));
        t.setRequestId(rs.getString("request_id"));
        String tagsJson = rs.getString("tags");
        if (tagsJson != null) t.setTags(mapper.readValue(tagsJson, new TypeReference<Map<String, String>>() {}));
        return t;
    }
}
