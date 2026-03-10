/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import com.ghatana.yappc.api.repository.AlertRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.inject.annotation.Inject;
import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of AlertRepository backed by L3 SecurityAlert entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed security alert persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcAlertRepository implements AlertRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcAlertRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });


    private static final String INSERT_SQL = """
        INSERT INTO security_alerts (id, workspace_id, alert_type, severity, title,
            description, source, resource_id, project_id, incident_id,
            rule_id, rule_name, detected_at, assigned_to, metadata,
            affected_resources, status, acknowledged_by, acknowledged_at,
            resolved_by, resolved_at, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb,
                ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            severity = EXCLUDED.severity, title = EXCLUDED.title,
            description = EXCLUDED.description, status = EXCLUDED.status,
            assigned_to = EXCLUDED.assigned_to, metadata = EXCLUDED.metadata,
            acknowledged_by = EXCLUDED.acknowledged_by,
            acknowledged_at = EXCLUDED.acknowledged_at,
            resolved_by = EXCLUDED.resolved_by,
            resolved_at = EXCLUDED.resolved_at,
            updated_at = EXCLUDED.updated_at, version = EXCLUDED.version
        """;

    private final DataSource dataSource;

    @Inject
    public JdbcAlertRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<SecurityAlert> save(SecurityAlert alert) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            if (alert.getId() == null) alert.setId(UUID.randomUUID());
            alert.setUpdatedAt(Instant.now());
            if (alert.getCreatedAt() == null) alert.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int i = 1;
                ps.setObject(i++, alert.getId());
                ps.setObject(i++, alert.getWorkspaceId());
                ps.setString(i++, alert.getAlertType());
                ps.setString(i++, alert.getSeverity());
                ps.setString(i++, alert.getTitle());
                ps.setString(i++, alert.getDescription());
                ps.setString(i++, alert.getSource());
                ps.setObject(i++, alert.getResourceId());
                ps.setObject(i++, alert.getProjectId());
                ps.setObject(i++, alert.getIncidentId());
                ps.setString(i++, alert.getRuleId());
                ps.setString(i++, alert.getRuleName());
                setTs(ps, i++, alert.getDetectedAt());
                ps.setObject(i++, alert.getAssignedTo());
                ps.setString(i++, alert.getMetadata());
                ps.setString(i++, alert.getAffectedResources());
                ps.setString(i++, alert.getStatus());
                ps.setObject(i++, alert.getAcknowledgedBy());
                setTs(ps, i++, alert.getAcknowledgedAt());
                ps.setObject(i++, alert.getResolvedBy());
                setTs(ps, i++, alert.getResolvedAt());
                ps.setTimestamp(i++, Timestamp.from(alert.getCreatedAt()));
                ps.setTimestamp(i++, Timestamp.from(alert.getUpdatedAt()));
                ps.setInt(i++, alert.getVersion());
                ps.executeUpdate();
            }
            return alert;
        });
    }

    @Override
    public Promise<SecurityAlert> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findByProject(UUID workspaceId, UUID projectId) {
        return queryUuids("SELECT * FROM security_alerts WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<SecurityAlert>> findByStatus(UUID workspaceId, String status) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setString(2, status);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND severity = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setString(2, severity);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findOpen(UUID workspaceId) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND status = 'OPEN' ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findOpenByProject(UUID workspaceId, UUID projectId) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND project_id = ? AND status = 'OPEN' ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setObject(2, projectId);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findByAssignedTo(UUID workspaceId, UUID userId) {
        return queryUuids("SELECT * FROM security_alerts WHERE workspace_id = ? AND assigned_to = ? ORDER BY created_at DESC",
            workspaceId, userId);
    }

    @Override
    public Promise<Long> countOpenBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM security_alerts WHERE workspace_id = ? AND status = 'OPEN' AND severity = ?")) {
                ps.setObject(1, workspaceId); ps.setString(2, severity);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM security_alerts WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM security_alerts WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<SecurityAlert>> queryUuids(String sql, UUID a, UUID b) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }
        });
    }

    private List<SecurityAlert> collectRows(PreparedStatement ps) throws SQLException {
        List<SecurityAlert> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        return list;
    }

    private SecurityAlert mapRow(ResultSet rs) throws SQLException {
        SecurityAlert o = new SecurityAlert();
        o.setId(rs.getObject("id", UUID.class));
        o.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        o.setAlertType(rs.getString("alert_type"));
        o.setSeverity(rs.getString("severity"));
        o.setTitle(rs.getString("title"));
        o.setDescription(rs.getString("description"));
        o.setSource(rs.getString("source"));
        o.setResourceId(rs.getObject("resource_id", UUID.class));
        o.setProjectId(rs.getObject("project_id", UUID.class));
        o.setIncidentId(rs.getObject("incident_id", UUID.class));
        o.setRuleId(rs.getString("rule_id"));
        o.setRuleName(rs.getString("rule_name"));
        o.setDetectedAt(getTs(rs, "detected_at"));
        o.setAssignedTo(rs.getObject("assigned_to", UUID.class));
        o.setMetadata(rs.getString("metadata"));
        o.setAffectedResources(rs.getString("affected_resources"));
        o.setStatus(rs.getString("status"));
        o.setAcknowledgedBy(rs.getObject("acknowledged_by", UUID.class));
        o.setAcknowledgedAt(getTs(rs, "acknowledged_at"));
        o.setResolvedBy(rs.getObject("resolved_by", UUID.class));
        o.setResolvedAt(getTs(rs, "resolved_at"));
        o.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        o.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        o.setVersion(rs.getInt("version"));
        return o;
    }

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }

    private static Instant getTs(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }
}
