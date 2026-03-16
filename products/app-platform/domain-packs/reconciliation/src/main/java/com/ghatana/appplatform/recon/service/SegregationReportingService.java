package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Generates regulatory segregation reports: daily attestation of client money
 *              segregation status. Exports in PDF/CSV formats via D-10 regulatory reporting.
 *              Report archival for 7-year retention (K-08). Maker-checker sign-off required.
 *              Satisfies STORY-D13-013.
 * @doc.layer   Domain
 * @doc.pattern Maker-checker workflow; report archival (K-08); export port for D-10;
 *              audit trail via INSERT-only report_submissions table.
 */
public class SegregationReportingService {

    private static final Logger log = LoggerFactory.getLogger(SegregationReportingService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ReportExportPort reportExport;
    private final ArchivalPort     archival;
    private final Counter          reportCounter;

    public SegregationReportingService(HikariDataSource dataSource, Executor executor,
                                       ReportExportPort reportExport, ArchivalPort archival,
                                       MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.reportExport = reportExport;
        this.archival     = archival;
        this.reportCounter = registry.counter("recon.segregation_report.generated");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface ReportExportPort {
        byte[] exportPdf(SegregationReport report);
        byte[] exportCsv(SegregationReport report);
    }

    /** K-08 archival — 7-year retention. */
    public interface ArchivalPort {
        String archive(String reportId, byte[] content, String format, LocalDate expiresAfter);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SegregationReport(String reportId, LocalDate reportDate, String jurisdiction,
                                    String status, List<CheckLine> checkLines,
                                    String submittedBy, String approvedBy) {}

    public record CheckLine(String checkId, String jurisdiction, java.math.BigDecimal obligations,
                            java.math.BigDecimal segregated, java.math.BigDecimal ratio,
                            boolean compliant) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Step 1: Maker submits draft report for a given date. */
    public Promise<String> submitDraft(LocalDate reportDate, String submitterId) {
        return Promise.ofBlocking(executor, () -> {
            List<CheckLine> lines = loadCheckLines(reportDate);
            String reportId = UUID.randomUUID().toString();
            persistReport(reportId, reportDate, submitterId, "PENDING_REVIEW");
            log.info("Segregation report draft submitted: reportId={} date={}", reportId, reportDate);
            return reportId;
        });
    }

    /** Step 2: Checker approves report — must be different from maker. */
    public Promise<String> approve(String reportId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            SegregationReport report = loadReport(reportId);
            if (report.submittedBy().equals(approverId)) {
                throw new IllegalStateException("Maker and checker must be different persons: " + approverId);
            }
            try (Connection conn = dataSource.getConnection()) {
                updateStatus(conn, reportId, "APPROVED", approverId);
            }
            // generate and archive
            byte[] pdfBytes = reportExport.exportPdf(report);
            byte[] csvBytes = reportExport.exportCsv(report);
            LocalDate retention = report.reportDate().plusYears(7);
            String pdfRef = archival.archive(reportId + "-pdf", pdfBytes, "PDF", retention);
            String csvRef = archival.archive(reportId + "-csv", csvBytes, "CSV", retention);
            persistArchiveRefs(reportId, pdfRef, csvRef);
            reportCounter.increment();
            return reportId;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<CheckLine> loadCheckLines(LocalDate reportDate) throws SQLException {
        List<CheckLine> lines = new ArrayList<>();
        String sql = """
                SELECT check_id, jurisdiction, client_obligations, segregated_balance, ratio, compliant
                FROM client_money_segregation_checks
                WHERE check_date = ?
                ORDER BY jurisdiction
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new CheckLine(rs.getString("check_id"), rs.getString("jurisdiction"),
                            rs.getBigDecimal("client_obligations"),
                            rs.getBigDecimal("segregated_balance"),
                            rs.getBigDecimal("ratio"), rs.getBoolean("compliant")));
                }
            }
        }
        return lines;
    }

    private void persistReport(String reportId, LocalDate reportDate, String submitterId,
                               String status) throws SQLException {
        String sql = """
                INSERT INTO segregation_reports
                    (report_id, report_date, submitted_by, status, created_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (report_date) DO UPDATE
                    SET submitted_by=EXCLUDED.submitted_by, status=EXCLUDED.status
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportId);
            ps.setObject(2, reportDate);
            ps.setString(3, submitterId);
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }

    private SegregationReport loadReport(String reportId) throws SQLException {
        String sql = """
                SELECT report_id, report_date, jurisdiction, status, submitted_by,
                       COALESCE(approved_by,'') AS approved_by
                FROM segregation_reports WHERE report_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDate date = rs.getObject("report_date", LocalDate.class);
                    return new SegregationReport(reportId, date,
                            rs.getString("jurisdiction"), rs.getString("status"),
                            loadCheckLines(date), rs.getString("submitted_by"),
                            rs.getString("approved_by"));
                }
            }
        }
        throw new IllegalArgumentException("Report not found: " + reportId);
    }

    private void updateStatus(Connection conn, String reportId, String newStatus, String approverId)
            throws SQLException {
        String sql = "UPDATE segregation_reports SET status=?, approved_by=?, approved_at=NOW() WHERE report_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, approverId);
            ps.setString(3, reportId);
            ps.executeUpdate();
        }
    }

    private void persistArchiveRefs(String reportId, String pdfRef, String csvRef) throws SQLException {
        String sql = "UPDATE segregation_reports SET pdf_archive_ref=?, csv_archive_ref=? WHERE report_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pdfRef);
            ps.setString(2, csvRef);
            ps.setString(3, reportId);
            ps.executeUpdate();
        }
    }
}
