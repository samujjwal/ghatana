package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Regulatory reporting analytics over a 6-month rolling window. Computes
 *              rejection rates per regulator, deadline compliance percentage, NACK error
 *              code frequencies, and regulator-specific rejection patterns. Supports
 *              identification of systemic issues for process improvement.
 *              Satisfies STORY-D10-013.
 * @doc.layer   Domain
 * @doc.pattern Analytics aggregation; 6-month rolling window; Gauge for compliance rate.
 */
public class ReportingAnalyticsService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AtomicLong       complianceRateBps = new AtomicLong();  // basis points × 10000

    public ReportingAnalyticsService(HikariDataSource dataSource, Executor executor,
                                      MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor   = executor;
        Gauge.builder("reporting.analytics.compliance_rate_bps", complianceRateBps, AtomicLong::doubleValue)
             .register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record RegulatorRejectionStats(String regulator, long totalSubmissions, long rejected,
                                           double rejectionRate) {}

    public record ErrorCodeFrequency(String errorCode, long count, double sharePct) {}

    public record AnalyticsSummary(List<RegulatorRejectionStats> rejectionByRegulator,
                                    List<ErrorCodeFrequency> topErrorCodes,
                                    double overallComplianceRate,
                                    double deadlineCompliancePct,
                                    LocalDate fromDate, LocalDate toDate) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<AnalyticsSummary> getAnalytics(LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> {
            List<RegulatorRejectionStats> rejections = loadRejectionStats(from, to);
            List<ErrorCodeFrequency> errorCodes      = loadTopErrorCodes(from, to);
            double complianceRate                    = computeOverallComplianceRate(from, to);
            double deadlineCompliance                = computeDeadlineCompliance(from, to);

            complianceRateBps.set(Math.round(complianceRate * 10000));
            return new AnalyticsSummary(rejections, errorCodes, complianceRate, deadlineCompliance, from, to);
        });
    }

    // ─── Analytics queries ───────────────────────────────────────────────────

    private List<RegulatorRejectionStats> loadRejectionStats(LocalDate from, LocalDate to)
            throws SQLException {
        String sql = """
                SELECT regulator,
                       COUNT(*)                                         AS total,
                       COUNT(CASE WHEN status='REJECTED' THEN 1 END)   AS rejected
                FROM regulator_submissions
                WHERE DATE(submitted_at) BETWEEN ? AND ?
                GROUP BY regulator
                ORDER BY rejected DESC
                """;
        List<RegulatorRejectionStats> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long total    = rs.getLong("total");
                    long rejected = rs.getLong("rejected");
                    double rate   = total == 0 ? 0.0 : (double) rejected / total;
                    result.add(new RegulatorRejectionStats(rs.getString("regulator"), total, rejected, rate));
                }
            }
        }
        return result;
    }

    private List<ErrorCodeFrequency> loadTopErrorCodes(LocalDate from, LocalDate to)
            throws SQLException {
        String sql = """
                SELECT error_code, COUNT(*) AS cnt
                FROM submission_nack_errors e
                JOIN regulator_submissions s ON s.submission_id = e.submission_id
                WHERE DATE(s.submitted_at) BETWEEN ? AND ?
                GROUP BY error_code ORDER BY cnt DESC LIMIT 20
                """;
        // compute share after loading totals
        List<ErrorCodeFrequency> raw = new ArrayList<>();
        long total = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long cnt = rs.getLong("cnt");
                    total += cnt;
                    raw.add(new ErrorCodeFrequency(rs.getString("error_code"), cnt, 0));
                }
            }
        }
        long finalTotal = total;
        return raw.stream()
                .map(e -> new ErrorCodeFrequency(e.errorCode(), e.count(),
                        finalTotal == 0 ? 0 : (double) e.count() / finalTotal))
                .toList();
    }

    private double computeOverallComplianceRate(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS total,
                       COUNT(CASE WHEN status IN ('ACCEPTED','ACKNOWLEDGED') THEN 1 END) AS accepted
                FROM regulator_submissions
                WHERE DATE(submitted_at) BETWEEN ? AND ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 1.0;
                long total    = rs.getLong("total");
                long accepted = rs.getLong("accepted");
                return total == 0 ? 1.0 : (double) accepted / total;
            }
        }
    }

    private double computeDeadlineCompliance(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS total,
                       COUNT(CASE WHEN DATE(submitted_at) <= next_deadline THEN 1 END) AS on_time
                FROM regulator_submissions rs
                JOIN report_definitions rd ON rd.report_code = rs.report_code
                WHERE DATE(rs.submitted_at) BETWEEN ? AND ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 1.0;
                long total  = rs.getLong("total");
                long onTime = rs.getLong("on_time");
                return total == 0 ? 1.0 : (double) onTime / total;
            }
        }
    }
}
