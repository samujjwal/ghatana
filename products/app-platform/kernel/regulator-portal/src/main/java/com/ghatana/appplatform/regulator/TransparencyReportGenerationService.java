package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Scheduled generation and delivery of regulatory transparency reports.
 *              Three report types:
 *              - AML: monthly Anti-Money Laundering compliance summary
 *              - SETTLEMENT: quarterly settlement volume and failure analysis
 *              - AUDIT: annual comprehensive audit report
 *              Reports are auto-generated on schedule, stored, and submitted to all
 *              licensed regulators in the relevant jurisdiction.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-008: Transparency report generation, storage, and auto-submission
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS transparency_reports (
 *   report_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   report_type    TEXT NOT NULL,   -- AML | SETTLEMENT | AUDIT
 *   period_label   TEXT NOT NULL,   -- e.g. '2081-03' or '2081-Q1' or '2081'
 *   jurisdiction   TEXT,
 *   status         TEXT NOT NULL DEFAULT 'GENERATING',  -- GENERATING | READY | SUBMITTED | FAILED
 *   storage_ref    TEXT,
 *   sha256         TEXT,
 *   generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   submitted_at   TIMESTAMPTZ
 * );
 * </pre>
 */
public class TransparencyReportGenerationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ReportBuilderPort {
        byte[] buildAmlReport(String periodLabel, String jurisdiction) throws Exception;
        byte[] buildSettlementReport(String periodLabel, String jurisdiction) throws Exception;
        byte[] buildAuditReport(String periodLabel, String jurisdiction) throws Exception;
    }

    public interface SecureStoragePort {
        String store(String reportId, byte[] data) throws Exception;
    }

    public interface RegulatoryDeliveryPort {
        /** Send the report to all licensed regulators for the jurisdiction (or all if null). */
        void deliverToRegulators(String reportId, String reportType, String periodLabel,
                                  String jurisdiction, String storageRef) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ReportBuilderPort builder;
    private final SecureStoragePort storage;
    private final RegulatoryDeliveryPort delivery;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter reportsGenerated;
    private final Counter reportsFailed;

    public TransparencyReportGenerationService(
        javax.sql.DataSource ds,
        ReportBuilderPort builder,
        SecureStoragePort storage,
        RegulatoryDeliveryPort delivery,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.builder          = builder;
        this.storage          = storage;
        this.delivery         = delivery;
        this.audit            = audit;
        this.executor         = executor;
        this.reportsGenerated = Counter.builder("regulator.transparency.reports_generated").register(registry);
        this.reportsFailed    = Counter.builder("regulator.transparency.reports_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Generate and auto-submit the monthly AML report for a jurisdiction. */
    public Promise<String> generateMonthlyAml(String periodLabel, String jurisdiction) {
        return generate("AML", periodLabel, jurisdiction);
    }

    /** Generate and auto-submit the quarterly settlement report. */
    public Promise<String> generateQuarterlySettlement(String periodLabel, String jurisdiction) {
        return generate("SETTLEMENT", periodLabel, jurisdiction);
    }

    /** Generate and auto-submit the annual audit report. */
    public Promise<String> generateAnnualAudit(String periodLabel, String jurisdiction) {
        return generate("AUDIT", periodLabel, jurisdiction);
    }

    /** Retrieve status and storage ref for a report. */
    public Promise<Map<String, Object>> getReport(String reportId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT report_type, period_label, jurisdiction, status, storage_ref, sha256, generated_at, submitted_at " +
                     "FROM transparency_reports WHERE report_id=?"
                 )) {
                ps.setString(1, reportId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new NoSuchElementException("Report not found: " + reportId);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reportId",     reportId);
                    m.put("reportType",   rs.getString("report_type"));
                    m.put("periodLabel",  rs.getString("period_label"));
                    m.put("jurisdiction", rs.getString("jurisdiction"));
                    m.put("status",       rs.getString("status"));
                    m.put("storageRef",   rs.getString("storage_ref"));
                    m.put("sha256",       rs.getString("sha256"));
                    m.put("generatedAt",  rs.getTimestamp("generated_at").toInstant().toString());
                    Timestamp sub = rs.getTimestamp("submitted_at");
                    if (sub != null) m.put("submittedAt", sub.toInstant().toString());
                    return m;
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Promise<String> generate(String reportType, String periodLabel, String jurisdiction) {
        return Promise.ofBlocking(executor, () -> {
            String reportId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO transparency_reports (report_type, period_label, jurisdiction) VALUES (?,?,?) RETURNING report_id"
                 )) {
                ps.setString(1, reportType); ps.setString(2, periodLabel); ps.setString(3, jurisdiction);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); reportId = rs.getString(1); }
            }
            String reportIdFinal = reportId;
            executor.execute(() -> {
                try {
                    byte[] data = switch (reportType) {
                        case "AML"        -> builder.buildAmlReport(periodLabel, jurisdiction);
                        case "SETTLEMENT" -> builder.buildSettlementReport(periodLabel, jurisdiction);
                        case "AUDIT"      -> builder.buildAuditReport(periodLabel, jurisdiction);
                        default           -> throw new IllegalArgumentException("Unknown report type: " + reportType);
                    };
                    String sha256 = sha256Hex(data);
                    String storageRef = storage.store(reportIdFinal, data);
                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "UPDATE transparency_reports SET status='READY', storage_ref=?, sha256=? WHERE report_id=?"
                         )) {
                        ps.setString(1, storageRef); ps.setString(2, sha256); ps.setString(3, reportIdFinal);
                        ps.executeUpdate();
                    }
                    delivery.deliverToRegulators(reportIdFinal, reportType, periodLabel, jurisdiction, storageRef);
                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "UPDATE transparency_reports SET status='SUBMITTED', submitted_at=NOW() WHERE report_id=?"
                         )) { ps.setString(1, reportIdFinal); ps.executeUpdate(); }
                    audit.record("system", "TRANSPARENCY_REPORT_SUBMITTED",
                        "reportId=" + reportIdFinal + " type=" + reportType + " period=" + periodLabel);
                    reportsGenerated.increment();
                } catch (Exception ex) {
                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "UPDATE transparency_reports SET status='FAILED' WHERE report_id=?"
                         )) { ps.setString(1, reportIdFinal); ps.executeUpdate(); } catch (Exception ignored) {}
                    reportsFailed.increment();
                }
            });
            return reportId;
        });
    }

    private String sha256Hex(byte[] data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
