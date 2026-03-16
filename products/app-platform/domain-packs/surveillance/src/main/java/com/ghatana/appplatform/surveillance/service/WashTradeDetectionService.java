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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detects wash trading: a client simultaneously buys and sells the same instrument
 *              within a configurable time window (default: 5 minutes). Listens to OrderFilled
 *              events from EMS. Confidence scoring is based on price proximity to market VWAP —
 *              trades at exactly the same price score higher. Only alerts with confidence ≥
 *              MIN_CONFIDENCE are persisted to wash_trade_alerts for regulatory review.
 *              The K-03 rules engine provides configurable detection parameters.
 * @doc.layer   Domain
 * @doc.pattern Event-driven (OrderFilled); K-03 RulesEnginePort for parameter config;
 *              confidence scoring; INSERT-only audit trail.
 */
public class WashTradeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(WashTradeDetectionService.class);

    private static final int    DEFAULT_WINDOW_MINUTES  = 5;
    private static final double MIN_CONFIDENCE          = 0.60;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RulesEnginePort  rulesEngine;
    private final Counter          detectedCounter;
    private final Counter          screenedCounter;

    public WashTradeDetectionService(HikariDataSource dataSource, Executor executor,
                                     RulesEnginePort rulesEngine, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.rulesEngine     = rulesEngine;
        this.detectedCounter = registry.counter("surveillance.wash_trade.detected");
        this.screenedCounter = registry.counter("surveillance.wash_trade.screened");
    }

    // ─── Inner port (K-03) ───────────────────────────────────────────────────

    /**
     * K-03 RulesEnginePort — fetch configurable detection parameters per instrument/client.
     */
    public interface RulesEnginePort {
        int    getWindowMinutes(String instrumentId);   // default 5
        double getMinConfidence(String instrumentId);   // default 0.60
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OrderFilled(
        String  fillId,
        String  orderId,
        String  clientId,
        String  instrumentId,
        String  side,          // BUY | SELL
        double  quantity,
        double  fillPrice,
        Instant fillTime
    ) {}

    public record WashTradeAlert(
        String  alertId,
        String  clientId,
        String  instrumentId,
        String  buyFillId,
        String  sellFillId,
        double  confidenceScore,
        String  alertStatus,   // OPEN | REVIEWED | DISMISSED | ESCALATED
        Instant detectedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Screen a fill for wash trading against recent opposite-side fills by the same client.
     */
    public Promise<List<WashTradeAlert>> onOrderFilled(OrderFilled fill) {
        return Promise.ofBlocking(executor, () -> {
            screenedCounter.increment();
            int windowMins    = rulesEngine.getWindowMinutes(fill.instrumentId());
            double minConf    = rulesEngine.getMinConfidence(fill.instrumentId());

            List<RecentFill> opposites = loadOppositeRecentFills(fill, windowMins);
            List<WashTradeAlert> alerts = opposites.stream()
                .map(opp -> scoreWashTrade(fill, opp))
                .filter(alert -> alert.confidenceScore() >= minConf)
                .toList();

            for (WashTradeAlert alert : alerts) {
                persistAlert(alert);
                detectedCounter.increment();
                log.warn("Wash trade detected clientId={} instrumentId={} confidence={}",
                         fill.clientId(), fill.instrumentId(), alert.confidenceScore());
            }
            return alerts;
        });
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private record RecentFill(String fillId, String side, double quantity, double price, Instant fillTime) {}

    private WashTradeAlert scoreWashTrade(OrderFilled fill, RecentFill opposite) {
        // Confidence factors:
        // 1. Price proximity: trades at same price = +0.40
        // 2. Quantity match: same quantity = +0.30
        // 3. Time proximity: within 1 min = +0.30, within 5 min = +0.15
        double priceDiffPct = Math.abs(fill.fillPrice() - opposite.price()) / fill.fillPrice();
        double priceScore   = priceDiffPct < 0.001 ? 0.40 : (priceDiffPct < 0.01 ? 0.25 : 0.10);

        double qtyDiffPct   = Math.abs(fill.quantity() - opposite.quantity()) / fill.quantity();
        double qtyScore     = qtyDiffPct < 0.001 ? 0.30 : (qtyDiffPct < 0.10 ? 0.15 : 0.05);

        long secondsDiff = Math.abs(fill.fillTime().getEpochSecond() - opposite.fillTime().getEpochSecond());
        double timeScore = secondsDiff < 60 ? 0.30 : (secondsDiff < 300 ? 0.15 : 0.05);

        double confidence = Math.min(priceScore + qtyScore + timeScore, 1.0);

        String buyFillId  = "BUY".equals(fill.side()) ? fill.fillId() : opposite.fillId();
        String sellFillId = "SELL".equals(fill.side()) ? fill.fillId() : opposite.fillId();

        return new WashTradeAlert(
            UUID.randomUUID().toString(),
            fill.clientId(),
            fill.instrumentId(),
            buyFillId,
            sellFillId,
            confidence,
            "OPEN",
            Instant.now()
        );
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private List<RecentFill> loadOppositeRecentFills(OrderFilled fill, int windowMins) throws SQLException {
        String oppositeSide = "BUY".equals(fill.side()) ? "SELL" : "BUY";
        String sql = """
            SELECT fill_id, side, quantity, fill_price, fill_time
            FROM order_fills
            WHERE client_id = ?
              AND instrument_id = ?
              AND side = ?
              AND fill_time >= ? - INTERVAL '1 minute' * ?
              AND fill_id != ?
            ORDER BY fill_time DESC
            LIMIT 20
            """;
        List<RecentFill> result = new java.util.ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fill.clientId());
            ps.setString(2, fill.instrumentId());
            ps.setString(3, oppositeSide);
            ps.setObject(4, fill.fillTime());
            ps.setInt(5, windowMins);
            ps.setString(6, fill.fillId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RecentFill(
                        rs.getString("fill_id"),
                        rs.getString("side"),
                        rs.getDouble("quantity"),
                        rs.getDouble("fill_price"),
                        rs.getObject("fill_time", Instant.class)
                    ));
                }
            }
        }
        return result;
    }

    private void persistAlert(WashTradeAlert alert) throws SQLException {
        String sql = """
            INSERT INTO wash_trade_alerts (
                alert_id, client_id, instrument_id, buy_fill_id, sell_fill_id,
                confidence_score, alert_status, detected_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (buy_fill_id, sell_fill_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alert.alertId());
            ps.setString(2, alert.clientId());
            ps.setString(3, alert.instrumentId());
            ps.setString(4, alert.buyFillId());
            ps.setString(5, alert.sellFillId());
            ps.setDouble(6, alert.confidenceScore());
            ps.setString(7, alert.alertStatus());
            ps.setObject(8, alert.detectedAt());
            ps.executeUpdate();
        }
    }
}
