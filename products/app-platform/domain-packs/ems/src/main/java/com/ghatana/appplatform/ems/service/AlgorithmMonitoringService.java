package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Real-time progress monitoring for all active algorithm executions (D02-008).
 *              Tracks filled vs target quantity, slippage vs arrival price, and raises
 *              behind-schedule alerts when execution pace falls more than 15% behind target.
 * @doc.layer   Domain — EMS algorithm monitoring
 * @doc.pattern Polling-based monitoring; per-order metrics; slippage threshold alerts
 */
public class AlgorithmMonitoringService {

    private static final double BEHIND_SCHEDULE_THRESHOLD = 0.15;  // 15% behind target pace

    public record AlgoProgress(
        String orderId,
        String algoType,   // VWAP / TWAP / IS
        long totalQuantity,
        long filledQuantity,
        long remainingQuantity,
        double completionPct,
        double targetCompletionPct,        // expected based on elapsed time
        BigDecimal arrivalPrice,
        BigDecimal executedVwap,
        BigDecimal slippageBps,
        boolean isBehindSchedule,
        Instant lastUpdated
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter alertsCounter;
    private final AtomicInteger behindScheduleCount = new AtomicInteger(0);

    public AlgorithmMonitoringService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.alertsCounter = Counter.builder("ems.algo.behind_schedule_alerts_total").register(registry);
        Gauge.builder("ems.algo.behind_schedule_count", behindScheduleCount, AtomicInteger::get)
            .register(registry);
    }

    /** Get real-time progress for a single order. */
    public Promise<AlgoProgress> getProgress(String orderId) {
        return Promise.ofBlocking(executor, () -> computeProgress(orderId));
    }

    /** Scan all active algorithm orders and alert on any that are behind schedule. */
    public Promise<List<AlgoProgress>> scanActiveOrders() {
        return Promise.ofBlocking(executor, () -> {
            List<String> activeOrders = loadActiveAlgoOrderIds();
            List<AlgoProgress> results = new ArrayList<>();
            int behind = 0;
            for (String id : activeOrders) {
                AlgoProgress p = computeProgress(id);
                results.add(p);
                if (p.isBehindSchedule()) {
                    alertsCounter.increment();
                    behind++;
                }
            }
            behindScheduleCount.set(behind);
            return results;
        });
    }

    private AlgoProgress computeProgress(String orderId) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Load order fundamentals
            long totalQty, filledQty;
            BigDecimal arrivalPrice;
            String algoType;
            Instant orderCreatedAt;
            Instant estimatedEndAt;

            String orderSql = "SELECT o.quantity, o.algo_type, o.arrival_price, o.created_at, " +
                              "o.algo_estimated_end_at, COALESCE(fa.total_filled_qty, 0) AS filled " +
                              "FROM orders o LEFT JOIN fill_aggregates fa ON fa.order_id = o.id " +
                              "WHERE o.id = ?";
            try (PreparedStatement ps = c.prepareStatement(orderSql)) {
                ps.setObject(1, java.util.UUID.fromString(orderId));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Order not found: " + orderId);
                    totalQty      = rs.getLong("quantity");
                    filledQty     = rs.getLong("filled");
                    arrivalPrice  = rs.getBigDecimal("arrival_price");
                    algoType      = rs.getString("algo_type");
                    orderCreatedAt = rs.getTimestamp("created_at").toInstant();
                    estimatedEndAt = rs.getTimestamp("algo_estimated_end_at") != null
                        ? rs.getTimestamp("algo_estimated_end_at").toInstant()
                        : Instant.now().plusSeconds(3600);
                }
            }

            // Load current VWAP from fills
            BigDecimal execVwap = loadExecutedVwap(c, orderId);
            BigDecimal slippageBps = computeSlippageBps(arrivalPrice, execVwap);

            double completionPct = totalQty > 0 ? (double) filledQty / totalQty : 0.0;

            // Time-based target completion
            long totalWindowSecs = Math.max(1,
                estimatedEndAt.getEpochSecond() - orderCreatedAt.getEpochSecond());
            long elapsedSecs = Instant.now().getEpochSecond() - orderCreatedAt.getEpochSecond();
            double targetCompletionPct = Math.min(1.0, (double) elapsedSecs / totalWindowSecs);

            boolean isBehind = (targetCompletionPct - completionPct) > BEHIND_SCHEDULE_THRESHOLD;

            return new AlgoProgress(orderId, algoType, totalQty, filledQty,
                Math.max(0, totalQty - filledQty), completionPct, targetCompletionPct,
                arrivalPrice, execVwap, slippageBps, isBehind, Instant.now());
        }
    }

    private BigDecimal loadExecutedVwap(Connection c, String orderId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT weighted_avg_price FROM fill_aggregates WHERE order_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("weighted_avg_price") : BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal computeSlippageBps(BigDecimal arrival, BigDecimal execVwap) {
        if (arrival == null || arrival.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return execVwap.subtract(arrival).divide(arrival, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(10_000)).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> loadActiveAlgoOrderIds() throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id FROM orders WHERE algo_type IS NOT NULL AND status IN ('OPEN','PARTIALLY_FILLED')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("id"));
            }
        }
        return ids;
    }
}
