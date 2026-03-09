/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.products.yappc.domain.model.Project;
import com.ghatana.yappc.api.repository.ProjectRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of ProjectRepository backed by L3 Project entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed project persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcProjectRepository implements ProjectRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcProjectRepository.class);

    private static final String INSERT_SQL = """
        INSERT INTO projects (id, workspace_id, name, key, description,
            repository_url, default_branch, language, settings,
            archived, archived_at, last_scan_at, scan_count,
            created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, key = EXCLUDED.key,
            description = EXCLUDED.description,
            repository_url = EXCLUDED.repository_url,
            default_branch = EXCLUDED.default_branch,
            language = EXCLUDED.language, settings = EXCLUDED.settings,
            archived = EXCLUDED.archived, archived_at = EXCLUDED.archived_at,
            last_scan_at = EXCLUDED.last_scan_at, scan_count = EXCLUDED.scan_count,
            updated_at = EXCLUDED.updated_at, version = EXCLUDED.version
        """;

    private final DataSource dataSource;

    @Inject
    public JdbcProjectRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<Project> save(Project p) {
        return Promise.ofBlocking(() -> {
            if (p.getId() == null) p.setId(UUID.randomUUID());
            p.setUpdatedAt(Instant.now());
            if (p.getCreatedAt() == null) p.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int i = 1;
                ps.setString(i++, p.getId().toString());
                ps.setString(i++, p.getWorkspaceId().toString());
                ps.setString(i++, p.getName());
                ps.setString(i++, p.getKey());
                ps.setString(i++, p.getDescription());
                ps.setString(i++, p.getRepositoryUrl());
                ps.setString(i++, p.getDefaultBranch());
                ps.setString(i++, p.getLanguage());
                ps.setString(i++, p.getSettings());
                ps.setBoolean(i++, p.isArchived());
                setTs(ps, i++, p.getArchivedAt());
                setTs(ps, i++, p.getLastScanAt());
                ps.setInt(i++, p.getScanCount());
                ps.setTimestamp(i++, Timestamp.from(p.getCreatedAt()));
                ps.setTimestamp(i++, Timestamp.from(p.getUpdatedAt()));
                ps.setInt(i++, p.getVersion());
                ps.executeUpdate();
            }
            return p;
        });
    }

    @Override
    public Promise<Optional<Project>> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<Optional<Project>> findByKey(UUID workspaceId, String key) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND key = ?")) {
                ps.setObject(1, workspaceId); ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<Project>> findByWorkspace(UUID workspaceId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? ORDER BY name")) {
                ps.setString(1, workspaceId.toString());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<Project>> findActiveByWorkspace(UUID workspaceId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND archived = false ORDER BY name")) {
                ps.setString(1, workspaceId.toString());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<Project>> searchByName(UUID workspaceId, String query) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND LOWER(name) LIKE ? ORDER BY name")) {
                ps.setString(1, workspaceId.toString());
                ps.setString(2, "%" + query.toLowerCase() + "%");
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<Boolean> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE workspace_id = ? AND id = ?")) {
                ps.setString(1, workspaceId.toString()); ps.setString(2, id.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM projects WHERE workspace_id = ? AND id = ?")) {
                ps.setString(1, workspaceId.toString()); ps.setString(2, id.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    @Override
    public Promise<Boolean> isKeyAvailable(UUID workspaceId, String key) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM projects WHERE workspace_id = ? AND key = ?")) {
                ps.setString(1, workspaceId.toString()); ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) { return !rs.next(); }
            }
        });
    }

    @Override
    public Promise<Long> countByWorkspace(UUID workspaceId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM projects WHERE workspace_id = ?")) {
                ps.setString(1, workspaceId.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    // ========== Helpers ==========

    private List<Project> collectRows(PreparedStatement ps) throws SQLException {
        List<Project> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        return list;
    }

    private Project mapRow(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setId(UUID.fromString(rs.getString("id")));
        p.setWorkspaceId(UUID.fromString(rs.getString("workspace_id")));
        p.setName(rs.getString("name"));
        p.setKey(rs.getString("key"));
        p.setDescription(rs.getString("description"));
        p.setRepositoryUrl(rs.getString("repository_url"));
        p.setDefaultBranch(rs.getString("default_branch"));
        p.setLanguage(rs.getString("language"));
        p.setSettings(rs.getString("settings"));
        p.setArchived(rs.getBoolean("archived"));
        p.setArchivedAt(getTs(rs, "archived_at"));
        p.setLastScanAt(getTs(rs, "last_scan_at"));
        p.setScanCount(rs.getInt("scan_count"));
        p.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        p.setVersion(rs.getInt("version"));
        return p;
    }

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }

    private static Instant getTs(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }
}
