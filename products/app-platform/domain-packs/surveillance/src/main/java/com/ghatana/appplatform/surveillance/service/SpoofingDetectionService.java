package com.ghatana.appplatform.surveillance.service;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detects spoofing: a manipulative pattern where a large order (spoof order)
 *              is placed to move prices, then cancelled once the price moves in the desired
 *              direction, allowing the trader to profit from a real trade in the opposite
 *              direction. Detection timeline: (1) large order placed, (2) price moves ≥
 *              PRICE_MOVE_THRESHOLD_BPS in desired direction, (3) order cancelled within
 *              CANCEL_LATENCY_MS, (4) opposite-side fill appears within REACTION_WINDOW_MS.
 *              Confidence scoring is based on price move magnitude and cancel latency.
 * @doc.layer   Domain
 * @doc.pattern Event-driven (OrderPlaced + OrderCancelled + OrderFilled composite);
 *              K-03 RulesEnginePort for threshold configuration; JSON event timeline storage;
 *              INSERT-only audit in spoofing_alerts.
 */
public class SpoofingDetectionService {

    private static final Logger log = LoggerFactory.getLogger(SpoofingDetectionService.class);

    private static final double DEFAULT_PRICE_MOVE_THRESHOLD_BPS = 20.0;  // 20 bps = 0.20%
    private static final long   DEFAULT_CANCEL_LATENCY_MS        = 30_000; // 30 seconds
    private static final long   DEFAULT_REACTION_WINDOW_MS       = 60_000; // 1 minute
    private static final double DEFAULT_SPOOF_SIZE_MULTIPLE       = 5.0;   // spoof order ≥ 5× avg

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RulesEnginePort  rulesEngine;
    private final Counter          detectedCounter;
    private final Counter          screenedCounter;

    public SpoofingDetectionService(HikariDataSource dataSource, Executor executor,
                                    RulesEnginePort rulesEngine, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.rulesEngine     = rulesEngine;
        this.detectedCounter = registry.counter("surveillance.spoofing.detected");
        this.screenedCounter = registry.counter("surveillance.spoofing.screened");
    }

    // ─── Inner port (K-03) ───────────────────────────────────────────────────

