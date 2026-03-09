#!/usr/bin/env python3
"""Rewrite 5 JDBC repos to align with L3 entities and V2/V3 schema."""
import os, sys

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/repository/jdbc")

TQ = '\"\"\"'  # Java text block delimiter

def write(name, content):
    path = os.path.join(BASE, name)
    with open(path, 'w') as f:
        f.write(content)
    print(f"OK: {name} ({len(content)} bytes)")


# ============================================================
# 1. JdbcIncidentRepository
# ============================================================
write("JdbcIncidentRepository.java", f'''/*
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
public class JdbcIncidentRepository implements IncidentRepository {{

    private static final Logger logger = LoggerFactory.getLogger(JdbcIncidentRepository.class);

    private static final String INSERT_SQL = {TQ}
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
        {TQ};

    private final DataSource dataSource;

    @Inject
    public JdbcIncidentRepository(DataSource dataSource) {{
        this.dataSource = dataSource;
    }}

    @Override
    public Promise<Incident> save(Incident incident) {{
        return Promise.ofBlocking(() -> {{
            if (incident.getId() == null) {{
                incident.setId(UUID.randomUUID());
            }}
            incident.setUpdatedAt(Instant.now());
            if (incident.getCreatedAt() == null) {{
                incident.setCreatedAt(Instant.now());
            }}
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {{
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
            }}
            return incident;
        }});
    }}

    @Override
    public Promise<Incident> findById(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{
                    return rs.next() ? mapRow(rs) : null;
                }}
            }}
        }});
    }}

    @Override
    public Promise<List<Incident>> findByProject(UUID workspaceId, UUID projectId) {{
        return queryUuids("SELECT * FROM incidents WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }}

    @Override
    public Promise<List<Incident>> findByStatus(UUID workspaceId, String status) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                ps.setString(2, status);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<Incident>> findBySeverity(UUID workspaceId, String severity) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND severity = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<Incident>> findOpen(UUID workspaceId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND status IN ('OPEN','ASSIGNED') ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<Incident>> findOpenByProject(UUID workspaceId, UUID projectId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND project_id = ? AND status IN ('OPEN','ASSIGNED') ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                ps.setObject(2, projectId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<Incident>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM incidents WHERE workspace_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                ps.setTimestamp(2, Timestamp.from(start));
                ps.setTimestamp(3, Timestamp.from(end));
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<Long> countOpen(UUID workspaceId) {{
        return countOne("SELECT COUNT(*) FROM incidents WHERE workspace_id = ? AND status IN ('OPEN','ASSIGNED')", workspaceId);
    }}

    @Override
    public Promise<Long> countBySeverity(UUID workspaceId, String severity) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM incidents WHERE workspace_id = ? AND severity = ?")) {{
                ps.setObject(1, workspaceId);
                ps.setString(2, severity);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? rs.getLong(1) : 0L; }}
            }}
        }});
    }}

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM incidents WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }}
            return null;
        }});
    }}

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM incidents WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next(); }}
            }}
        }});
    }}

    // ========== Helpers ==========

    private Promise<List<Incident>> queryUuids(String sql, UUID a, UUID b) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {{
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }}
        }});
    }}

    private Promise<Long> countOne(String sql, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {{
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? rs.getLong(1) : 0L; }}
            }}
        }});
    }}

    private List<Incident> collectRows(PreparedStatement ps) throws SQLException {{
        List<Incident> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {{ while (rs.next()) list.add(mapRow(rs)); }}
        return list;
    }}

    private Incident mapRow(ResultSet rs) throws SQLException {{
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
    }}

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {{
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }}

    private static Instant getTs(ResultSet rs, String col) throws SQLException {{
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }}
}}
''')


