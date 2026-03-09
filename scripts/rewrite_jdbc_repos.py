#!/usr/bin/env python3
"""Rewrite JDBC repository implementations to use L3 entity API and V2/V3 schema."""

import os

BASE = "products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/repository/jdbc"

files = {}

# ============================================================
# 1. JdbcIncidentRepository
# ============================================================
files[f"{BASE}/JdbcIncidentRepository.java"] = r"""/*
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
 * JDBC implementation of IncidentRepository.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed incident persistence
 * @doc.layer repository
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
                ps.setObject(i++, incident.getDetectedAt() != null ? Timestamp.from(incident.getDetectedAt()) : null);
                ps.setObject(i++, incident.getInvestigationStartedAt() != null ? Timestamp.from(incident.getInvestigationStartedAt()) : null);
                ps.setObject(i++, incident.getResolvedAt() != null ? Timestamp.from(incident.getResolvedAt()) : null);
                ps.setObject(i++, Timestamp.from(incident.getCreatedAt()));
                ps.setObject(i++, Timestamp.from(incident.getUpdatedAt()));
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
        return queryList("SELECT * FROM incidents WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
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
                     "SELECT * FROM incidents WHERE workspace_id = ? AND status IN ('OPEN', 'ASSIGNED') ORDER BY created_at DESC")) {
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
                     "SELECT * FROM incidents WHERE workspace_id = ? AND project_id = ? AND status IN ('OPEN', 'ASSIGNED') ORDER BY created_at DESC")) {
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
        return countQuery("SELECT COUNT(*) FROM incidents WHERE workspace_id = ? AND status IN ('OPEN', 'ASSIGNED')", workspaceId);
    }

    @Override
    public Promise<Long> countBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM incidents WHERE workspace_id = ? AND severity = ?")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM incidents WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM incidents WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<Incident>> queryList(String sql, UUID workspaceId, UUID secondId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, secondId);
                return collectRows(ps);
            }
        });
    }

    private Promise<Long> countQuery(String sql, UUID workspaceId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, workspaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private List<Incident> collectRows(PreparedStatement ps) throws SQLException {
        List<Incident> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Incident mapRow(ResultSet rs) throws SQLException {
        Incident incident = new Incident();
        incident.setId(rs.getObject("id", UUID.class));
        incident.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        incident.setProjectId(rs.getObject("project_id", UUID.class));
        incident.setTitle(rs.getString("title"));
        incident.setDescription(rs.getString("description"));
        incident.setSeverity(rs.getString("severity"));
        incident.setStatus(rs.getString("status"));
        incident.setPriority(rs.getInt("priority"));
        incident.setAssigneeId(rs.getObject("assignee_id", UUID.class));
        incident.setReporterId(rs.getObject("reporter_id", UUID.class));
        incident.setOwnerId(rs.getObject("owner_id", UUID.class));
        incident.setCategory(rs.getString("category"));
        incident.setTags(rs.getString("tags"));
        incident.setRootCause(rs.getString("root_cause"));
        incident.setResolution(rs.getString("resolution"));
        Timestamp detectedAt = rs.getTimestamp("detected_at");
        if (detectedAt != null) incident.setDetectedAt(detectedAt.toInstant());
        Timestamp investigationStartedAt = rs.getTimestamp("investigation_started_at");
        if (investigationStartedAt != null) incident.setInvestigationStartedAt(investigationStartedAt.toInstant());
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        if (resolvedAt != null) incident.setResolvedAt(resolvedAt.toInstant());
        incident.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        incident.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        incident.setVersion(rs.getInt("version"));
        return incident;
    }
}
"""

