/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.yappc.api.repository.SecurityScanRepository;
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
 * JDBC implementation of SecurityScanRepository backed by L3 ScanJob entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed security scan persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcSecurityScanRepository implements SecurityScanRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSecurityScanRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });


    private static final String INSERT_SQL = """
        INSERT INTO scan_jobs (id, workspace_id, project_id, scan_type, status,
            description, config, error_message, findings_count, critical_count,
            high_count, medium_count, low_count, scanner_name, scanner_version,
            target, info_count, created_at, started_at, completed_at, updated_at, version)
        Values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            status = EXCLUDED.status, error_message = EXCLUDED.error_message,
            findings_count = EXCLUDED.findings_count, critical_count = EXCLUDED.critical_count,
            high_count = EXCLUDED.high_count, medium_count = EXCLUDED.medium_count,
            low_count = EXCLUDED.low_count, info_count = EXCLUDED.info_count,
            started_at = EXCLUDED.started_at, completed_at = EXCLUDED.completed_at,
            updated_at = EXCLUDED.updated_at, version = EXCLUDED.version
        """;

    private final DataSource dataSource;

    @Inject
    public JdbcSecurityScanRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<ScanJob> save(ScanJob scan) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            if (scan.getId() == null) scan.setId(UUID.randomUUID());
            scan.setUpdatedAt(Instant.now());
            if (scan.getCreatedAt() == null) scan.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int i = 1;
                ps.setObject(i++, scan.getId());
                ps.setObject(i++, scan.getWorkspaceId());
                ps.setObject(i++, scan.getProjectId());
                ps.setString(i++, scan.getScanType() != null ? scan.getScanType().name() : null);
                ps.setString(i++, scan.getStatus() != null ? scan.getStatus().name() : null);
                ps.setString(i++, scan.getDescription());
                ps.setString(i++, scan.getConfig());
                ps.setString(i++, scan.getErrorMessage());
                ps.setInt(i++, scan.getFindingsCount());
                ps.setInt(i++, scan.getCriticalCount());
                ps.setInt(i++, scan.getHighCount());
                ps.setInt(i++, scan.getMediumCount());
                ps.setInt(i++, scan.getLowCount());
                ps.setString(i++, scan.getScannerName());
                ps.setString(i++, scan.getScannerVersion());
                ps.setString(i++, scan.getTarget());
                ps.setInt(i++, scan.getInfoCount());
                ps.setTimestamp(i++, Timestamp.from(scan.getCreatedAt()));
                setTs(ps, i++, scan.getStartedAt());
                setTs(ps, i++, scan.getCompletedAt());
                ps.setTimestamp(i++, Timestamp.from(scan.getUpdatedAt()));
                ps.setInt(i++, scan.getVersion());
                ps.executeUpdate();
            }
            return scan;
        });
    }

    @Override
    public Promise<ScanJob> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findByProject(UUID workspaceId, UUID projectId) {
        return queryUuids("SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<ScanJob>> findByType(UUID workspaceId, ScanType type) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND scan_type = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setString(2, type.name());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findByStatus(UUID workspaceId, ScanStatus status) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setString(2, status.name());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findRunning(UUID workspaceId) {
        return findByStatus(workspaceId, ScanStatus.RUNNING);
    }

    @Override
    public Promise<List<ScanJob>> findByProjectAndType(UUID workspaceId, UUID projectId, ScanType type) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? AND scan_type = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setString(3, type.name());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setTimestamp(2, Timestamp.from(start));
                ps.setTimestamp(3, Timestamp.from(end));
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<ScanJob> findLatestByProjectAndType(UUID workspaceId, UUID projectId, ScanType type) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? AND scan_type = ? ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setString(3, type.name());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
            }
        });
    }

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, ScanStatus status) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM scan_jobs WHERE workspace_id = ? AND status = ?")) {
                ps.setObject(1, workspaceId); ps.setString(2, status.name());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<ScanJob>> queryUuids(String sql, UUID a, UUID b) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }
        });
    }

    private List<ScanJob> collectRows(PreparedStatement ps) throws SQLException {
        List<ScanJob> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        return list;
    }

    private ScanJob mapRow(ResultSet rs) throws SQLException {
        ScanJob o = new ScanJob();
        o.setId(rs.getObject("id", UUID.class));
        o.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        o.setProjectId(rs.getObject("project_id", UUID.class));
        String scanType = rs.getString("scan_type");
        if (scanType != null) o.setScanType(ScanType.valueOf(scanType));
        String status = rs.getString("status");
        if (status != null) o.setStatus(ScanStatus.valueOf(status));
        o.setDescription(rs.getString("description"));
        o.setConfig(rs.getString("config"));
        o.setErrorMessage(rs.getString("error_message"));
        o.setFindingsCount(rs.getInt("findings_count"));
        o.setCriticalCount(rs.getInt("critical_count"));
        o.setHighCount(rs.getInt("high_count"));
        o.setMediumCount(rs.getInt("medium_count"));
        o.setLowCount(rs.getInt("low_count"));
        o.setScannerName(rs.getString("scanner_name"));
        o.setScannerVersion(rs.getString("scanner_version"));
        o.setTarget(rs.getString("target"));
        o.setInfoCount(rs.getInt("info_count"));
        o.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        o.setStartedAt(getTs(rs, "started_at"));
        o.setCompletedAt(getTs(rs, "completed_at"));
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
