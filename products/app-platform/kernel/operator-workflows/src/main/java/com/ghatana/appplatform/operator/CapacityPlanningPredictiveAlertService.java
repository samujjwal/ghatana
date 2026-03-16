package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose ML-powered capacity planning and predictive alerting for platform resources.
 *              Uses Prophet time-series forecasting (via a local model runner, K-09 governed)
 *              to predict CPU, memory, Kafka consumer lag, DB connections, and storage per service
 *              over the next 7 days, updated daily.
 *              Predictive alert: service forecasted to breach quota within 7 days → SRE notified
 *              with a scale-up recommendation.
 *              Growth anomaly: sustained ≥15% week-over-week growth → CapacityGrowthAlert.
 *              Forecasts exposed in operator dashboard and via K-06 Grafana panels.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-014: AI-powered capacity planning and predictive alerting
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS capacity_forecasts (
 *   forecast_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   service_name   TEXT NOT NULL,
 *   metric_type    TEXT NOT NULL,   -- CPU | MEMORY | KAFKA_LAG | DB_CONNECTIONS | STORAGE
 *   forecast_date  DATE NOT NULL,
 *   predicted_value NUMERIC(18,4) NOT NULL,
 *   quota_limit    NUMERIC(18,4),
 *   will_breach    BOOLEAN NOT NULL DEFAULT FALSE,
 *   model_run_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE (service_name, metric_type, forecast_date)
 * );
 * CREATE TABLE IF NOT EXISTS capacity_alerts (
 *   alert_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   service_name   TEXT NOT NULL,
 *   metric_type    TEXT NOT NULL,
 *   alert_type     TEXT NOT NULL,   -- BREACH_PREDICTED | GROWTH_ANOMALY
 *   breach_date    DATE,
 *   growth_pct     NUMERIC(6,2),
 *   recommendation TEXT NOT NULL,
 *   acknowledged   BOOLEAN NOT NULL DEFAULT FALSE,
 *   fired_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class CapacityPlanningPredictiveAlertService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface TimeSeriesModelPort {
        /**
         * Run a Prophet 7-day forecast for the given metric.
         * Returns a list of {date, predicted_value} maps.
         */
        List<Map<String, Object>> forecast(String serviceName, String metricType,
                                            List<Map<String, Object>> historicPoints) throws Exception;
    }

    public interface MetricsCollectorPort {
        /**
         * Collect the last N days of daily average metric values for a service.
         * Returns {date, value} maps ordered ascending.
         */
        List<Map<String, Object>> collectHistory(String serviceName, String metricType, int days) throws Exception;
        /** Return the configured quota limit for a service+metric. */
        double quotaLimit(String serviceName, String metricType) throws Exception;
    }

    public interface AlertDispatchPort {
        void alertSre(String subject, String body) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final List<String> METRIC_TYPES = List.of("CPU", "MEMORY", "KAFKA_LAG", "DB_CONNECTIONS", "STORAGE");
    private static final double GROWTH_ANOMALY_THRESHOLD = 0.15; // 15% week-over-week

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final TimeSeriesModelPort model;
    private final MetricsCollectorPort collector;
    private final AlertDispatchPort alertDispatch;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter predictiveAlertsCounter;
    private final Counter growthAnomalyAlertsCounter;

    public CapacityPlanningPredictiveAlertService(
        javax.sql.DataSource ds,
        TimeSeriesModelPort model,
        MetricsCollectorPort collector,
        AlertDispatchPort alertDispatch,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                       = ds;
        this.model                    = model;
        this.collector                = collector;
        this.alertDispatch            = alertDispatch;
        this.audit                    = audit;
        this.executor                 = executor;
        this.predictiveAlertsCounter  = Counter.builder("operator.capacity.predictive_alerts").register(registry);
        this.growthAnomalyAlertsCounter = Counter.builder("operator.capacity.growth_anomaly_alerts").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run daily capacity forecast for all services and all metric types.
     * Fires predictive alerts and growth anomaly alerts as needed.
     */
    public Promise<Integer> runDailyForecast(List<String> serviceNames) {
        return Promise.ofBlocking(executor, () -> {
            int alertsFired = 0;
            for (String service : serviceNames) {
                for (String metric : METRIC_TYPES) {
                    try {
                        alertsFired += forecastServiceMetric(service, metric);
                    } catch (Exception e) {
                        // Log and continue — one service failing should not block others
                        audit.record("system", "CAPACITY_FORECAST_ERROR",
                            "service=" + service + " metric=" + metric + " error=" + e.getMessage());
                    }
                }
            }
            audit.record("system", "CAPACITY_FORECAST_RUN", "services=" + serviceNames.size() + " alertsFired=" + alertsFired);
            return alertsFired;
        });
    }

    /** Return 7-day forecast for a specific service + metric. */
    public Promise<List<Map<String, Object>>> getForecast(String serviceName, String metricType) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT forecast_date::text, predicted_value, quota_limit, will_breach " +
                     "FROM capacity_forecasts WHERE service_name=? AND metric_type=? " +
                     "AND forecast_date >= CURRENT_DATE ORDER BY forecast_date"
                 )) {
                ps.setString(1, serviceName); ps.setString(2, metricType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("date",           rs.getString("forecast_date"));
                        row.put("predictedValue", rs.getBigDecimal("predicted_value"));
                        row.put("quotaLimit",     rs.getBigDecimal("quota_limit"));
                        row.put("willBreach",     rs.getBoolean("will_breach"));
                        rows.add(row);
                    }
                }
            }
            return rows;
        });
    }

    /** Return unacknowledged capacity alerts. */
    public Promise<List<Map<String, String>>> getPendingAlerts() {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, String>> alerts = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT alert_id, service_name, metric_type, alert_type, breach_date::text, growth_pct::text, recommendation, fired_at::text " +
                     "FROM capacity_alerts WHERE acknowledged=FALSE ORDER BY fired_at DESC"
                 );
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> a = new LinkedHashMap<>();
                    a.put("alertId",      rs.getString("alert_id"));
                    a.put("service",      rs.getString("service_name"));
                    a.put("metric",       rs.getString("metric_type"));
                    a.put("alertType",    rs.getString("alert_type"));
                    a.put("breachDate",   rs.getString("breach_date"));
                    a.put("growthPct",    rs.getString("growth_pct"));
                    a.put("recommendation", rs.getString("recommendation"));
                    a.put("firedAt",      rs.getString("fired_at"));
                    alerts.add(a);
                }
            }
            return alerts;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private int forecastServiceMetric(String service, String metric) throws Exception {
        List<Map<String, Object>> history = collector.collectHistory(service, metric, 30);
        double quota = collector.quotaLimit(service, metric);
        List<Map<String, Object>> forecasts = model.forecast(service, metric, history);

        int alertsFired = 0;
        java.time.LocalDate earliestBreach = null;

        for (Map<String, Object> point : forecasts) {
            java.time.LocalDate date = java.time.LocalDate.parse(point.get("date").toString());
            double prediction = ((Number) point.get("predicted_value")).doubleValue();
            boolean willBreach = quota > 0 && prediction >= quota;
            if (willBreach && earliestBreach == null) earliestBreach = date;

            persistForecast(service, metric, date, prediction, quota > 0 ? quota : null, willBreach);
        }

        if (earliestBreach != null) {
            String recommendation = "Scale up " + service + " " + metric + " before " + earliestBreach;
            persistAlert(service, metric, "BREACH_PREDICTED", earliestBreach, null, recommendation);
            alertDispatch.alertSre("[Capacity Alert] " + service + " " + metric + " breach predicted", recommendation);
            predictiveAlertsCounter.increment();
            alertsFired++;
        }

        // Growth anomaly: compare last week avg vs prior week avg
        if (history.size() >= 14) {
            double lastWeekAvg = avgLastN(history, 7);
            double priorWeekAvg = avgSlice(history, 7, 14);
            if (priorWeekAvg > 0) {
                double growth = (lastWeekAvg - priorWeekAvg) / priorWeekAvg;
                if (growth >= GROWTH_ANOMALY_THRESHOLD) {
                    String recommendation = String.format("%.0f%% WoW growth in %s %s — investigate runaway usage", growth * 100, service, metric);
                    persistAlert(service, metric, "GROWTH_ANOMALY", null, growth * 100, recommendation);
                    alertDispatch.alertSre("[Capacity Growth] " + service + " " + metric, recommendation);
                    growthAnomalyAlertsCounter.increment();
                    alertsFired++;
                }
            }
        }
        return alertsFired;
    }

    private void persistForecast(String service, String metric, java.time.LocalDate date,
                                  double prediction, Double quota, boolean willBreach) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO capacity_forecasts (service_name, metric_type, forecast_date, predicted_value, quota_limit, will_breach) " +
                 "VALUES (?,?,?,?,?,?) ON CONFLICT (service_name, metric_type, forecast_date) " +
                 "DO UPDATE SET predicted_value=EXCLUDED.predicted_value, will_breach=EXCLUDED.will_breach, model_run_at=NOW()"
             )) {
            ps.setString(1, service); ps.setString(2, metric); ps.setDate(3, Date.valueOf(date));
            ps.setDouble(4, prediction);
            if (quota != null) ps.setDouble(5, quota); else ps.setNull(5, Types.NUMERIC);
            ps.setBoolean(6, willBreach);
            ps.executeUpdate();
        }
    }

    private void persistAlert(String service, String metric, String alertType,
                               java.time.LocalDate breachDate, Double growthPct, String recommendation) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO capacity_alerts (service_name, metric_type, alert_type, breach_date, growth_pct, recommendation) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, service); ps.setString(2, metric); ps.setString(3, alertType);
            if (breachDate != null) ps.setDate(4, Date.valueOf(breachDate)); else ps.setNull(4, Types.DATE);
            if (growthPct != null) ps.setDouble(5, growthPct); else ps.setNull(5, Types.NUMERIC);
            ps.setString(6, recommendation);
            ps.executeUpdate();
        }
    }

    private double avgLastN(List<Map<String, Object>> history, int n) {
        return history.stream().skip(Math.max(0, history.size() - n))
            .mapToDouble(m -> ((Number) m.get("value")).doubleValue()).average().orElse(0);
    }

    private double avgSlice(List<Map<String, Object>> history, int fromEnd, int toEnd) {
        int size = history.size();
        return history.stream().skip(Math.max(0, size - toEnd)).limit(toEnd - fromEnd)
            .mapToDouble(m -> ((Number) m.get("value")).doubleValue()).average().orElse(0);
    }
}