    public interface RulesEnginePort {
        double getPriceMoveThresholdBps(String instrumentId);
        long   getCancelLatencyMs(String instrumentId);
        long   getReactionWindowMs(String instrumentId);
        double getSpoofSizeMultiple(String instrumentId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OrderEvent(
        String  orderId,
        String  clientId,
        String  instrumentId,
        String  side,          // BUY | SELL
        double  quantity,
        double  price,
        String  eventType,     // PLACED | CANCELLED | FILLED
        Instant eventTime
    ) {}

    public record SpoofingAlert(
        String  alertId,
        String  clientId,
        String  instrumentId,
        String  spoofOrderId,
        String  realOrderId,
        double  priceMoveActualBps,
        long    cancelLatencyMs,
        double  confidenceScore,
        String  eventTimelineJson,
        String  alertStatus,
        Instant detectedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Evaluate a cancelled order for spoofing behaviour.
     * Called when an OrderCancelled event is received for a large order.
     */
    public Promise<List<SpoofingAlert>> onOrderCancelled(OrderEvent cancelEvent) {
        return Promise.ofBlocking(executor, () -> {
            screenedCounter.increment();
            double spoofMultiple   = rulesEngine.getSpoofSizeMultiple(cancelEvent.instrumentId());

            // Only screen large orders (≥ spoofMultiple × avg daily qty)
            double avgDailyQty = loadAvgDailyQuantity(cancelEvent.instrumentId());
            if (cancelEvent.quantity() < spoofMultiple * avgDailyQty) {
                return List.<SpoofingAlert>of();
            }

            // Find original PLACED event for this order
            OrderEvent placed = loadPlacedEvent(cancelEvent.orderId());
            if (placed == null) return List.<SpoofingAlert>of();

            long cancelLatencyMs = cancelEvent.eventTime().toEpochMilli()
                                 - placed.eventTime().toEpochMilli();
            long maxLatency = rulesEngine.getCancelLatencyMs(cancelEvent.instrumentId());
            if (cancelLatencyMs > maxLatency) return List.<SpoofingAlert>of();

            // Measure price move between placed and cancelled
            double priceMoveActualBps = measurePriceMove(
                cancelEvent.instrumentId(), placed.eventTime(), cancelEvent.eventTime(), placed.side());

            double threshBps = rulesEngine.getPriceMoveThresholdBps(cancelEvent.instrumentId());
            if (Math.abs(priceMoveActualBps) < threshBps) return List.<SpoofingAlert>of();

            // Check if real opposite-side trade appears within reaction window
            String oppSide   = "BUY".equals(placed.side()) ? "SELL" : "BUY";
            long   window    = rulesEngine.getReactionWindowMs(cancelEvent.instrumentId());
            boolean hasReal  = hasOppositeFillWithinWindow(
                cancelEvent.clientId(), cancelEvent.instrumentId(), oppSide,
                cancelEvent.eventTime(), window);

            if (!hasReal) return List.<SpoofingAlert>of();

            double confidence = computeConfidence(priceMoveActualBps, cancelLatencyMs, cancelEvent.quantity(), avgDailyQty);
            String timeline   = buildTimelineJson(placed, cancelEvent, priceMoveActualBps);
            String realOrderId = loadOppositeFillOrderId(cancelEvent.clientId(),
                                                          cancelEvent.instrumentId(),
                                                          oppSide, cancelEvent.eventTime(), window);

            SpoofingAlert alert = new SpoofingAlert(
                UUID.randomUUID().toString(),
                cancelEvent.clientId(),
                cancelEvent.instrumentId(),
                cancelEvent.orderId(),
                realOrderId,
                priceMoveActualBps,
                cancelLatencyMs,
                confidence,
                timeline,
                "OPEN",
                Instant.now()
            );
            persistAlert(alert);
            detectedCounter.increment();
            log.warn("Spoofing detected clientId={} instrumentId={} priceBps={} latencyMs={} conf={}",
                     cancelEvent.clientId(), cancelEvent.instrumentId(),
                     priceMoveActualBps, cancelLatencyMs, confidence);
            return List.of(alert);
        });
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private double computeConfidence(double priceMoveActualBps, long cancelLatencyMs,
                                     double qty, double avgQty) {
        // Higher price move, faster cancel, and larger relative size → higher confidence
        double moveFactor    = Math.min(Math.abs(priceMoveActualBps) / 50.0, 0.40); // cap at 40 bps
        double latencyFactor = cancelLatencyMs < 5_000 ? 0.35 : (cancelLatencyMs < 15_000 ? 0.20 : 0.10);
        double sizeFactor    = Math.min((qty / avgQty) / 10.0, 0.25);
        return Math.min(moveFactor + latencyFactor + sizeFactor, 1.0);
    }

    private String buildTimelineJson(OrderEvent placed, OrderEvent cancelled, double moveBps) {
        return String.format(
            "[{\"event\":\"PLACED\",\"time\":\"%s\",\"qty\":%.0f,\"price\":%.4f}," +
            "{\"event\":\"PRICE_MOVE_BPS\",\"value\":%.2f}," +
            "{\"event\":\"CANCELLED\",\"time\":\"%s\",\"latency_ms\":%d}]",
            placed.eventTime(), placed.quantity(), placed.price(),
            moveBps,
            cancelled.eventTime(),
            cancelled.eventTime().toEpochMilli() - placed.eventTime().toEpochMilli()
        );
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private double loadAvgDailyQuantity(String instrumentId) throws SQLException {
        String sql = """
            SELECT COALESCE(AVG(daily_vol), 1) FROM (
                SELECT DATE(fill_time) AS d, SUM(quantity) AS daily_vol
                FROM order_fills WHERE instrument_id = ?
                GROUP BY DATE(fill_time)
                ORDER BY d DESC LIMIT 20
            ) t
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private OrderEvent loadPlacedEvent(String orderId) throws SQLException {
        String sql = """
            SELECT order_id, client_id, instrument_id, side, quantity, price, event_type, event_time
            FROM order_events WHERE order_id = ? AND event_type = 'PLACED' LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderEvent(
                        rs.getString("order_id"),
                        rs.getString("client_id"),
                        rs.getString("instrument_id"),
                        rs.getString("side"),
                        rs.getDouble("quantity"),
                        rs.getDouble("price"),
                        rs.getString("event_type"),
                        rs.getObject("event_time", Instant.class)
                    );
                }
            }
        }
        return null;
    }

    private double measurePriceMove(String instrumentId, Instant from, Instant to, String side) throws SQLException {
        String sql = """
            SELECT
              (SELECT last_price FROM intraday_ticks WHERE instrument_id = ? AND tick_time <= ?
               ORDER BY tick_time DESC LIMIT 1) AS price_at_place,
              (SELECT last_price FROM intraday_ticks WHERE instrument_id = ? AND tick_time <= ?
               ORDER BY tick_time DESC LIMIT 1) AS price_at_cancel
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setObject(2, from);
            ps.setString(3, instrumentId);
            ps.setObject(4, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double p1 = rs.getDouble("price_at_place");
                    double p2 = rs.getDouble("price_at_cancel");
                    if (p1 <= 0) return 0.0;
                    int sign = "BUY".equals(side) ? 1 : -1;
                    return sign * (p2 - p1) / p1 * 10_000.0; // in bps
                }
            }
        }
        return 0.0;
    }

    private boolean hasOppositeFillWithinWindow(String clientId, String instrumentId,
                                                String side, Instant after, long windowMs) throws SQLException {
        String sql = """
            SELECT 1 FROM order_fills
            WHERE client_id = ? AND instrument_id = ? AND side = ?
              AND fill_time > ? AND fill_time <= ?
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, instrumentId);
            ps.setString(3, side);
            ps.setObject(4, after);
            ps.setObject(5, after.plusMillis(windowMs));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String loadOppositeFillOrderId(String clientId, String instrumentId,
                                            String side, Instant after, long windowMs) throws SQLException {
        String sql = """
            SELECT order_id FROM order_fills
            WHERE client_id = ? AND instrument_id = ? AND side = ?
              AND fill_time > ? AND fill_time <= ?
            ORDER BY fill_time ASC LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, instrumentId);
            ps.setString(3, side);
            ps.setObject(4, after);
            ps.setObject(5, after.plusMillis(windowMs));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("order_id") : null;
            }
        }
    }

    private void persistAlert(SpoofingAlert alert) throws SQLException {
        String sql = """
            INSERT INTO spoofing_alerts (
                alert_id, client_id, instrument_id, spoof_order_id, real_order_id,
                price_move_bps, cancel_latency_ms, confidence_score, event_timeline_json,
                alert_status, detected_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (spoof_order_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alert.alertId());
            ps.setString(2, alert.clientId());
            ps.setString(3, alert.instrumentId());
            ps.setString(4, alert.spoofOrderId());
            ps.setString(5, alert.realOrderId());
            ps.setDouble(6, alert.priceMoveActualBps());
            ps.setLong(7, alert.cancelLatencyMs());
            ps.setDouble(8, alert.confidenceScore());
            ps.setString(9, alert.eventTimelineJson());
            ps.setString(10, alert.alertStatus());
            ps.setObject(11, alert.detectedAt());
            ps.executeUpdate();
        }
    }
}
