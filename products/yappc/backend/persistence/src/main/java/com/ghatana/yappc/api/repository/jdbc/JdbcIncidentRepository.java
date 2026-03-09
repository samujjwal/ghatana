/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.products.yappc.domain.model.Incident;
import com.ghatana.yappc.api.repository.IncidentRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of IncidentRepository backed by L3 Incident entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed incident persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcIncidentRepository implements IncidentRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcIncidentRepository.class);

    private static final String INSERT_SQL = """
        INSERT INTO incidents (id, workspace_id, project_id, title, description,
            severity, status, priority, assignee_id, reporter_id, owner_id,
            category, tags, root_cause, resolution, detected_at,
            investigation_started_at, resolved_at, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            title = EXCLUDED.title, description = EXCLUDED.description,
            severity = EXCLUDED.severity, status = EXCLUDED.status,
            priority = EXCLUDED.priority, assignee_id = EXCLUDED.assignee_id,
            owner_id = EXCLUDED.owner_id, category = EXCLUDED.category,
            tags = EXCLUDED.tags, root_cause = EXCLUDED.root_cause,
            resolution = EXCLUDED.resolution,
            investigation_started_at = EXCLUDED.investigation_started_at,
            resolved_at = EXCLUDED.resolved_at,
            updated_at = EXCLUDED.updated_at, version = EXCLUDED.version
        """;

    private final DataSource dataSource;

    @Inject
    public JdbcIncidentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<Incident> save(Incident incident) {
        return Promise.ofBlocking(() -> {
            if (incident.getId() == null) {
                incident.setId(UUID.randomUUID());
            }
            incident.setUpdatedAt(Instant.now());
            if (incident.getCreatedAt() == null) {
                incident.setCreatedAt(Instant.now());
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int i = 1;
                ps.setObject(i++, incident.getId());
                ps.setObject(i++, incident.getWorkspaceId());
                ps.setObject(i++, incident.getProjectId());
                ps.setString(i++, incident.getTitle());
                ps.setString(i++, incident.getDescription());
                ps.setString(i++, incident.getSeverity());
                ps.setString(i++, incident.getStatus());
                ps.setInt(i++, incident.getPriority());
                ps.setObject(i++, incident.getAssigneeId());
                ps.setObject(i++, incident.getReporterId());
                ps.setObject(i++, incident.getOwnerId());
                ps.setString(i++, incident.getCategory());
                ps.setString(i++, incident.getTags());
                ps.setString(i++, incident.getRootCause());
                ps.setString(i++, incident.getResolution());
                setTs(ps, i++, incident.getDetectedAt());
                setTs(ps, i++, incident.getInvestigationStartedAt());
                setTs(ps, i++, incident.getResolvedAt());
                ps.setTimestamp(i++, Timestamp.from(incident.getCreatedAt()));
                ps.setTimestamp(i++, Timestamp.from(incident.getUpdatedAt()));
                ps.setInt(i++, incident.getVersion());
                ps.executeUpdate();
            }
            return incident;
        });
    }

    @Override
    public Promise<Incident> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public Promise<List<Incident>> findByProject(UUID workspaceId, UUID projectId) {
        return queryUuids("SELECT * FROM incidents WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<Incident>> findByStatus(UUID workspaceId, String status) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, status);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<Incident>> findBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND severity = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<Incident>> findOpen(UUID workspaceId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND status IN ('OPEN','ASSIGNED') ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<Incident>> findOpenByProject(UUID workspaceId, UUID projectId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND project_id = ? AND status IN ('OPEN','ASSIGNED') ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<Incident>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setTimestamp(2, Timestamp.from(start));
                ps.setTimestamp(3, Timestamp.from(end));
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<Long> countOpen(UUID workspaceId) {
        return countOne("SELECT COUNT(*) FROM incidents WHERE workspace_id = ? AND status IN ('OPEN','ASSIGNED')", workspaceId);
    }

    @Override
    public Promise<Long> countBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM incidents WHERE workspace_id = ? AND severity = ?")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM incidents WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM incidents WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<Incident>> queryUuids(String sql, UUID a, UUID b) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }
        });
    }

    private Promise<Long> countOne(String sql, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    private List<Incident> collectRows(PreparedStatement ps) throws SQLException {
        List<Incident> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        return list;
    }

    private Incident mapRow(ResultSet rs) throws SQLException {
        Incident o = new Incident();
        o.setId(rs.getObject("id", UUID.class));
        o.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        o.setProjectId(rs.getObject("project_id", UUID.class));
        o.setTitle(rs.getString("title"));
        o.setDescription(rs.getString("description"));
        o.setSeverity(rs.getString("severity"));
        o.setStatus(rs.getString("status"));
        o.setPriority(rs.getInt("priority"));
        o.setAssigneeId(rs.getObject("assignee_id", UUID.class));
        o.setReporterId(rs.getObject("reporter_id", UUID.class));
        o.setOwnerId(rs.getObject("owner_id", UUID.class));
        o.setCategory(rs.getString("category"));
        o.setTags(rs.getString("tags"));
        o.setRootCause(rs.getString("root_cause"));
        o.setResolution(rs.getString("resolution"));
        o.setDetectedAt(getTs(rs, "detected_at"));
        o.setInvestigationStartedAt(getTs(rs, "investigation_started_at"));
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
