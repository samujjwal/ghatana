package com.ghatana.audit.integrity.ports;

import com.ghatana.audit.integrity.domain.AuditIntegrityReport;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for audit integrity report persistence
 * Day 42: Audit Integrity - Report Repository Port
 */
public interface AuditIntegrityReportRepository {
    
    /**
     * Save an audit integrity report
     */
    AuditIntegrityReport save(AuditIntegrityReport report);
    
    /**
     * Find report by ID
     */
    Optional<AuditIntegrityReport> findById(String reportId);
    
    /**
     * Find reports within a time range
     */
    List<AuditIntegrityReport> findReportsByTimeRange(Instant startTime, Instant endTime);
    
    /**
     * Find the latest audit integrity report
     */
    Optional<AuditIntegrityReport> findLatestReport();
    
    /**
     * Find reports with specific integrity status
     */
    List<AuditIntegrityReport> findReportsByStatus(AuditIntegrityReport.IntegrityStatus status);
    
    /**
     * Find reports with violations
     */
    List<AuditIntegrityReport> findReportsWithViolations();
    
    /**
     * Get total count of integrity reports
     */
    long getTotalReportCount();
    
    /**
     * Delete reports older than the specified time
     */
    int deleteReportsOlderThan(Instant cutoffTime);
}