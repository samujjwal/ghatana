package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Aggregates partial fills for an algorithm slice into a single weighted-average
 *              fill event (D02-019). Publishes FillAggregated event for downstream consumers
 *              (TCA, post-trade confirmation).
 * @doc.layer   Domain — EMS execution management
 * @doc.pattern Event-driven fill consolidation; immutable records; no duplicate fills
 */
public class FillAggregationService {

    public record PartialFill(
        String fillId,
        String orderId,
        String sliceId,
        long filledQuantity,
        BigDecimal fillPrice,
        Instant filledAt
    ) {}

    public record FillAggregated(
        String aggregateId,
        String orderId,
        long totalFilledQuantity,
        long totalOrderQuantity,
        long remainingQuantity,
        BigDecimal weightedAvgPrice,
        List<String> constituentFillIds,
        boolean isComplete,
        Instant aggregatedAt
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter aggregationsCounter;

    public FillAggregationService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.aggregationsCounter = Counter.builder("ems.fills.aggregations_total").register(registry);
    }

    /**
     * Aggregate all fills for an order into a weighted-average roll-up.
     * Called after each new fill arrives to provide live progress.
     */
    public Promise<FillAggregated> aggregateFills(String orderId) {
        return Promise.ofBlocking(executor, () -> {
            List<PartialFill> fills = loadFills(orderId);
            if (fills.isEmpty()) throw new IllegalStateException("No fills found for order: " + orderId);

            long totalFilled = fills.stream().mapToLong(PartialFill::filledQuantity).sum();
            BigDecimal wavg = computeWeightedAvg(fills);
            long totalQty = loadOrderQuantity(orderId);
            long remaining = Math.max(0L, totalQty - totalFilled);
            boolean complete = remaining == 0;
            List<String> fillIds = fills.stream().map(PartialFill::fillId).toList();

            FillAggregated agg = new FillAggregated(UUID.randomUUID().toString(), orderId,
                totalFilled, totalQty, remaining, wavg, fillIds, complete, Instant.now());
            persistAggregate(agg);
            aggregationsCounter.increment();
            return agg;
        });
    }

    private BigDecimal computeWeightedAvg(List<PartialFill> fills) {
        BigDecimal totalValue = BigDecimal.ZERO;
        long totalQty = 0;
        for (PartialFill f : fills) {
            totalValue = totalValue.add(f.fillPrice().multiply(BigDecimal.valueOf(f.filledQuantity())));
            totalQty += f.filledQuantity();
        }
        if (totalQty == 0) return BigDecimal.ZERO;
        return totalValue.divide(BigDecimal.valueOf(totalQty), 4, RoundingMode.HALF_UP);
    }

    private List<PartialFill> loadFills(String orderId) throws Exception {
        List<PartialFill> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, order_id, slice_id, filled_quantity, fill_price, filled_at " +
                 "FROM order_fills WHERE order_id = ? ORDER BY filled_at ASC")) {
            ps.setObject(1, UUID.fromString(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PartialFill(rs.getString("id"), orderId,
                        rs.getString("slice_id"), rs.getLong("filled_quantity"),
                        rs.getBigDecimal("fill_price"),
                        rs.getTimestamp("filled_at").toInstant()));
                }
            }
        }
        return list;
    }

    private long loadOrderQuantity(String orderId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT quantity FROM orders WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("quantity");
                throw new IllegalStateException("Order not found: " + orderId);
            }
        }
    }

    private void persistAggregate(FillAggregated agg) throws Exception {
        String sql = "INSERT INTO fill_aggregates(id, order_id, total_filled_qty, total_order_qty, " +
                     "remaining_qty, weighted_avg_price, is_complete, aggregated_at) " +
                     "VALUES(?,?,?,?,?,?,?,NOW()) " +
                     "ON CONFLICT(order_id) DO UPDATE SET total_filled_qty=EXCLUDED.total_filled_qty, " +
                     "remaining_qty=EXCLUDED.remaining_qty, weighted_avg_price=EXCLUDED.weighted_avg_price, " +
                     "is_complete=EXCLUDED.is_complete, aggregated_at=NOW()";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(agg.aggregateId()));
            ps.setObject(2, UUID.fromString(agg.orderId()));
            ps.setLong(3, agg.totalFilledQuantity());
            ps.setLong(4, agg.totalOrderQuantity());
            ps.setLong(5, agg.remainingQuantity());
            ps.setBigDecimal(6, agg.weightedAvgPrice());
            ps.setBoolean(7, agg.isComplete());
            ps.executeUpdate();
        }
    }
}
