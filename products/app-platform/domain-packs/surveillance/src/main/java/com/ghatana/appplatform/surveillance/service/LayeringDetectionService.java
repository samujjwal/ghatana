package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detect layering: placing multiple orders at different price levels to create
 *              artificial order book depth, while trading on the other side. Detection: if a
 *              single client represents &gt;X% of depth on one side (configurable via ConfigPort)
 *              AND is actively trading on the opposite side within the same session, flag as
 *              layering. Order book depth data from D-04 orderbook snapshots.
 *              Satisfies STORY-D08-005.
 * @doc.layer   Domain
 * @doc.pattern Order-book depth analysis; D-04 integration; cross-side trade detection;
 *              K-02 ConfigPort; Counter for layering alerts.
 */
public class LayeringDetectionService {

    private static final double DEFAULT_DEPTH_THRESHOLD = 0.30; // 30% of one side

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final AlertPort        alertPort;
    private final Counter          alertCounter;

    public LayeringDetectionService(HikariDataSource dataSource, Executor executor,
                                     ConfigPort configPort, AlertPort alertPort,
                                     MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.configPort   = configPort;
        this.alertPort    = alertPort;
        this.alertCounter = Counter.builder("surveillance.layering.alerts_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface ConfigPort {
        double getDepthConcentrationThreshold();
        boolean isMarketMaker(String clientId);
    }

    public interface AlertPort {
        String persistAlert(String clientId, String instrumentId, String alertType,
                            String description, String severity, LocalDate runDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record LayeringAlert(String alertId, String clientId, String instrumentId,
                                LocalDate runDate, double bidDepthPct, double askDepthPct,
                                int cancelledOrderCount, boolean crossSideTradeDetected) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<LayeringAlert>> detect(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> runDetection(runDate));
    }

    // ─── Detection logic ─────────────────────────────────────────────────────

    private List<LayeringAlert> runDetection(LocalDate runDate) throws SQLException {
        List<LayeringAlert> alerts = new ArrayList<>();
        double threshold = configPort.getDepthConcentrationThreshold();
        if (threshold == 0.0) threshold = DEFAULT_DEPTH_THRESHOLD;

        List<DepthRow> rows = loadDepthConcentrations(runDate);
        for (DepthRow row : rows) {
            if (configPort.isMarketMaker(row.clientId())) continue;

            boolean bidConcentrated  = row.clientBidPct() > threshold;
            boolean askConcentrated  = row.clientAskPct() > threshold;
            boolean crossSideTrade   = false;

            if (bidConcentrated || askConcentrated) {
                // Check if client also traded on the opposite side
                crossSideTrade = hasOppositeSideTrade(row.clientId(), row.instrumentId(), runDate,
                        bidConcentrated ? "SELL" : "BUY");
            }

            if ((bidConcentrated || askConcentrated) && crossSideTrade) {
                String desc = "Bid depth=%.1f%%, Ask depth=%.1f%%, cross-side trade detected".formatted(
                        row.clientBidPct() * 100, row.clientAskPct() * 100);
                String alertId = alertPort.persistAlert(row.clientId(), row.instrumentId(),
                        "LAYERING", desc, "HIGH", runDate);
                alerts.add(new LayeringAlert(alertId, row.clientId(), row.instrumentId(), runDate,
                        row.clientBidPct(), row.clientAskPct(), row.cancelledOrderCount(), true));
                alertCounter.increment();
            }
        }
        return alerts;
    }

    private record DepthRow(String clientId, String instrumentId, double clientBidPct,
                            double clientAskPct, int cancelledOrderCount) {}

    private List<DepthRow> loadDepthConcentrations(LocalDate runDate) throws SQLException {
        List<DepthRow> rows = new ArrayList<>();
        String sql = """
                SELECT o.client_id, o.instrument_id,
                       COALESCE(SUM(CASE WHEN o.side='BUY' THEN o.quantity ELSE 0 END) /
                           NULLIF(SUM(SUM(CASE WHEN o.side='BUY' THEN o.quantity ELSE 0 END))
                               OVER (PARTITION BY o.instrument_id), 0), 0) AS client_bid_pct,
                       COALESCE(SUM(CASE WHEN o.side='SELL' THEN o.quantity ELSE 0 END) /
                           NULLIF(SUM(SUM(CASE WHEN o.side='SELL' THEN o.quantity ELSE 0 END))
                               OVER (PARTITION BY o.instrument_id), 0), 0) AS client_ask_pct,
                       COUNT(CASE WHEN o.status='CANCELLED' THEN 1 END) AS cancelled_orders
                FROM orders o
                WHERE DATE(o.submitted_at) = ? AND o.status IN ('ACTIVE','CANCELLED','PARTIAL')
                GROUP BY o.client_id, o.instrument_id
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new DepthRow(rs.getString("client_id"), rs.getString("instrument_id"),
                            rs.getDouble("client_bid_pct"), rs.getDouble("client_ask_pct"),
                            rs.getInt("cancelled_orders")));
                }
            }
        }
        return rows;
    }

    private boolean hasOppositeSideTrade(String clientId, String instrumentId,
                                          LocalDate runDate, String side) throws SQLException {
        String sql = "SELECT 1 FROM trades WHERE client_id = ? AND instrument_id = ? AND trade_date = ? AND side = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, instrumentId);
            ps.setObject(3, runDate);
            ps.setString(4, side);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