# ============================================================
# 2. JdbcAlertRepository
# ============================================================
write("JdbcAlertRepository.java", f'''/*
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
 * JDBC implementation of AlertRepository backed by L3 SecurityAlert entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed security alert persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcAlertRepository implements AlertRepository {{

    private static final Logger logger = LoggerFactory.getLogger(JdbcAlertRepository.class);

    private static final String INSERT_SQL = {TQ}
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
        {TQ};

    private final DataSource dataSource;

    @Inject
    public JdbcAlertRepository(DataSource dataSource) {{
        this.dataSource = dataSource;
    }}

    @Override
    public Promise<SecurityAlert> save(SecurityAlert alert) {{
        return Promise.ofBlocking(() -> {{
            if (alert.getId() == null) alert.setId(UUID.randomUUID());
            alert.setUpdatedAt(Instant.now());
            if (alert.getCreatedAt() == null) alert.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {{
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
            }}
            return alert;
        }});
    }}

    @Override
    public Promise<SecurityAlert> findById(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? mapRow(rs) : null; }}
            }}
        }});
    }}

    @Override
    public Promise<List<SecurityAlert>> findByProject(UUID workspaceId, UUID projectId) {{
        return queryUuids("SELECT * FROM security_alerts WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }}

    @Override
    public Promise<List<SecurityAlert>> findByStatus(UUID workspaceId, String status) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setString(2, status);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<SecurityAlert>> findBySeverity(UUID workspaceId, String severity) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND severity = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setString(2, severity);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<SecurityAlert>> findOpen(UUID workspaceId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND status = 'OPEN' ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<SecurityAlert>> findOpenByProject(UUID workspaceId, UUID projectId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM security_alerts WHERE workspace_id = ? AND project_id = ? AND status = 'OPEN' ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, projectId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<SecurityAlert>> findByAssignedTo(UUID workspaceId, UUID userId) {{
        return queryUuids("SELECT * FROM security_alerts WHERE workspace_id = ? AND assigned_to = ? ORDER BY created_at DESC",
            workspaceId, userId);
    }}

    @Override
    public Promise<Long> countOpenBySeverity(UUID workspaceId, String severity) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM security_alerts WHERE workspace_id = ? AND status = 'OPEN' AND severity = ?")) {{
                ps.setObject(1, workspaceId); ps.setString(2, severity);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? rs.getLong(1) : 0L; }}
            }}
        }});
    }}

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM security_alerts WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }}
            return null;
        }});
    }}

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM security_alerts WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next(); }}
            }}
        }});
    }}

    // ========== Helpers ==========

    private Promise<List<SecurityAlert>> queryUuids(String sql, UUID a, UUID b) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {{
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }}
        }});
    }}

    private List<SecurityAlert> collectRows(PreparedStatement ps) throws SQLException {{
        List<SecurityAlert> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {{ while (rs.next()) list.add(mapRow(rs)); }}
        return list;
    }}

    private SecurityAlert mapRow(ResultSet rs) throws SQLException {{
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
    }}

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {{
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }}

    private static Instant getTs(ResultSet rs, String col) throws SQLException {{
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }}
}}
''')


# ============================================================
# 3. JdbcSecurityScanRepository
# ============================================================
write("JdbcSecurityScanRepository.java", f'''/*
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
 * JDBC implementation of SecurityScanRepository backed by L3 ScanJob entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed security scan persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcSecurityScanRepository implements SecurityScanRepository {{

    private static final Logger logger = LoggerFactory.getLogger(JdbcSecurityScanRepository.class);

    private static final String INSERT_SQL = {TQ}
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
        {TQ};

    private final DataSource dataSource;

    @Inject
    public JdbcSecurityScanRepository(DataSource dataSource) {{
        this.dataSource = dataSource;
    }}

    @Override
    public Promise<ScanJob> save(ScanJob scan) {{
        return Promise.ofBlocking(() -> {{
            if (scan.getId() == null) scan.setId(UUID.randomUUID());
            scan.setUpdatedAt(Instant.now());
            if (scan.getCreatedAt() == null) scan.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {{
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
            }}
            return scan;
        }});
    }}

    @Override
    public Promise<ScanJob> findById(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? mapRow(rs) : null; }}
            }}
        }});
    }}

    @Override
    public Promise<List<ScanJob>> findByProject(UUID workspaceId, UUID projectId) {{
        return queryUuids("SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }}

    @Override
    public Promise<List<ScanJob>> findByType(UUID workspaceId, ScanType type) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND scan_type = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setString(2, type.name());
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<ScanJob>> findByStatus(UUID workspaceId, ScanStatus status) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setString(2, status.name());
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<ScanJob>> findRunning(UUID workspaceId) {{
        return findByStatus(workspaceId, ScanStatus.RUNNING);
    }}

    @Override
    public Promise<List<ScanJob>> findByProjectAndType(UUID workspaceId, UUID projectId, ScanType type) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? AND scan_type = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setString(3, type.name());
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<ScanJob>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId);
                ps.setTimestamp(2, Timestamp.from(start));
                ps.setTimestamp(3, Timestamp.from(end));
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<ScanJob> findLatestByProjectAndType(UUID workspaceId, UUID projectId, ScanType type) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM scan_jobs WHERE workspace_id = ? AND project_id = ? AND scan_type = ? ORDER BY created_at DESC LIMIT 1")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setString(3, type.name());
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? mapRow(rs) : null; }}
            }}
        }});
    }}

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, ScanStatus status) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM scan_jobs WHERE workspace_id = ? AND status = ?")) {{
                ps.setObject(1, workspaceId); ps.setString(2, status.name());
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? rs.getLong(1) : 0L; }}
            }}
        }});
    }}

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }}
            return null;
        }});
    }}

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM scan_jobs WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next(); }}
            }}
        }});
    }}

    // ========== Helpers ==========

    private Promise<List<ScanJob>> queryUuids(String sql, UUID a, UUID b) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {{
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }}
        }});
    }}

    private List<ScanJob> collectRows(PreparedStatement ps) throws SQLException {{
        List<ScanJob> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {{ while (rs.next()) list.add(mapRow(rs)); }}
        return list;
    }}

    private ScanJob mapRow(ResultSet rs) throws SQLException {{
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
    }}

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {{
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }}

    private static Instant getTs(ResultSet rs, String col) throws SQLException {{
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }}
}}
''')


