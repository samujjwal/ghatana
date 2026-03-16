package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Aggregate platform-wide operational metrics across jurisdictions for operator reports.
 *              Reports: settlement volume by jurisdiction, regulatory submission compliance rate,
 *              AML risk distribution, platform uptime by jurisdiction.
 *              Operator-only access. Export to PDF/CSV. Scheduled delivery to operator email.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-010: Cross-jurisdiction reporting aggregation
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS jurisdiction_report_schedules (
 *   schedule_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   report_type    TEXT NOT NULL,  -- SETTLEMENT_VOLUME | SUBMISSION_COMPLIANCE | AML_DISTRIBUTION | UPTIME
 *   frequency      TEXT NOT NULL,  -- MONTHLY | QUARTERLY
 *   recipient_emails JSONB NOT NULL DEFAULT '[]',
 *   last_run_at    TIMESTAMPTZ,
 *   created_by     TEXT NOT NULL,
 *   created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS jurisdiction_report_runs (
 *   run_id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   schedule_id    TEXT,
 *   report_type    TEXT NOT NULL,
 *   period_from    DATE NOT NULL,
 *   period_to      DATE NOT NULL,
 *   status         TEXT NOT NULL DEFAULT 'PENDING',
 *   export_format  TEXT NOT NULL DEFAULT 'PDF',
 *   storage_path   TEXT,
 *   generated_by   TEXT NOT NULL,
 *   generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class CrossJurisdictionReportingService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ReportExportPort {
        String exportPdf(String reportType, List<Map<String, Object>> data, String period) throws Exception;
        String exportCsv(String reportType, List<Map<String, Object>> data) throws Exception;
    }

    public interface EmailDeliveryPort {
        void send(List<String> recipients, String subject, String storagePath) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record JurisdictionReportResult(
        String reportType, String periodFrom, String periodTo,
        List<Map<String, Object>> rows, String storagePath
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ReportExportPort exporter;
    private final EmailDeliveryPort email;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter reportsGeneratedCounter;

    public CrossJurisdictionReportingService(
        javax.sql.DataSource ds,
        ReportExportPort exporter,
        EmailDeliveryPort email,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                      = ds;
        this.exporter                = exporter;
        this.email                   = email;
        this.audit                   = audit;
        this.executor                = executor;
        this.reportsGeneratedCounter = Counter.builder("operator.crossjuris.reports_generated").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Generate settlement volume by jurisdiction for the given period. */
    public Promise<JurisdictionReportResult> settlementVolumeReport(
        java.time.LocalDate from, java.time.LocalDate to, String format, String requestedBy
    ) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT oj.code AS jurisdiction, oj.currency, " +
                     "COUNT(*)::bigint AS trade_count, " +
                     "SUM(COALESCE((si.settlement_amount)::numeric, 0)) AS settlement_value, " +
                     "SUM(CASE WHEN si.status='SETTLED' THEN 1 ELSE 0 END)::bigint AS settled_count " +
                     "FROM operator_jurisdictions oj " +
                     "LEFT JOIN tenant_jurisdictions tj ON tj.jurisdiction_id=oj.jurisdiction_id " +
                     "LEFT JOIN settlement_instructions si ON si.tenant_id=tj.tenant_id " +
                     "  AND si.settlement_date BETWEEN ? AND ? " +
                     "WHERE oj.status='ACTIVE' GROUP BY oj.code, oj.currency ORDER BY settlement_value DESC NULLS LAST"
                 )) {
                ps.setDate(1, Date.valueOf(from)); ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("jurisdiction", rs.getString("jurisdiction"));
                        row.put("currency", rs.getString("currency"));
                        row.put("tradeCount", rs.getLong("trade_count"));
                        row.put("settlementValue", rs.getBigDecimal("settlement_value"));
                        row.put("settledCount", rs.getLong("settled_count"));
                        rows.add(row);
                    }
                }
            }
            String path = exportData("SETTLEMENT_VOLUME", rows, from, to, format);
            audit.record(requestedBy, "CROSSJURIS_REPORT_GENERATED", "type=SETTLEMENT_VOLUME period=" + from + "/" + to);
            reportsGeneratedCounter.increment();
            return new JurisdictionReportResult("SETTLEMENT_VOLUME", from.toString(), to.toString(), rows, path);
        });
    }

    /** Generate regulatory submission compliance rate by jurisdiction. */
    public Promise<JurisdictionReportResult> submissionComplianceReport(
        java.time.LocalDate from, java.time.LocalDate to, String format, String requestedBy
    ) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT oj.code AS jurisdiction, " +
                     "COUNT(rs.report_id)::bigint AS total_submissions, " +
                     "SUM(CASE WHEN rs.status='ACCEPTED' THEN 1 ELSE 0 END)::bigint AS accepted, " +
                     "SUM(CASE WHEN rs.status='REJECTED' THEN 1 ELSE 0 END)::bigint AS rejected " +
                     "FROM operator_jurisdictions oj " +
                     "LEFT JOIN regulatory_submissions rs ON rs.jurisdiction_code=oj.code " +
                     "  AND rs.submitted_at::date BETWEEN ? AND ? " +
                     "WHERE oj.status='ACTIVE' GROUP BY oj.code ORDER BY oj.code"
                 )) {
                ps.setDate(1, Date.valueOf(from)); ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("jurisdiction", rs.getString("jurisdiction"));
                        long total = rs.getLong("total_submissions");
                        long accepted = rs.getLong("accepted");
                        row.put("totalSubmissions", total);
                        row.put("accepted", accepted);
                        row.put("rejected", rs.getLong("rejected"));
                        row.put("complianceRate", total == 0 ? "N/A" : String.format("%.1f%%", accepted * 100.0 / total));
                        rows.add(row);
                    }
                }
            }
            String path = exportData("SUBMISSION_COMPLIANCE", rows, from, to, format);
            audit.record(requestedBy, "CROSSJURIS_REPORT_GENERATED", "type=SUBMISSION_COMPLIANCE");
            reportsGeneratedCounter.increment();
            return new JurisdictionReportResult("SUBMISSION_COMPLIANCE", from.toString(), to.toString(), rows, path);
        });
    }

    /** Register a scheduled report for automated periodic delivery. */
    public Promise<String> scheduleReport(String reportType, String frequency,
                                           List<String> recipients, String createdBy) {
        return Promise.ofBlocking(executor, () -> {
            String recipientsJson = listToJson(recipients);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO jurisdiction_report_schedules (report_type, frequency, recipient_emails, created_by) " +
                     "VALUES (?,?,?::jsonb,?) RETURNING schedule_id"
                 )) {
                ps.setString(1, reportType); ps.setString(2, frequency);
                ps.setString(3, recipientsJson); ps.setString(4, createdBy);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("schedule_id"); }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String exportData(String reportType, List<Map<String, Object>> rows,
                               java.time.LocalDate from, java.time.LocalDate to, String format) throws Exception {
        String period = from + " to " + to;
        return "PDF".equalsIgnoreCase(format)
            ? exporter.exportPdf(reportType, rows, period)
            : exporter.exportCsv(reportType, rows);
    }

    private String listToJson(List<String> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (String s : items) sb.append("\"").append(s).append("\",");
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }
}
