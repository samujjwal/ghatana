package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Compute and track MTTA (Mean Time To Acknowledge), MTTR (Mean Time To Resolve),
 *              and MTTD (Mean Time To Detect) for incidents by severity.
 *              SLA targets: P1 MTTA &lt; 5 min, P1 MTTR &lt; 4 h; P2 MTTA &lt; 15 min, P2 MTTR &lt; 8 h.
 *              12-month rolling trend stored for dashboard charts.
 *              Publishes Micrometer gauges for Prometheus alerting on SLA breaches.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-007: MTTA/MTTR/MTTD incident metrics with SLA breach tracking
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS incident_metric_snapshots (
 *   snapshot_id   TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   month_label   TEXT NOT NULL,   -- 'YYYY-MM'
 *   severity      TEXT NOT NULL,
 *   incident_count INT NOT NULL DEFAULT 0,
 *   avg_mttd_min  NUMERIC(10,2),
 *   avg_mtta_min  NUMERIC(10,2),
 *   avg_mttr_min  NUMERIC(10,2),
 *   sla_breach_count INT NOT NULL DEFAULT 0,
 *   computed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE (month_label, severity)
 * );
 * </pre>
 *
 * Source data expected in `incidents` table with columns:
 *   incident_id, severity, detected_at, acknowledged_at, resolved_at
 */
public class IncidentMetricsMttrService {

    // ── SLA constants (minutes) ───────────────────────────────────────────────

    private static final Map<String, int[]> SLA_MINUTES = Map.of(
        "P1", new int[]{5,  240},   // [MTTA, MTTR]
        "P2", new int[]{15, 480},
        "P3", new int[]{60, 1440}
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final Executor executor;
    private final Gauge p1MttaGauge;
    private final Gauge p1MttrGauge;

    public IncidentMetricsMttrService(
        javax.sql.DataSource ds,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds       = ds;
        this.executor = executor;
        // Live gauges fed by latest snapshot
        this.p1MttaGauge = Gauge.builder("incident.sla.p1_avg_mtta_min", this, svc -> {
            try { return svc.latestAvg("P1", "avg_mtta_min"); } catch (Exception e) { return 0; }
        }).register(registry);
        this.p1MttrGauge = Gauge.builder("incident.sla.p1_avg_mttr_min", this, svc -> {
            try { return svc.latestAvg("P1", "avg_mttr_min"); } catch (Exception e) { return 0; }
        }).register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Compute and store the monthly MTTA/MTTR/MTTD snapshot for a given month (YYYY-MM).
     * Should be called by a monthly batch job.
     */
    public Promise<Map<String, Object>> computeMonthly(String monthLabel, String severity) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Object> metrics = computeFromIncidents(monthLabel, severity);
            // UPSERT snapshot
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO incident_metric_snapshots " +
                     "(month_label, severity, incident_count, avg_mttd_min, avg_mtta_min, avg_mttr_min, sla_breach_count) " +
                     "VALUES (?,?,?,?,?,?,?) " +
                     "ON CONFLICT (month_label, severity) DO UPDATE SET " +
                     "incident_count=EXCLUDED.incident_count, avg_mttd_min=EXCLUDED.avg_mttd_min, " +
                     "avg_mtta_min=EXCLUDED.avg_mtta_min, avg_mttr_min=EXCLUDED.avg_mttr_min, " +
                     "sla_breach_count=EXCLUDED.sla_breach_count, computed_at=NOW()"
                 )) {
                ps.setString(1, monthLabel); ps.setString(2, severity);
                ps.setInt(3, (int) metrics.get("incidentCount"));
                ps.setObject(4, metrics.get("avgMttdMin")); ps.setObject(5, metrics.get("avgMttaMin"));
                ps.setObject(6, metrics.get("avgMttrMin")); ps.setInt(7, (int) metrics.get("slaBreaches"));
                ps.executeUpdate();
            }
            return metrics;
        });
    }

    /** 12-month rolling trend by severity. */
    public Promise<List<Map<String, Object>>> trend(String severity) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT month_label, incident_count, avg_mttd_min, avg_mtta_min, avg_mttr_min, sla_breach_count " +
                     "FROM incident_metric_snapshots WHERE severity=? ORDER BY month_label DESC LIMIT 12"
                 )) {
                ps.setString(1, severity);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("monthLabel",    rs.getString("month_label"));
                        m.put("incidentCount", rs.getInt("incident_count"));
                        m.put("avgMttdMin",    rs.getObject("avg_mttd_min"));
                        m.put("avgMttaMin",    rs.getObject("avg_mtta_min"));
                        m.put("avgMttrMin",    rs.getObject("avg_mttr_min"));
                        m.put("slaBreaches",   rs.getInt("sla_breach_count"));
                        rows.add(m);
                    }
                }
            }
            return rows;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> computeFromIncidents(String monthLabel, String severity) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) AS cnt, " +
                 "AVG(EXTRACT(EPOCH FROM (acknowledged_at - detected_at))/60) AS avg_mttd, " +
                 "AVG(EXTRACT(EPOCH FROM (acknowledged_at - created_at))/60) AS avg_mtta, " +
                 "AVG(EXTRACT(EPOCH FROM (resolved_at - created_at))/60) AS avg_mttr " +
                 "FROM incidents " +
                 "WHERE severity=? AND TO_CHAR(created_at, 'YYYY-MM')=? AND resolved_at IS NOT NULL"
             )) {
            ps.setString(1, severity); ps.setString(2, monthLabel);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int cnt = rs.getInt("cnt");
                double mttd = rs.getDouble("avg_mttd");
                double mtta = rs.getDouble("avg_mtta");
                double mttr = rs.getDouble("avg_mttr");

                int[] sla = SLA_MINUTES.getOrDefault(severity, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE});
                // Count SLA breaches: incidents where MTTA or MTTR exceeded threshold
                int breaches = countSlaBreaches(c, monthLabel, severity, sla[0], sla[1]);

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("incidentCount", cnt);
                m.put("avgMttdMin",   cnt == 0 ? null : Math.round(mttd * 100.0) / 100.0);
                m.put("avgMttaMin",   cnt == 0 ? null : Math.round(mtta * 100.0) / 100.0);
                m.put("avgMttrMin",   cnt == 0 ? null : Math.round(mttr * 100.0) / 100.0);
                m.put("slaBreaches",  breaches);
                return m;
            }
        }
    }

    private int countSlaBreaches(Connection c, String monthLabel, String severity,
                                  int mttaSlaMin, int mttrSlaMin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT COUNT(*) FROM incidents WHERE severity=? AND TO_CHAR(created_at,'YYYY-MM')=? " +
            "AND resolved_at IS NOT NULL AND (" +
            "EXTRACT(EPOCH FROM (acknowledged_at - created_at))/60 > ? OR " +
            "EXTRACT(EPOCH FROM (resolved_at - created_at))/60 > ?)"
        )) {
            ps.setString(1, severity); ps.setString(2, monthLabel);
            ps.setInt(3, mttaSlaMin); ps.setInt(4, mttrSlaMin);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    double latestAvg(String severity, String column) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT " + column + " FROM incident_metric_snapshots WHERE severity=? ORDER BY month_label DESC LIMIT 1"
             )) {
            ps.setString(1, severity);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        }
    }
}
