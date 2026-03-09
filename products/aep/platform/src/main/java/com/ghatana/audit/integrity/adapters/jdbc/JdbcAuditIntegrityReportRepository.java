package com.ghatana.audit.integrity.adapters.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.integrity.domain.AuditIntegrityReport;
import com.ghatana.audit.integrity.domain.AuditIntegrityReport.IntegrityViolation;
import com.ghatana.audit.integrity.ports.AuditIntegrityReportRepository;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of AuditIntegrityReportRepository
 * Day 42: Audit Integrity - JDBC Report Repository
 */
@Slf4j
public class JdbcAuditIntegrityReportRepository implements AuditIntegrityReportRepository {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    // SQL queries
    private static final String INSERT_REPORT = """
        INSERT INTO audit_integrity_reports (
            report_id, verification_time, audit_start_time, audit_end_time,
            total_batches, total_entries, verified_batches, tampered_batches, missing_batches,
            violation_batch_ids_json, violations_json, overall_status, verification_duration_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    
    private static final String SELECT_BY_ID = """
        SELECT report_id, verification_time, audit_start_time, audit_end_time,
               total_batches, total_entries, verified_batches, tampered_batches, missing_batches,
               violation_batch_ids_json, violations_json, overall_status, verification_duration_ms
        FROM audit_integrity_reports WHERE report_id = ?
        """;
    
    private static final String SELECT_BY_TIME_RANGE = """
        SELECT report_id, verification_time, audit_start_time, audit_end_time,
               total_batches, total_entries, verified_batches, tampered_batches, missing_batches,
               violation_batch_ids_json, violations_json, overall_status, verification_duration_ms
        FROM audit_integrity_reports 
        WHERE verification_time >= ? AND verification_time <= ?
        ORDER BY verification_time DESC
        """;
    
    private static final String SELECT_LATEST = """
        SELECT report_id, verification_time, audit_start_time, audit_end_time,
               total_batches, total_entries, verified_batches, tampered_batches, missing_batches,
               violation_batch_ids_json, violations_json, overall_status, verification_duration_ms
        FROM audit_integrity_reports 
        ORDER BY verification_time DESC LIMIT 1
        """;
    
    private static final String SELECT_BY_STATUS = """
        SELECT report_id, verification_time, audit_start_time, audit_end_time,
               total_batches, total_entries, verified_batches, tampered_batches, missing_batches,
               violation_batch_ids_json, violations_json, overall_status, verification_duration_ms
        FROM audit_integrity_reports 
        WHERE overall_status = ?
        ORDER BY verification_time DESC
        """;
    
    private static final String SELECT_WITH_VIOLATIONS = """
        SELECT report_id, verification_time, audit_start_time, audit_end_time,
               total_batches, total_entries, verified_batches, tampered_batches, missing_batches,
               violation_batch_ids_json, violations_json, overall_status, verification_duration_ms
        FROM audit_integrity_reports 
        WHERE JSON_LENGTH(violations_json) > 0
        ORDER BY verification_time DESC
        """;
    
    private static final String COUNT_REPORTS = "SELECT COUNT(*) FROM audit_integrity_reports";
    
    private static final String DELETE_OLD_REPORTS = "DELETE FROM audit_integrity_reports WHERE verification_time < ?";
    
    public JdbcAuditIntegrityReportRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public AuditIntegrityReport save(AuditIntegrityReport report) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_REPORT)) {
            
            stmt.setString(1, report.getReportId());
            stmt.setTimestamp(2, Timestamp.from(report.getVerificationTime()));
            stmt.setTimestamp(3, Timestamp.from(report.getAuditStartTime()));
            stmt.setTimestamp(4, Timestamp.from(report.getAuditEndTime()));
            stmt.setLong(5, report.getTotalBatches());
            stmt.setLong(6, report.getTotalEntries());
            stmt.setLong(7, report.getVerifiedBatches());
            stmt.setLong(8, report.getTamperedBatches());
            stmt.setLong(9, report.getMissingBatches());
            stmt.setString(10, serializeStringList(report.getViolationBatchIds()));
            stmt.setString(11, serializeViolations(report.getViolations()));
            stmt.setString(12, report.getOverallStatus().name());
            stmt.setLong(13, report.getVerificationDurationMs());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new RuntimeException("Failed to insert audit integrity report: " + report.getReportId());
            }
            
            log.debug("Saved audit integrity report {} with status {}", 
                    report.getReportId(), report.getOverallStatus());
            return report;
            
        } catch (SQLException e) {
            log.error("Error saving audit integrity report {}", report.getReportId(), e);
            throw new RuntimeException("Failed to save audit integrity report", e);
        }
    }
    
    @Override
    public Optional<AuditIntegrityReport> findById(String reportId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setString(1, reportId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToReport(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            log.error("Error finding audit integrity report by ID {}", reportId, e);
            throw new RuntimeException("Failed to find audit integrity report", e);
        }
    }
    
    @Override
    public List<AuditIntegrityReport> findReportsByTimeRange(Instant startTime, Instant endTime) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TIME_RANGE)) {
            
            stmt.setTimestamp(1, Timestamp.from(startTime));
            stmt.setTimestamp(2, Timestamp.from(endTime));
            
            List<AuditIntegrityReport> reports = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(mapResultSetToReport(rs));
                }
            }
            
            log.debug("Found {} audit integrity reports in time range {} to {}", 
                    reports.size(), startTime, endTime);
            return reports;
            
        } catch (SQLException e) {
            log.error("Error finding audit integrity reports by time range {} to {}", startTime, endTime, e);
            throw new RuntimeException("Failed to find audit integrity reports by time range", e);
        }
    }
    
    @Override
    public Optional<AuditIntegrityReport> findLatestReport() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_LATEST);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return Optional.of(mapResultSetToReport(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Error finding latest audit integrity report", e);
            throw new RuntimeException("Failed to find latest audit integrity report", e);
        }
    }
    
    @Override
    public List<AuditIntegrityReport> findReportsByStatus(AuditIntegrityReport.IntegrityStatus status) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_STATUS)) {
            
            stmt.setString(1, status.name());
            
            List<AuditIntegrityReport> reports = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(mapResultSetToReport(rs));
                }
            }
            
            return reports;
            
        } catch (SQLException e) {
            log.error("Error finding audit integrity reports by status {}", status, e);
            throw new RuntimeException("Failed to find audit integrity reports by status", e);
        }
    }
    
    @Override
    public List<AuditIntegrityReport> findReportsWithViolations() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_WITH_VIOLATIONS)) {
            
            List<AuditIntegrityReport> reports = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(mapResultSetToReport(rs));
                }
            }
            
            return reports;
            
        } catch (SQLException e) {
            log.error("Error finding audit integrity reports with violations", e);
            throw new RuntimeException("Failed to find audit integrity reports with violations", e);
        }
    }
    
    @Override
    public long getTotalReportCount() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(COUNT_REPORTS);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
            
        } catch (SQLException e) {
            log.error("Error counting audit integrity reports", e);
            throw new RuntimeException("Failed to count audit integrity reports", e);
        }
    }
    
    @Override
    public int deleteReportsOlderThan(Instant cutoffTime) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_OLD_REPORTS)) {
            
            stmt.setTimestamp(1, Timestamp.from(cutoffTime));
            
            int deletedCount = stmt.executeUpdate();
            log.info("Deleted {} audit integrity reports older than {}", deletedCount, cutoffTime);
            return deletedCount;
            
        } catch (SQLException e) {
            log.error("Error deleting old audit integrity reports", e);
            throw new RuntimeException("Failed to delete old audit integrity reports", e);
        }
    }
    
    private AuditIntegrityReport mapResultSetToReport(ResultSet rs) throws SQLException {
        return AuditIntegrityReport.builder()
                .reportId(rs.getString("report_id"))
                .verificationTime(rs.getTimestamp("verification_time").toInstant())
                .auditStartTime(rs.getTimestamp("audit_start_time").toInstant())
                .auditEndTime(rs.getTimestamp("audit_end_time").toInstant())
                .totalBatches(rs.getLong("total_batches"))
                .totalEntries(rs.getLong("total_entries"))
                .verifiedBatches(rs.getLong("verified_batches"))
                .tamperedBatches(rs.getLong("tampered_batches"))
                .missingBatches(rs.getLong("missing_batches"))
                .violationBatchIds(deserializeStringList(rs.getString("violation_batch_ids_json")))
                .violations(deserializeViolations(rs.getString("violations_json")))
                .overallStatus(AuditIntegrityReport.IntegrityStatus.valueOf(rs.getString("overall_status")))
                .verificationDurationMs(rs.getLong("verification_duration_ms"))
                .build();
    }
    
    private String serializeStringList(List<String> stringList) {
        try {
            return objectMapper.writeValueAsString(stringList);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize string list", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> deserializeStringList(String json) {
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize string list", e);
        }
    }
    
    private String serializeViolations(List<IntegrityViolation> violations) {
        try {
            return objectMapper.writeValueAsString(violations);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize violations", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<IntegrityViolation> deserializeViolations(String violationsJson) {
        try {
            return objectMapper.readValue(violationsJson, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, IntegrityViolation.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize violations", e);
        }
    }
}