# ============================================================
# 4. JdbcComplianceRepository
# ============================================================
write("JdbcComplianceRepository.java", f'''/*
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
 * JDBC implementation of ComplianceRepository backed by L3 ComplianceAssessment entity.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed compliance assessment persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcComplianceRepository implements ComplianceRepository {{

    private static final Logger logger = LoggerFactory.getLogger(JdbcComplianceRepository.class);

    private static final String INSERT_SQL = {TQ}
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
        {TQ};

    private final DataSource dataSource;

    @Inject
    public JdbcComplianceRepository(DataSource dataSource) {{
        this.dataSource = dataSource;
    }}

    @Override
    public Promise<ComplianceAssessment> save(ComplianceAssessment a) {{
        return Promise.ofBlocking(() -> {{
            if (a.getId() == null) a.setId(UUID.randomUUID());
            a.setUpdatedAt(Instant.now());
            if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {{
                int i = 1;
                ps.setObject(i++, a.getId());
                ps.setObject(i++, a.getWorkspaceId());
                ps.setObject(i++, a.getFrameworkId());
                ps.setObject(i++, a.getProjectId());
                ps.setObject(i++, a.getAssessmentDate());
                ps.setObject(i++, a.getDueDate());
                ps.setString(i++, a.getAssessorName());
                ps.setString(i++, a.getAssessmentType());
                ps.setString(i++, a.getNotes());
                ps.setInt(i++, a.getScore());
                ps.setInt(i++, a.getPassedControls());
                ps.setInt(i++, a.getFailedControls());
                ps.setInt(i++, a.getNaControls());
                ps.setInt(i++, a.getTotalControls());
                ps.setString(i++, a.getStatus());
                ps.setString(i++, a.getDetails());
                setTs(ps, i++, a.getStartedAt());
                setTs(ps, i++, a.getAssessedAt());
                ps.setTimestamp(i++, Timestamp.from(a.getCreatedAt()));
                ps.setTimestamp(i++, Timestamp.from(a.getUpdatedAt()));
                ps.setInt(i++, a.getVersion());
                ps.executeUpdate();
            }}
            return a;
        }});
    }}

    @Override
    public Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? mapRow(rs) : null; }}
            }}
        }});
    }}

    @Override
    public Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId) {{
        return queryUuids("SELECT * FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }}

    @Override
    public Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId) {{
        return queryUuids("SELECT * FROM compliance_assessments WHERE workspace_id = ? AND framework_id = ? ORDER BY created_at DESC",
            workspaceId, frameworkId);
    }}

    @Override
    public Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? AND framework_id = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setObject(3, frameworkId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<ComplianceAssessment>> findByStatus(UUID workspaceId, String status) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND status = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setString(2, status);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<ComplianceAssessment>> findByAssessmentType(UUID workspaceId, String assessmentType) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND assessment_type = ? ORDER BY created_at DESC")) {{
                ps.setObject(1, workspaceId); ps.setString(2, assessmentType);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, UUID projectId, String status) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? AND status = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setString(3, status);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? rs.getLong(1) : 0L; }}
            }}
        }});
    }}

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }}
            return null;
        }});
    }}

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next(); }}
            }}
        }});
    }}

    // ========== Helpers ==========

    private Promise<List<ComplianceAssessment>> queryUuids(String sql, UUID a, UUID b) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {{
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }}
        }});
    }}

    private List<ComplianceAssessment> collectRows(PreparedStatement ps) throws SQLException {{
        List<ComplianceAssessment> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {{ while (rs.next()) list.add(mapRow(rs)); }}
        return list;
    }}

    private ComplianceAssessment mapRow(ResultSet rs) throws SQLException {{
        ComplianceAssessment o = new ComplianceAssessment();
        o.setId(rs.getObject("id", UUID.class));
        o.setWorkspaceId(rs.getObject("workspace_id", UUID.class));
        o.setFrameworkId(rs.getObject("framework_id", UUID.class));
        o.setProjectId(rs.getObject("project_id", UUID.class));
        Date assessmentDate = rs.getDate("assessment_date");
        if (assessmentDate != null) o.setAssessmentDate(assessmentDate.toLocalDate());
        Date dueDate = rs.getDate("due_date");
        if (dueDate != null) o.setDueDate(dueDate.toLocalDate());
        o.setAssessorName(rs.getString("assessor_name"));
        o.setAssessmentType(rs.getString("assessment_type"));
        o.setNotes(rs.getString("notes"));
        o.setScore(rs.getInt("score"));
        o.setPassedControls(rs.getInt("passed_controls"));
        o.setFailedControls(rs.getInt("failed_controls"));
        o.setNaControls(rs.getInt("na_controls"));
        o.setTotalControls(rs.getInt("total_controls"));
        o.setStatus(rs.getString("status"));
        o.setDetails(rs.getString("details"));
        o.setStartedAt(getTs(rs, "started_at"));
        o.setAssessedAt(getTs(rs, "assessed_at"));
        o.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        o.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        o.setVersion(rs.getInt("version"));
        return o;
    }}

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {{
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }}

    private static Instant getTs(ResultSet rs, String col) throws SQLException {{
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }}
}}
''')


