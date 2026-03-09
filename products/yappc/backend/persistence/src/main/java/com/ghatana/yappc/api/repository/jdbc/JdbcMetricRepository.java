/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Metric;
import com.ghatana.yappc.api.domain.Metric.*;
import com.ghatana.yappc.api.repository.MetricRepository;
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
 * JDBC implementation of MetricRepository.
  *
 * @doc.type class
 * @doc.purpose jdbc metric repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcMetricRepository implements MetricRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcMetricRepository.class);
    private static final String TABLE = "yappc.metrics";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcMetricRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = Executors.newCachedThreadPool();
        this.mapper = mapper;
    }

    @Override
    public Promise<Metric> save(Metric metric) {
        return Promise.ofBlocking(executor, () -> {
            if (metric.getId() == null) {
                metric.setId(UUID.randomUUID());
            }
            String sql = "INSERT INTO " + TABLE +
                " (id, tenant_id, project_id, name, description, metric_type, unit, " +
                "tags, data_points, summary, metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "name = EXCLUDED.name, description = EXCLUDED.description, unit = EXCLUDED.unit, " +
                "tags = EXCLUDED.tags, data_points = EXCLUDED.data_points, summary = EXCLUDED.summary, " +
                "metadata = EXCLUDED.metadata, updated_at = EXCLUDED.updated_at";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, metric.getId());
                ps.setString(i++, metric.getTenantId());
                ps.setString(i++, metric.getProjectId());
                ps.setString(i++, metric.getName());
                ps.setString(i++, metric.getDescription());
                ps.setString(i++, metric.getType() != null ? metric.getType().name() : "GAUGE");
                ps.setString(i++, metric.getUnit());
                ps.setString(i++, mapper.writeValueAsString(metric.getTags()));
                ps.setString(i++, mapper.writeValueAsString(metric.getDataPoints()));
                ps.setString(i++, mapper.writeValueAsString(metric.getSummary()));
                ps.setString(i++, mapper.writeValueAsString(metric.getMetadata()));
                ps.setTimestamp(i++, Timestamp.from(Instant.now()));
                ps.setTimestamp(i++, Timestamp.from(Instant.now()));
                ps.executeUpdate();
                return metric;
            }
        });
    }

    @Override
    public Promise<Metric> findById(String tenantId, UUID id) {
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
    public Promise<List<Metric>> findByProject(String tenantId, String projectId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?::uuid", tenantId, projectId);
    }

    @Override
    public Promise<List<Metric>> findByName(String tenantId, String name) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND name = ?", tenantId, name);
    }

    @Override
    public Promise<List<Metric>> findByNamePattern(String tenantId, String namePattern) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND name LIKE ?";
            List<Metric> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, namePattern.replace("*", "%"));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<Metric>> findByType(String tenantId, MetricType type) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND metric_type = ?", tenantId, type.name());
    }

    @Override
    public Promise<List<Metric>> findByTags(String tenantId, Map<String, String> tags) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND tags @> ?::jsonb";
            List<Metric> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, mapper.writeValueAsString(tags));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<Metric> addDataPoints(String tenantId, UUID metricId, List<DataPoint> dataPoints) {
        return Promise.ofBlocking(executor, () -> {
            // First get current metric
            String selectSql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            Metric metric = null;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, metricId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) metric = mapRow(rs);
                }
            }
            if (metric == null) return null;

            // Add data points
            for (DataPoint dp : dataPoints) {
                metric.addDataPoint(dp);
            }

            // Update in DB
            String updateSql = "UPDATE " + TABLE + " SET data_points = ?::jsonb, summary = ?::jsonb, updated_at = ? WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, mapper.writeValueAsString(metric.getDataPoints()));
                ps.setString(2, mapper.writeValueAsString(metric.getSummary()));
                ps.setTimestamp(3, Timestamp.from(Instant.now()));
                ps.setString(4, tenantId);
                ps.setObject(5, metricId);
                ps.executeUpdate();
            }
            return metric;
        });
    }

    @Override
    public Promise<List<DataPoint>> queryDataPoints(String tenantId, UUID metricId, Instant start, Instant end) {
        return Promise.ofBlocking(executor, () -> {
            // Get metric and filter data points by time range
            String sql = "SELECT data_points FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, metricId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String dpJson = rs.getString("data_points");
                        if (dpJson != null) {
                            List<DataPoint> all = mapper.readValue(dpJson, new TypeReference<List<DataPoint>>() {});
                            List<DataPoint> filtered = new ArrayList<>();
                            for (DataPoint dp : all) {
                                if (dp.getTimestamp() != null
                                    && !dp.getTimestamp().isBefore(start)
                                    && !dp.getTimestamp().isAfter(end)) {
                                    filtered.add(dp);
                                }
                            }
                            return filtered;
                        }
                    }
                }
            }
            return Collections.emptyList();
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

    @Override
    public Promise<Integer> deleteDataPointsBefore(String tenantId, Instant before) {
        // For JSONB-stored data points, this requires reading, filtering, and writing back
        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            String selectSql = "SELECT id, data_points FROM " + TABLE + " WHERE tenant_id = ?";
            List<UUID> ids = new ArrayList<>();
            List<String> updatedDps = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String dpJson = rs.getString("data_points");
                        if (dpJson != null) {
                            List<DataPoint> all = mapper.readValue(dpJson, new TypeReference<List<DataPoint>>() {});
                            int originalSize = all.size();
                            all.removeIf(dp -> dp.getTimestamp() != null && dp.getTimestamp().isBefore(before));
                            if (all.size() < originalSize) {
                                ids.add(rs.getObject("id", UUID.class));
                                updatedDps.add(mapper.writeValueAsString(all));
                                count += originalSize - all.size();
                            }
                        }
                    }
                }

                // Batch update
                String updateSql = "UPDATE " + TABLE + " SET data_points = ?::jsonb, updated_at = ? WHERE id = ?";
                try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                    for (int i = 0; i < ids.size(); i++) {
                        ups.setString(1, updatedDps.get(i));
                        ups.setTimestamp(2, Timestamp.from(Instant.now()));
                        ups.setObject(3, ids.get(i));
                        ups.addBatch();
                    }
                    ups.executeBatch();
                }
            }
            return count;
        });
    }

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + TABLE + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    private Promise<List<Metric>> queryList(String sql, String... params) {
        return Promise.ofBlocking(executor, () -> {
            List<Metric> results = new ArrayList<>();
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

    @SuppressWarnings("unchecked")
    private Metric mapRow(ResultSet rs) throws SQLException, IOException {
        Metric m = new Metric();
        m.setId(rs.getObject("id", UUID.class));
        m.setTenantId(rs.getString("tenant_id"));
        m.setProjectId(rs.getString("project_id"));
        m.setName(rs.getString("name"));
        m.setDescription(rs.getString("description"));
        try { m.setType(MetricType.valueOf(rs.getString("metric_type"))); } catch (Exception ignored) {}
        m.setUnit(rs.getString("unit"));
        String tagsJson = rs.getString("tags");
        if (tagsJson != null) m.setTags(mapper.readValue(tagsJson, new TypeReference<Map<String, String>>() {}));
        String dpJson = rs.getString("data_points");
        if (dpJson != null) m.setDataPoints(mapper.readValue(dpJson, new TypeReference<List<DataPoint>>() {}));
        String summaryJson = rs.getString("summary");
        if (summaryJson != null && !summaryJson.equals("null")) m.setSummary(mapper.readValue(summaryJson, MetricSummary.class));
        return m;
    }
}