# ============================================================
# 2. JdbcAlertRepository
# ============================================================
files[f"{BASE}/JdbcAlertRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import com.ghatana.yappc.api.repository.AlertRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of AlertRepository.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed alert persistence
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class JdbcAlertRepository implements AlertRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcAlertRepository.class);

    private static final String INSERT_SQL = """
        INSERT INTO security_alerts (id, workspace_id, alert_type, severity, title,
            description, source, resource_id, project_id, incident_id,
            rule_id, rule_name, detected_at, assigned_to, metadata,
            affected_resources, status, acknowledged_by, acknowledged_at,
            resolved_by, resolved_at, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
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
        return Promise.ofBlocking(() -> {
            if (alert.getId() == null) {
                alert.setId(UUID.randomUUID());
            }
            alert.setUpdatedAt(Instant.now());
            if (alert.getCreatedAt() == null) {
                alert.setCreatedAt(Instant.now());
            }

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
                ps.setObject(i++, alert.getDetectedAt() != null ? Timestamp.from(alert.getDetectedAt()) : null);
                ps.setObject(i++, alert.getAssignedTo());
                ps.setString(i++, alert.getMetadata());
                ps.setString(i++, alert.getAffectedResources());
                ps.setString(i++, alert.getStatus());
                ps.setObject(i++, alert.getAcknowledgedBy());
                ps.setObject(i++, alert.getAcknowledgedAt() != null ? Timestamp.from(alert.getAcknowledgedAt()) : null);
                ps.setObject(i++, alert.getResolvedBy());
                ps.setObject(i++, alert.getResolvedAt() != null ? Timestamp.from(alert.getResolvedAt()) : null);
                ps.setObject(i++, Timestamp.from(alert.getCreatedAt()));
                ps.setObject(i++, Timestamp.from(alert.getUpdatedAt()));
                ps.setInt(i++, alert.getVersion());
                ps.executeUpdate();
            }
            return alert;
        });
    }

    @Override
    public Promise<SecurityAlert> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findByProject(UUID workspaceId, UUID projectId) {
        return queryList("SELECT * FROM security_alerts WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<SecurityAlert>> findByStatus(UUID workspaceId, String status) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, status);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND severity = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findOpen(UUID workspaceId) {
        return Promise.ofBlocking(() -> {
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
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND project_id = ? AND status = 'OPEN' ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<SecurityAlert>> findByAssignedTo(UUID workspaceId, UUID userId) {
        return queryList("SELECT * FROM security_alerts WHERE workspace_id = ? AND assigned_to = ? ORDER BY created_at DESC",
            workspaceId, userId);
    }

    @Override
    public Promise<Long> countOpenBySeverity(UUID workspaceId, String severity) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM security_alerts WHERE workspace_id = ? AND status = 'OPEN' AND severity = ?")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM security_alerts WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM security_alerts WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<SecurityAlert>> queryList(String sql, UUID workspaceId, UUID secondId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, secondId);
                return collectRows(ps);
            }
        });
    }

    private List<SecurityAlert> collectRows(PreparedStatement ps) throws SQLException {
        List<SecurityAlert> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private SecurityAlert mapRow(ResultSet rs) throws SQLException {
        SecurityAlert alert = new SecurityAlert();
        alert.setId(rs.getObject("id", UUID.class));
        alert.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        alert.setAlertType(rs.getString("alert_type"));
        alert.setSeverity(rs.getString("severity"));
        alert.setTitle(rs.getString("title"));
        alert.setDescription(rs.getString("description"));
        alert.setSource(rs.getString("source"));
        alert.setResourceId(rs.getObject("resource_id", UUID.class));
        alert.setProjectId(rs.getObject("project_id", UUID.class));
        alert.setIncidentId(rs.getObject("incident_id", UUID.class));
        alert.setRuleId(rs.getString("rule_id"));
        alert.setRuleName(rs.getString("rule_name"));
        Timestamp detectedAt = rs.getTimestamp("detected_at");
        if (detectedAt != null) alert.setDetectedAt(detectedAt.toInstant());
        alert.setAssignedTo(rs.getObject("assigned_to", UUID.class));
        alert.setMetadata(rs.getString("metadata"));
        alert.setAffectedResources(rs.getString("affected_resources"));
        alert.setStatus(rs.getString("status"));
        alert.setAcknowledgedBy(rs.getObject("acknowledged_by", UUID.class));
        Timestamp acknowledgedAt = rs.getTimestamp("acknowledged_at");
        if (acknowledgedAt != null) alert.setAcknowledgedAt(acknowledgedAt.toInstant());
        alert.setResolvedBy(rs.getObject("resolved_by", UUID.class));
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        if (resolvedAt != null) alert.setResolvedAt(resolvedAt.toInstant());
        alert.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        alert.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        alert.setVersion(rs.getInt("version"));
        return alert;
    }
}
"""

