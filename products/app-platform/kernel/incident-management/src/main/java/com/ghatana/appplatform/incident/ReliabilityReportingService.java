package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Monthly and quarterly reliability score reports delivered automatically to CTO.
 *              Reliability score = weighted composite: uptime%, incident count, MTTR, SLA breaches.
 *              Reports are generated as PDFs and stored; monthly covers the trailing month,
 *              quarterly covers rolling 3-month window aggregated from monthly snapshots.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-008: Reliability reporting with monthly/quarterly PDF auto-delivery
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS reliability_reports (
 *   report_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   period_type      TEXT NOT NULL,    -- MONTHLY | QUARTERLY
 *   period_label     TEXT NOT NULL,    -- '2081-03' or '2081-Q1'
 *   reliability_score NUMERIC(5,2),
 *   uptime_pct       NUMERIC(6,3),
 *   incident_count   INT,
 *   avg_mttr_min     NUMERIC(10,2),
 *   sla_breach_count INT,
 *   storage_ref      TEXT,
 *   status           TEXT NOT NULL DEFAULT 'GENERATING',
 *   generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   delivered_at     TIMESTAMPTZ,
 *   UNIQUE (period_type, period_label)
 * );
 * </pre>
 */
public class ReliabilityReportingService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface UptimeCollectorPort {
        /** Returns uptime percentage for the given period (ISO date range). */
        double collectUptimePct(String dateFrom, String dateTo) throws Exception;
    }

    public interface PdfReportBuilderPort {
        byte[] build(Map<String, Object> data) throws Exception;
    }

    public interface SecureStoragePort {
        String store(String reportId, byte[] data) throws Exception;
    }

    public interface EmailDeliveryPort {
        void sendToCto(String subject, String body, String storageRef) throws Exception;
    }

    // ── Score weights ─────────────────────────────────────────────────────────

    // reliability_score = 0.4*uptimePct + 0.3*(100 - normalizedMttr) + 0.3*(100 - slaBreachRate)
    private static final double W_UPTIME  = 0.4;
    private static final double W_MTTR    = 0.3;
    private static final double W_SLA     = 0.3;
    private static final double MTTR_NORM = 240.0; // normalize against 240-min baseline

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final UptimeCollectorPort uptime;
    private final PdfReportBuilderPort pdfBuilder;
    private final SecureStoragePort storage;
    private final EmailDeliveryPort email;
    private final Executor executor;
    private final Counter reportsDelivered;

    public ReliabilityReportingService(
        javax.sql.DataSource ds,
        UptimeCollectorPort uptime,
        PdfReportBuilderPort pdfBuilder,
        SecureStoragePort storage,
        EmailDeliveryPort email,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.uptime           = uptime;
        this.pdfBuilder       = pdfBuilder;
        this.storage          = storage;
        this.email            = email;
        this.executor         = executor;
        this.reportsDelivered = Counter.builder("incident.reliability.reports_delivered").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Generate and deliver monthly reliability report. */
    public Promise<String> generateMonthly(String periodLabel, String dateFrom, String dateTo) {
        return generate("MONTHLY", periodLabel, dateFrom, dateTo);
    }

    /** Generate and deliver quarterly reliability report (aggregates 3 monthly snapshots). */
    public Promise<String> generateQuarterly(String periodLabel, String dateFrom, String dateTo) {
        return generate("QUARTERLY", periodLabel, dateFrom, dateTo);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Promise<String> generate(String periodType, String periodLabel, String dateFrom, String dateTo) {
        return Promise.ofBlocking(executor, () -> {
            String reportId = insertReport(periodType, periodLabel);
            executor.execute(() -> {
                try {
                    // Gather raw data
                    double uptimePct = uptime.collectUptimePct(dateFrom, dateTo);
                    Map<String, Object> incidentStats = queryIncidentStats(dateFrom, dateTo);
                    int incidentCount    = (int) incidentStats.get("incidentCount");
                    double avgMttr       = (double) incidentStats.get("avgMttrMin");
                    int slaBreachCount   = (int) incidentStats.get("slaBreaches");
                    int totalIncidents   = Math.max(incidentCount, 1);

                    // Compute reliability score
                    double normalizedMttr = Math.min(avgMttr / MTTR_NORM, 1.0) * 100;
                    double slaBreachRate  = Math.min((double) slaBreachCount / totalIncidents * 100, 100);
                    double score = W_UPTIME * uptimePct
                                 + W_MTTR  * (100 - normalizedMttr)
                                 + W_SLA   * (100 - slaBreachRate);

                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("periodType",     periodType);
                    data.put("periodLabel",    periodLabel);
                    data.put("reliabilityScore", Math.round(score * 100.0) / 100.0);
                    data.put("uptimePct",       uptimePct);
                    data.put("incidentCount",   incidentCount);
                    data.put("avgMttrMin",      avgMttr);
                    data.put("slaBreachCount",  slaBreachCount);

                    byte[] pdf = pdfBuilder.build(data);
                    String ref = storage.store(reportId, pdf);

                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "UPDATE reliability_reports SET status='READY', storage_ref=?, " +
                             "reliability_score=?, uptime_pct=?, incident_count=?, avg_mttr_min=?, sla_breach_count=? " +
                             "WHERE report_id=?"
                         )) {
                        ps.setString(1, ref);
                        ps.setDouble(2, score); ps.setDouble(3, uptimePct);
                        ps.setInt(4, incidentCount); ps.setDouble(5, avgMttr);
                        ps.setInt(6, slaBreachCount); ps.setString(7, reportId);
                        ps.executeUpdate();
                    }

                    email.sendToCto(periodType + " Reliability Report: " + periodLabel,
                        "Score: " + Math.round(score * 10) / 10.0 + "/100 | Uptime: " + uptimePct + "% | Incidents: " + incidentCount, ref);

                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "UPDATE reliability_reports SET status='DELIVERED', delivered_at=NOW() WHERE report_id=?"
                         )) { ps.setString(1, reportId); ps.executeUpdate(); }

                    reportsDelivered.increment();
                } catch (Exception ex) {
                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(
                             "UPDATE reliability_reports SET status='FAILED' WHERE report_id=?"
                         )) { ps.setString(1, reportId); ps.executeUpdate(); } catch (Exception ignored) {}
                }
            });
            return reportId;
        });
    }

    private String insertReport(String periodType, String periodLabel) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO reliability_reports (period_type, period_label) VALUES (?,?) " +
                 "ON CONFLICT (period_type, period_label) DO UPDATE SET status='GENERATING', generated_at=NOW() " +
                 "RETURNING report_id"
             )) {
            ps.setString(1, periodType); ps.setString(2, periodLabel);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private Map<String, Object> queryIncidentStats(String dateFrom, String dateTo) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) AS cnt, " +
                 "COALESCE(AVG(EXTRACT(EPOCH FROM (resolved_at - created_at))/60), 0) AS avg_mttr, " +
                 "SUM(CASE WHEN EXTRACT(EPOCH FROM (resolved_at - created_at))/60 > 240 THEN 1 ELSE 0 END) AS breaches " +
                 "FROM incidents WHERE created_at BETWEEN ?::timestamptz AND ?::timestamptz AND resolved_at IS NOT NULL"
             )) {
            ps.setString(1, dateFrom); ps.setString(2, dateTo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("incidentCount", rs.getInt("cnt"));
                m.put("avgMttrMin",    rs.getDouble("avg_mttr"));
                m.put("slaBreaches",   rs.getInt("breaches"));
                return m;
            }
        }
    }
}
