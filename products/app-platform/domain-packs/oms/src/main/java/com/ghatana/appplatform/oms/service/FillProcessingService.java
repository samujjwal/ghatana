package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Processes execution fills received from D-02 EMS. Handles full and partial fills,
 *                accumulates filled quantities, computes weighted average fill price, and updates
 *                order state (PARTIALLY_FILLED → FILLED).
 * @doc.layer     Application
 * @doc.pattern   Event Consumer (inbound from EMS)
 *
 * Idempotency: fills are de-duplicated by execId (unique index in DB).
 *
 * Story: D01-014
 */
public class FillProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FillProcessingService.class);

    private final DataSource    dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter fullFills;
    private final Counter partialFills;

    public FillProcessingService(DataSource dataSource, Consumer<Object> eventPublisher,
                                  MeterRegistry meterRegistry) {
        this.dataSource     = dataSource;
        this.eventPublisher = eventPublisher;
        this.fullFills      = meterRegistry.counter("oms.fills.full");
        this.partialFills   = meterRegistry.counter("oms.fills.partial");
    }

    /**
     * Processes an execution fill from EMS.
     *
     * @param orderId      OMS parent order ID
     * @param routingId    EMS routing ID
     * @param execId       exchange execution ID (for idempotency)
     * @param filledQty    quantity filled in this execution
     * @param fillPrice    fill price
     * @param exchange     exchange that produced the fill
     */
    public void processFill(String orderId, String routingId, String execId,
                             long filledQty, BigDecimal fillPrice, String exchange) {

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Idempotency check — ignore duplicate execId
                if (fillExists(c, execId)) {
                    log.debug("FillProcessing: duplicate fill ignored execId={}", execId);
                    c.rollback();
                    return;
                }

                // Load current order
                OrderFillState current = loadOrderFillState(c, orderId);
                if (current == null) {
                    log.warn("FillProcessing: order not found orderId={}", orderId);
                    c.rollback();
                    return;
                }

                // Compute new aggregates
                long newFilled = current.filledQuantity() + filledQty;
                BigDecimal newAvgPrice = computeWeightedAvg(
                        current.avgFillPrice(), current.filledQuantity(),
                        fillPrice, filledQty);
                String newStatus = newFilled >= current.totalQuantity() ? "FILLED" : "PARTIALLY_FILLED";

                // Save fill record
                saveFill(c, execId, orderId, routingId, filledQty, fillPrice, exchange);

                // Update order
                updateOrderFill(c, orderId, newFilled, newAvgPrice, newStatus);

                c.commit();

                if ("FILLED".equals(newStatus)) {
                    fullFills.increment();
                    log.info("FillProcessing: order FULLY FILLED orderId={} avgPrice={}", orderId, newAvgPrice);
                    eventPublisher.accept(new OrderFilledEvent(orderId, newFilled, newAvgPrice));
                } else {
                    partialFills.increment();
                    log.info("FillProcessing: partial fill orderId={} filled={}/{} avgPrice={}",
                            orderId, newFilled, current.totalQuantity(), newAvgPrice);
                    eventPublisher.accept(new OrderPartiallyFilledEvent(
                            orderId, filledQty, newFilled, current.totalQuantity(), fillPrice, newAvgPrice));
                }

            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("FillProcessing: DB error for orderId={} execId={}", orderId, execId, e);
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private boolean fillExists(Connection c, String execId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM order_fills WHERE exec_id = ?")) {
            ps.setString(1, execId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private OrderFillState loadOrderFillState(Connection c, String orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT order_id, total_quantity, filled_quantity, avg_fill_price FROM orders WHERE order_id = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new OrderFillState(
                        rs.getString("order_id"),
                        rs.getLong("total_quantity"),
                        rs.getLong("filled_quantity"),
                        rs.getBigDecimal("avg_fill_price") != null
                                ? rs.getBigDecimal("avg_fill_price") : BigDecimal.ZERO);
            }
        }
    }

    private void saveFill(Connection c, String execId, String orderId, String routingId,
                           long qty, BigDecimal price, String exchange) throws SQLException {
        String sql = "INSERT INTO order_fills(fill_id, exec_id, order_id, routing_id, "
                   + "filled_quantity, fill_price, exchange, filled_at) VALUES(?,?,?,?,?,?,?,?) "
                   + "ON CONFLICT(exec_id) DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, execId);
            ps.setString(3, orderId);
            ps.setString(4, routingId);
            ps.setLong(5, qty);
            ps.setBigDecimal(6, price);
            ps.setString(7, exchange);
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private void updateOrderFill(Connection c, String orderId, long filled,
                                  BigDecimal avgPrice, String status) throws SQLException {
        String sql = "UPDATE orders SET filled_quantity = ?, avg_fill_price = ?, status = ?, "
                   + "updated_at = ? WHERE order_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, filled);
            ps.setBigDecimal(2, avgPrice);
            ps.setString(3, status);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setString(5, orderId);
            ps.executeUpdate();
        }
    }

    private BigDecimal computeWeightedAvg(BigDecimal prevAvg, long prevQty,
                                           BigDecimal newPrice, long newQty) {
        if (prevQty == 0) return newPrice;
        BigDecimal prevTotal = prevAvg.multiply(BigDecimal.valueOf(prevQty));
        BigDecimal newTotal  = newPrice.multiply(BigDecimal.valueOf(newQty));
        return prevTotal.add(newTotal).divide(
                BigDecimal.valueOf(prevQty + newQty), 6, RoundingMode.HALF_EVEN);
    }

    // ─── Inner record ─────────────────────────────────────────────────────────

    private record OrderFillState(String orderId, long totalQuantity,
                                   long filledQuantity, BigDecimal avgFillPrice) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OrderFilledEvent(String orderId, long filledQuantity, BigDecimal avgFillPrice) {}
    public record OrderPartiallyFilledEvent(String orderId, long thisFillQty, long cumulativeFilled,
                                            long totalQuantity, BigDecimal fillPrice, BigDecimal avgFillPrice) {}
}
