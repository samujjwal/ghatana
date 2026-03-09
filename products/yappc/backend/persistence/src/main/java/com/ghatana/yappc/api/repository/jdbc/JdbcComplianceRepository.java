/*
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
    public Promise<ComplianceAssessment> save(ComplianceAssessment a) {
        return Promise.ofBlocking(() -> {
            if (a.getId() == null) a.setId(UUID.randomUUID());
            a.setUpdatedAt(Instant.now());
            if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
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
            }
            return a;
        });
    }

    @Override
    public Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
            }
        });
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId) {
        return queryUuids("SELECT * FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? ORDER BY created_at DESC",
            workspaceId, projectId);
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId) {
        return queryUuids("SELECT * FROM compliance_assessments WHERE workspace_id = ? AND framework_id = ? ORDER BY created_at DESC",
            workspaceId, frameworkId);
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM compliance_assessments WHERE workspace_id = ? AND project_id = ? AND framework_id = ? ORDER BY created_at DESC")) {
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setObject(3, frameworkId);
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
                ps.setObject(1, workspaceId); ps.setString(2, status);
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
                ps.setObject(1, workspaceId); ps.setString(2, assessmentType);
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
                ps.setObject(1, workspaceId); ps.setObject(2, projectId); ps.setString(3, status);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM compliance_assessments WHERE workspace_id = ? AND id = ?")) {
                ps.setObject(1, workspaceId); ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    // ========== Helpers ==========

    private Promise<List<ComplianceAssessment>> queryUuids(String sql, UUID a, UUID b) {
        return Promise.ofBlocking(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, a); ps.setObject(2, b);
                return collectRows(ps);
            }
        });
    }

    private List<ComplianceAssessment> collectRows(PreparedStatement ps) throws SQLException {
        List<ComplianceAssessment> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapRow(rs)); }
        return list;
    }

    private ComplianceAssessment mapRow(ResultSet rs) throws SQLException {
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
    }

    private static void setTs(PreparedStatement ps, int i, Instant v) throws SQLException {
        ps.setTimestamp(i, v != null ? Timestamp.from(v) : null);
    }

    private static Instant getTs(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col); return t != null ? t.toInstant() : null;
    }
}
