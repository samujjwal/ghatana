/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.LogEntry;
import com.ghatana.yappc.api.domain.LogEntry.LogLevel;
import com.ghatana.yappc.api.repository.LogEntryRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.inject.annotation.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of LogEntryRepository.
  *
 * @doc.type class
 * @doc.purpose jdbc log entry repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcLogEntryRepository implements LogEntryRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcLogEntryRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });

    private static final String TABLE = "yappc.log_entries";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcLogEntryRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = Executors.newCachedThreadPool();
        this.mapper = mapper;
    }

    @Override
    public Promise<LogEntry> save(LogEntry logEntry) {
        return Promise.ofBlocking(executor, () -> {
            if (logEntry.getId() == null) {
                logEntry.setId(UUID.randomUUID());
            }
            String sql = "INSERT INTO " + TABLE +
                " (id, tenant_id, project_id, service, instance, level, message, logger, thread, " +
                "trace_id, span_id, request_id, user_id, context, stack_trace, timestamp, metadata) " +
                "VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "level = EXCLUDED.level, message = EXCLUDED.message, context = EXCLUDED.context, " +
                "stack_trace = EXCLUDED.stack_trace";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, logEntry.getId());
                ps.setString(i++, logEntry.getTenantId());
                ps.setString(i++, logEntry.getProjectId());
                ps.setString(i++, logEntry.getService());
                ps.setString(i++, logEntry.getInstance());
                ps.setString(i++, logEntry.getLevel() != null ? logEntry.getLevel().name() : "INFO");
                ps.setString(i++, logEntry.getMessage());
                ps.setString(i++, logEntry.getLogger());
                ps.setString(i++, logEntry.getThread());
                ps.setString(i++, logEntry.getTraceId());
                ps.setString(i++, logEntry.getSpanId());
                ps.setString(i++, logEntry.getRequestId());
                ps.setString(i++, logEntry.getUserId());
                ps.setString(i++, mapper.writeValueAsString(logEntry.getContext()));
                ps.setString(i++, logEntry.getStackTrace());
                ps.setTimestamp(i++, Timestamp.from(logEntry.getTimestamp() != null ? logEntry.getTimestamp() : Instant.now()));
                ps.setString(i++, mapper.writeValueAsString(logEntry.getMetadata()));
                ps.executeUpdate();
                return logEntry;
            }
        });
    }

    @Override
    public Promise<List<LogEntry>> saveBatch(List<LogEntry> logEntries) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "INSERT INTO " + TABLE +
                " (id, tenant_id, project_id, service, instance, level, message, logger, thread, " +
                "trace_id, span_id, request_id, user_id, context, stack_trace, timestamp, metadata) " +
                "VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (LogEntry entry : logEntries) {
                    if (entry.getId() == null) entry.setId(UUID.randomUUID());
                    int i = 1;
                    ps.setObject(i++, entry.getId());
                    ps.setString(i++, entry.getTenantId());
                    ps.setString(i++, entry.getProjectId());
                    ps.setString(i++, entry.getService());
                    ps.setString(i++, entry.getInstance());
                    ps.setString(i++, entry.getLevel() != null ? entry.getLevel().name() : "INFO");
                    ps.setString(i++, entry.getMessage());
                    ps.setString(i++, entry.getLogger());
                    ps.setString(i++, entry.getThread());
                    ps.setString(i++, entry.getTraceId());
                    ps.setString(i++, entry.getSpanId());
                    ps.setString(i++, entry.getRequestId());
                    ps.setString(i++, entry.getUserId());
                    ps.setString(i++, mapper.writeValueAsString(entry.getContext()));
                    ps.setString(i++, entry.getStackTrace());
                    ps.setTimestamp(i++, Timestamp.from(entry.getTimestamp() != null ? entry.getTimestamp() : Instant.now()));
                    ps.setString(i++, mapper.writeValueAsString(entry.getMetadata()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return logEntries;
        });
    }

    @Override
    public Promise<LogEntry> findById(String tenantId, UUID id) {
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
    public Promise<List<LogEntry>> findByProject(String tenantId, String projectId, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?::uuid ORDER BY timestamp DESC LIMIT ?",
            limit, tenantId, projectId);
    }

    @Override
    public Promise<List<LogEntry>> findByService(String tenantId, String service, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND service = ? ORDER BY timestamp DESC LIMIT ?",
            limit, tenantId, service);
    }

    @Override
    public Promise<List<LogEntry>> findByLevel(String tenantId, LogLevel level, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND level = ? ORDER BY timestamp DESC LIMIT ?",
            limit, tenantId, level.name());
    }

    @Override
    public Promise<List<LogEntry>> findErrors(String tenantId, String projectId, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE +
            " WHERE tenant_id = ? AND project_id = ?::uuid AND level IN ('ERROR', 'FATAL') ORDER BY timestamp DESC LIMIT ?",
            limit, tenantId, projectId);
    }

    @Override
    public Promise<List<LogEntry>> findByTraceId(String tenantId, String traceId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND trace_id = ? ORDER BY timestamp",
            tenantId, traceId);
    }

    @Override
    public Promise<List<LogEntry>> findByRequestId(String tenantId, String requestId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND request_id = ? ORDER BY timestamp",
            tenantId, requestId);
    }

    @Override
    public Promise<List<LogEntry>> findByUser(String tenantId, String userId, int limit) {
        return queryListWithLimit("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND user_id = ? ORDER BY timestamp DESC LIMIT ?",
            limit, tenantId, userId);
    }

    @Override
    public Promise<List<LogEntry>> searchByMessage(String tenantId, String query, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND message ILIKE ? ORDER BY timestamp DESC LIMIT ?";
            List<LogEntry> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, "%" + query + "%");
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<LogEntry>> findByTimeRange(String tenantId, String projectId, Instant start, Instant end, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE +
                " WHERE tenant_id = ? AND project_id = ?::uuid AND timestamp BETWEEN ? AND ? ORDER BY timestamp DESC LIMIT ?";
            List<LogEntry> results = new ArrayList<>();
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
    public Promise<Long> countByLevel(String tenantId, String projectId, LogLevel level) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?::uuid AND level = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setString(3, level.name());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    @Override
    public Promise<Integer> deleteBefore(String tenantId, Instant before) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND timestamp < ?";
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

    private Promise<List<LogEntry>> queryList(String sql, String... params) {
        return Promise.ofBlocking(executor, () -> {
            List<LogEntry> results = new ArrayList<>();
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

    private Promise<List<LogEntry>> queryListWithLimit(String sql, int limit, String... params) {
        return Promise.ofBlocking(executor, () -> {
            List<LogEntry> results = new ArrayList<>();
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
    private LogEntry mapRow(ResultSet rs) throws SQLException, IOException {
        LogEntry entry = new LogEntry();
        entry.setId(rs.getObject("id", UUID.class));
        entry.setTenantId(rs.getString("tenant_id"));
        entry.setProjectId(rs.getString("project_id"));
        entry.setService(rs.getString("service"));
        entry.setInstance(rs.getString("instance"));
        try { entry.setLevel(LogLevel.valueOf(rs.getString("level"))); } catch (Exception ignored) {}
        entry.setMessage(rs.getString("message"));
        entry.setLogger(rs.getString("logger"));
        entry.setThread(rs.getString("thread"));
        entry.setTraceId(rs.getString("trace_id"));
        entry.setSpanId(rs.getString("span_id"));
        entry.setRequestId(rs.getString("request_id"));
        entry.setUserId(rs.getString("user_id"));
        String contextJson = rs.getString("context");
        if (contextJson != null) entry.setContext(mapper.readValue(contextJson, new TypeReference<Map<String, String>>() {}));
        entry.setStackTrace(rs.getString("stack_trace"));
        Timestamp ts = rs.getTimestamp("timestamp");
        if (ts != null) entry.setTimestamp(ts.toInstant());
        return entry;
    }
}