# ============================================================
# 3. JdbcSecurityScanRepository
# ============================================================
files[f"{BASE}/JdbcSecurityScanRepository.java"] = r"""/*
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

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of SecurityScanRepository.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed security scan persistence
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class JdbcSecurityScanRepository implements SecurityScanRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSecurityScanRepository.class);

    private static final String INSERT_SQL = """
        INSERT INTO scan_jobs (id, workspace_id, project_id, scan_type, status,
            description, config, error_message, findings_count, critical_count,
            high_count, medium_count, low_count, scanner_name, scanner_version,
            target, info_count, created_at, started_at, completed_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        return Promise.ofBlocking(() -> {
            if (scan.getId() == null) {
                scan.setId(UUID.randomUUID());
            }
            scan.setUpdatedAt(Instant.now());
            if (scan.getCreatedAt() == null) {
                scan.setCreatedAt(Instant.now());
            }

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
                ps.setObject(i++, Timestamp.from(scan.getCreatedAt()));
                ps.setObject(i++, scan.getStartedAt() != null ? Timestamp.from(scan.getStartedAt()) : null);
                ps.setObject(i++, scan.getCompletedAt() != null ? Timestamp.from(scan.getCompletedAt()) : null);
                ps.setObject(i++, Timestamp.from(scan.getUpdatedAt()));
                ps.setInt(i++, scan.getVersion());
                ps.executeUpdate();
            }
            return scan;
        });
    }

    @Override
    public Promise<ScanJob> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findByProject(UUID workspaceId, UUID projectId) {
        return queryList("SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<ScanJob>> findByType(UUID workspaceId, ScanType type) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND scan_type = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, type.name());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findByStatus(UUID workspaceId, ScanStatus status) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, status.name());
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
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? AND scan_type = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                ps.setString(3, type.name());
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ScanJob>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {
        return Promise.ofBlocking(() -> {
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
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? AND scan_type = ? ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                ps.setString(3, type.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, ScanStatus status) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM scan_jobs WHERE workspace_id = ? AND status = ?")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<ScanJob>> queryList(String sql, UUID workspaceId, UUID secondId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, secondId);
                return collectRows(ps);
            }
        });
    }

    private List<ScanJob> collectRows(PreparedStatement ps) throws SQLException {
        List<ScanJob> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private ScanJob mapRow(ResultSet rs) throws SQLException {
        ScanJob scan = new ScanJob();
        scan.setId(rs.getObject("id", UUID.class));
        scan.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        scan.setProjectId(rs.getObject("project_id", UUID.class));
        String scanType = rs.getString("scan_type");
        if (scanType != null) scan.setScanType(ScanType.valueOf(scanType));
        String status = rs.getString("status");
        if (status != null) scan.setStatus(ScanStatus.valueOf(status));
        scan.setDescription(rs.getString("description"));
        scan.setConfig(rs.getString("config"));
        scan.setErrorMessage(rs.getString("error_message"));
        scan.setFindingsCount(rs.getInt("findings_count"));
        scan.setCriticalCount(rs.getInt("critical_count"));
        scan.setHighCount(rs.getInt("high_count"));
        scan.setMediumCount(rs.getInt("medium_count"));
        scan.setLowCount(rs.getInt("low_count"));
        scan.setScannerName(rs.getString("scanner_name"));
        scan.setScannerVersion(rs.getString("scanner_version"));
        scan.setTarget(rs.getString("target"));
        scan.setInfoCount(rs.getInt("info_count"));
        scan.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        Timestamp startedAt = rs.getTimestamp("started_at");
        if (startedAt != null) scan.setStartedAt(startedAt.toInstant());
        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) scan.setCompletedAt(completedAt.toInstant());
        scan.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        scan.setVersion(rs.getInt("version"));
        return scan;
    }
}
"""

# ============================================================
# 4. JdbcComplianceRepository
# ============================================================
files[f"{BASE}/JdbcComplianceRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * JDBC implementation of ComplianceRepository.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed compliance assessment persistence
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class JdbcComplianceRepository implements ComplianceRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcComplianceRepository.class);

    private static final String INSERT_SQL = """
        INSERT INTO compliance_assessments (id, workspace_id, framework_id, project_id,
            assessment_date, due_date, assessor_name, assessment_type, notes,
            score, passed_controls, failed_controls, na_controls, total_controls,
            status, details, started_at, assessed_at, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            score = EXCLUDED.score, passed_controls = EXCLUDED.passed_controls,
            failed_controls = EXCLUDED.failed_controls, na_controls = EXCLUDED.na_controls,
            total_controls = EXCLUDED.total_controls, status = EXCLUDED.status,
            details = EXCLUDED.details, assessor_name = EXCLUDED.assessor_name,
            assessment_date = EXCLUDED.assessment_date, notes = EXCLUDED.notes,
            started_at = EXCLUDED.started_at, assessed_at = EXCLUDED.assessed_at,
            updated_at = EXCLUDED.updated_at, version = EXCLUDED.version
        """;

    private final DataSource dataSource;

    @Inject
    public JdbcComplianceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<ComplianceAssessment> save(ComplianceAssessment assessment) {
        return Promise.ofBlocking(() -> {
            if (assessment.getId() == null) {
                assessment.setId(UUID.randomUUID());
            }
            assessment.setUpdatedAt(Instant.now());
            if (assessment.getCreatedAt() == null) {
                assessment.setCreatedAt(Instant.now());
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int i = 1;
                ps.setObject(i++, assessment.getId());
                ps.setObject(i++, assessment.getWorkspaceId());
                ps.setObject(i++, assessment.getFrameworkId());
                ps.setObject(i++, assessment.getProjectId());
                ps.setObject(i++, assessment.getAssessmentDate());
                ps.setObject(i++, assessment.getDueDate());
                ps.setString(i++, assessment.getAssessorName());
                ps.setString(i++, assessment.getAssessmentType());
                ps.setString(i++, assessment.getNotes());
                ps.setInt(i++, assessment.getScore());
                ps.setInt(i++, assessment.getPassedControls());
                ps.setInt(i++, assessment.getFailedControls());
                ps.setInt(i++, assessment.getNaControls());
                ps.setInt(i++, assessment.getTotalControls());
                ps.setString(i++, assessment.getStatus());
                ps.setString(i++, assessment.getDetails());
                ps.setObject(i++, assessment.getStartedAt() != null ? Timestamp.from(assessment.getStartedAt()) : null);
                ps.setObject(i++, assessment.getAssessedAt() != null ? Timestamp.from(assessment.getAssessedAt()) : null);
                ps.setObject(i++, Timestamp.from(assessment.getCreatedAt()));
                ps.setObject(i++, Timestamp.from(assessment.getUpdatedAt()));
                ps.setInt(i++, assessment.getVersion());
                ps.executeUpdate();
            }
            return assessment;
        });
    }

    @Override
    public Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId) {
        return queryList("SELECT * FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId) {
        return queryList("SELECT * FROM compliance_assessments WHERE workspace_id = ? AND framework_id = ? ORDER BY created_at DESC",
            workspaceId, frameworkId);
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? AND framework_id = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                ps.setObject(3, frameworkId);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByStatus(UUID workspaceId, String status) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, status);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByAssessmentType(UUID workspaceId, String assessmentType) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND assessment_type = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, assessmentType);
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, UUID projectId, String status) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? AND status = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                ps.setString(3, status);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<ComplianceAssessment>> queryList(String sql, UUID workspaceId, UUID secondId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, secondId);
                return collectRows(ps);
            }
        });
    }

    private List<ComplianceAssessment> collectRows(PreparedStatement ps) throws SQLException {
        List<ComplianceAssessment> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private ComplianceAssessment mapRow(ResultSet rs) throws SQLException {
        ComplianceAssessment a = new ComplianceAssessment();
        a.setId(rs.getObject("id", UUID.class));
        a.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        a.setFrameworkId(rs.getObject("framework_id", UUID.class));
        a.setProjectId(rs.getObject("project_id", UUID.class));
        Date assessmentDate = rs.getDate("assessment_date");
        if (assessmentDate != null) a.setAssessmentDate(assessmentDate.toLocalDate());
        Date dueDate = rs.getDate("due_date");
        if (dueDate != null) a.setDueDate(dueDate.toLocalDate());
        a.setAssessorName(rs.getString("assessor_name"));
        a.setAssessmentType(rs.getString("assessment_type"));
        a.setNotes(rs.getString("notes"));
        a.setScore(rs.getInt("score"));
        a.setPassedControls(rs.getInt("passed_controls"));
        a.setFailedControls(rs.getInt("failed_controls"));
        a.setNaControls(rs.getInt("na_controls"));
        a.setTotalControls(rs.getInt("total_controls"));
        a.setStatus(rs.getString("status"));
        a.setDetails(rs.getString("details"));
        Timestamp startedAt = rs.getTimestamp("started_at");
        if (startedAt != null) a.setStartedAt(startedAt.toInstant());
        Timestamp assessedAt = rs.getTimestamp("assessed_at");
        if (assessedAt != null) a.setAssessedAt(assessedAt.toInstant());
        a.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        a.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        a.setVersion(rs.getInt("version"));
        return a;
    }
}
"""

# ============================================================
# 5. JdbcProjectRepository
# ============================================================
files[f"{BASE}/JdbcProjectRepository.java"] = r"""/*
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
 * JDBC implementation of ProjectRepository.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed project persistence
 * @doc.layer repository
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
    public Promise<Project> save(Project project) {
        return Promise.ofBlocking(() -> {
            if (project.getId() == null) {
                project.setId(UUID.randomUUID());
            }
            project.setUpdatedAt(Instant.now());
            if (project.getCreatedAt() == null) {
                project.setCreatedAt(Instant.now());
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                int i = 1;
                ps.setObject(i++, project.getId());
                ps.setObject(i++, project.getWorkspaceId());
                ps.setString(i++, project.getName());
                ps.setString(i++, project.getKey());
                ps.setString(i++, project.getDescription());
                ps.setString(i++, project.getRepositoryUrl());
                ps.setString(i++, project.getDefaultBranch());
                ps.setString(i++, project.getLanguage());
                ps.setString(i++, project.getSettings());
                ps.setBoolean(i++, project.isArchived());
                ps.setObject(i++, project.getArchivedAt() != null ? Timestamp.from(project.getArchivedAt()) : null);
                ps.setObject(i++, project.getLastScanAt() != null ? Timestamp.from(project.getLastScanAt()) : null);
                ps.setInt(i++, project.getScanCount());
                ps.setObject(i++, Timestamp.from(project.getCreatedAt()));
                ps.setObject(i++, Timestamp.from(project.getUpdatedAt()));
                ps.setInt(i++, project.getVersion());
                ps.executeUpdate();
            }
            return project;
        });
    }

    @Override
    public Promise<Optional<Project>> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
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
                ps.setObject(1, workspaceId);
                ps.setString(2, key);
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
                ps.setObject(1, workspaceId);
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
                ps.setObject(1, workspaceId);
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
                ps.setObject(1, workspaceId);
                ps.setString(2, "%" + query.toLowerCase() + "%");
                return collectRows(ps);
            }
        });
    }

    @Override
    public Promise<Boolean> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM projects WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM projects WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public Promise<Boolean> isKeyAvailable(UUID workspaceId, String key) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM projects WHERE workspace_id = ? AND key = ?")) {
                ps.setObject(1, workspaceId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return !rs.next();
                }
            }
        });
    }

    @Override
    public Promise<Long> countByWorkspace(UUID workspaceId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM projects WHERE workspace_id = ?")) {
                ps.setObject(1, workspaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    // ========== Helpers ==========

    private List<Project> collectRows(PreparedStatement ps) throws SQLException {
        List<Project> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Project mapRow(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setId(rs.getObject("id", UUID.class));
        p.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        p.setName(rs.getString("name"));
        p.setKey(rs.getString("key"));
        p.setDescription(rs.getString("description"));
        p.setRepositoryUrl(rs.getString("repository_url"));
        p.setDefaultBranch(rs.getString("default_branch"));
        p.setLanguage(rs.getString("language"));
        p.setSettings(rs.getString("settings"));
        p.setArchived(rs.getBoolean("archived"));
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        if (archivedAt != null) p.setArchivedAt(archivedAt.toInstant());
        Timestamp lastScanAt = rs.getTimestamp("last_scan_at");
        if (lastScanAt != null) p.setLastScanAt(lastScanAt.toInstant());
        p.setScanCount(rs.getInt("scan_count"));
        p.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        p.setVersion(rs.getInt("version"));
        return p;
    }
}
"""

# ============================================================
# Write all files
# ============================================================
for path, content in files.items():
    full_path = os.path.join(os.getcwd(), path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, 'w') as f:
        f.write(content.lstrip('\n'))
    print(f"OK: {path}")

print(f"\nWrote {len(files)} files")