# ============================================================
# 5. JdbcProjectRepository
# ============================================================
write("JdbcProjectRepository.java", f'''/*
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
public class JdbcProjectRepository implements ProjectRepository {{

    private static final Logger logger = LoggerFactory.getLogger(JdbcProjectRepository.class);

    private static final String INSERT_SQL = {TQ}
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
        {TQ};

    private final DataSource dataSource;

    @Inject
    public JdbcProjectRepository(DataSource dataSource) {{
        this.dataSource = dataSource;
    }}

    @Override
    public Promise<Project> save(Project p) {{
        return Promise.ofBlocking(() -> {{
            if (p.getId() == null) p.setId(UUID.randomUUID());
            p.setUpdatedAt(Instant.now());
            if (p.getCreatedAt() == null) p.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {{
                int i = 1;
                ps.setObject(i++, p.getId());
                ps.setObject(i++, p.getWorkspaceId());
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
            }}
            return p;
        }});
    }}

    @Override
    public Promise<Optional<Project>> findById(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }}
            }}
        }});
    }}

    @Override
    public Promise<Optional<Project>> findByKey(UUID workspaceId, String key) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND key = ?")) {{
                ps.setObject(1, workspaceId); ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {{
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }}
            }}
        }});
    }}

    @Override
    public Promise<List<Project>> findByWorkspace(UUID workspaceId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? ORDER BY name")) {{
                ps.setObject(1, workspaceId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<Project>> findActiveByWorkspace(UUID workspaceId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND archived = false ORDER BY name")) {{
                ps.setObject(1, workspaceId);
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<List<Project>> searchByName(UUID workspaceId, String query) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM projects WHERE workspace_id = ? AND LOWER(name) LIKE ? ORDER BY name")) {{
                ps.setObject(1, workspaceId);
                ps.setString(2, "%" + query.toLowerCase() + "%");
                return collectRows(ps);
            }}
        }});
    }}

    @Override
    public Promise<Boolean> delete(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                return ps.executeUpdate() > 0;
            }}
        }});
    }}

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM projects WHERE workspace_id = ? AND id = ?")) {{
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next(); }}
            }}
        }});
    }}

    @Override
    public Promise<Boolean> isKeyAvailable(UUID workspaceId, String key) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM projects WHERE workspace_id = ? AND key = ?")) {{
                ps.setObject(1, workspaceId); ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {{ return !rs.next(); }}
            }}
        }});
    }}

    @Override
    public Promise<Long> countByWorkspace(UUID workspaceId) {{
        return Promise.ofBlocking(() -> {{
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM projects WHERE workspace_id = ?")) {{
                ps.setObject(1, workspaceId);
                try (ResultSet rs = ps.executeQuery()) {{ return rs.next() ? rs.getLong(1) : 0L; }}
            }}
        }});
    }}

    // ========== Helpers ==========

    private List<Project> collectRows(PreparedStatement ps) throws SQLException {{
        List<Project> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {{ while (rs.next()) list.add(mapRow(rs)); }}
        return list;
    }}

    private Project mapRow(ResultSet rs) throws SQLException {{
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
        p.setArchivedAt(getTs(rs, "archived_at"));
        p.setLastScanAt(getTs(rs, "last_scan_at"));
        p.setScanCount(rs.getInt("scan_count"));
        p.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        p.setVersion(rs.getInt("version"));
        return p;
    }}

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {{
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }}

    private static Instant getTs(ResultSet rs, String col) throws SQLException {{
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }}
}}
''')

print(f"\nAll 5 JDBC repositories rewritten successfully.")
