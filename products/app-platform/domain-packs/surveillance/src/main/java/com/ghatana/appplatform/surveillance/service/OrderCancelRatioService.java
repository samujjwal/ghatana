package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
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
 * @doc.purpose Monitor order cancel ratio per client: cancelled_orders / total_orders.
 *              High cancel ratio (&gt;80%) signals potential spoofing or layering. Rolling 1-hour
 *              and daily calculation. Market makers exempt via ConfigPort exception list.
 *              K-02 configurable threshold. K-06 real-time dashboard data persisted.
 *              Satisfies STORY-D08-006.
 * @doc.layer   Domain
 * @doc.pattern Rolling window cancel ratio; market-maker exemption; K-02 ConfigPort;
 *              Gauge for real-time high-cancel-ratio client count; Counter for alerts.
 */
public class OrderCancelRatioService {

    private static final double DEFAULT_CANCEL_THRESHOLD = 0.80;
    private static final int    SUSTAINED_HOURS          = 2;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final AlertPort        alertPort;
    private final Counter          alertCounter;
    private final AtomicLong       highCancelClientCount = new AtomicLong(0);

    public OrderCancelRatioService(HikariDataSource dataSource, Executor executor,
                                    ConfigPort configPort, AlertPort alertPort,
                                    MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.configPort   = configPort;
        this.alertPort    = alertPort;
        this.alertCounter = Counter.builder("surveillance.cancel_ratio.alerts_total").register(registry);
        Gauge.builder("surveillance.cancel_ratio.high_cancel_clients",
                highCancelClientCount, AtomicLong::get).register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface ConfigPort {
        double getCancelRatioThreshold();
        boolean isMarketMaker(String clientId);
    }

    public interface AlertPort {
        String persistAlert(String clientId, String instrumentId, String alertType,
                            String description, String severity, LocalDate runDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CancelRatioRow(String clientId, long totalOrders, long cancelledOrders,
                                 double cancelRatio, String window) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<CancelRatioRow>> computeDailyRatios(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> computeAndAlert(runDate, "DAILY"));
    }

    public Promise<List<CancelRatioRow>> computeHourlyRatios(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> computeAndAlert(runDate, "HOURLY"));
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private List<CancelRatioRow> computeAndAlert(LocalDate runDate, String window)
            throws SQLException {
        double threshold = configPort.getCancelRatioThreshold();
        if (threshold == 0.0) threshold = DEFAULT_CANCEL_THRESHOLD;

        List<CancelRatioRow> rows = window.equals("DAILY")
                ? loadDailyRatios(runDate) : loadHourlyRatios(runDate);

        long highCount = 0;
        for (CancelRatioRow row : rows) {
            if (configPort.isMarketMaker(row.clientId())) continue;
            if (row.cancelRatio() > threshold) {
                highCount++;
                String desc = "Cancel ratio=%.1f%% (threshold=%.0f%%), %s window, total=%d cancelled=%d"
                        .formatted(row.cancelRatio() * 100, threshold * 100, window,
                                row.totalOrders(), row.cancelledOrders());
                alertPort.persistAlert(row.clientId(), null, "HIGH_CANCEL_RATIO",
                        desc, "MEDIUM", runDate);
                alertCounter.increment();
            }
        }
        highCancelClientCount.set(highCount);
        persistDashboardSummary(runDate, window, rows, threshold);
        return rows;
    }

    private List<CancelRatioRow> loadDailyRatios(LocalDate runDate) throws SQLException {
        List<CancelRatioRow> rows = new ArrayList<>();
        String sql = """
                SELECT client_id,
                       COUNT(*) AS total_orders,
                       COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_orders,
                       CAST(COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS DOUBLE PRECISION) /
                           NULLIF(COUNT(*), 0) AS cancel_ratio
                FROM orders
                WHERE DATE(submitted_at) = ?
                GROUP BY client_id HAVING COUNT(*) >= 10
                ORDER BY cancel_ratio DESC
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CancelRatioRow(rs.getString("client_id"), rs.getLong("total_orders"),
                            rs.getLong("cancelled_orders"), rs.getDouble("cancel_ratio"), "DAILY"));
                }
            }
        }
        return rows;
    }

    private List<CancelRatioRow> loadHourlyRatios(LocalDate runDate) throws SQLException {
        List<CancelRatioRow> rows = new ArrayList<>();
        String sql = """
                SELECT client_id,
                       COUNT(*) AS total_orders,
                       COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_orders,
                       CAST(COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS DOUBLE PRECISION) /
                           NULLIF(COUNT(*), 0) AS cancel_ratio
                FROM orders
                WHERE submitted_at >= NOW() - INTERVAL '1 hour'
                GROUP BY client_id HAVING COUNT(*) >= 5
                ORDER BY cancel_ratio DESC
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CancelRatioRow(rs.getString("client_id"), rs.getLong("total_orders"),
                            rs.getLong("cancelled_orders"), rs.getDouble("cancel_ratio"), "HOURLY"));
                }
            }
        }
        return rows;
    }

    private void persistDashboardSummary(LocalDate runDate, String window,
                                          List<CancelRatioRow> rows, double threshold)
            throws SQLException {
        long highCount = rows.stream().filter(r -> r.cancelRatio() > threshold).count();
        String sql = """
                INSERT INTO cancel_ratio_dashboard (run_date, window_type, client_count, high_ratio_count, computed_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (run_date, window_type) DO UPDATE
                SET client_count = EXCLUDED.client_count,
                    high_ratio_count = EXCLUDED.high_ratio_count,
                    computed_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setString(2, window);
            ps.setInt(3, rows.size());
            ps.setLong(4, highCount);
            ps.executeUpdate();
        }
    }
}
