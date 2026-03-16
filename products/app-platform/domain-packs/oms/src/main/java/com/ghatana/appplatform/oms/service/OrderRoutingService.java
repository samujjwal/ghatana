package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Order routing service that forwards approved OMS orders to D-02 EMS for execution.
 *                Creates a routing record, transitions order state to ROUTED, and handles
 *                EMS rejection by marking the order as REJECTED.
 * @doc.layer     Application
 * @doc.pattern   Outbound adapter invocation with idempotency guard
 *
 * Story: D01-013
 */
public class OrderRoutingService {

    private static final Logger log = LoggerFactory.getLogger(OrderRoutingService.class);

    private final EmsPort       emsPort;
    private final DataSource    dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter ordersRouted;
    private final Counter emsRejections;

    public OrderRoutingService(EmsPort emsPort, DataSource dataSource,
                                Consumer<Object> eventPublisher, MeterRegistry meterRegistry) {
        this.emsPort        = emsPort;
        this.dataSource     = dataSource;
        this.eventPublisher = eventPublisher;
        this.ordersRouted   = meterRegistry.counter("oms.orders.routed");
        this.emsRejections  = meterRegistry.counter("oms.ems.rejections");
    }

    /**
     * Routes an approved order to EMS. Creates a routing record and emits OrderRoutedEvent.
     *
     * @param orderId       OMS order ID
     * @param clientId      client identifier
     * @param instrumentId  instrument to trade
     * @param exchange      target exchange (from instrument reference data)
     * @param side          "BUY" or "SELL"
     * @param quantity      order quantity
     * @param limitPrice    limit price (null = market)
     * @param orderType     "LIMIT" or "MARKET"
     * @param timeInForce   "DAY", "GTC", "IOC"
     * @return routingId assigned by EMS
     */
    public String routeToEms(String orderId, String clientId, String instrumentId,
                               String exchange, String side, long quantity,
                               BigDecimal limitPrice, String orderType, String timeInForce) {

        // Idempotency: check if already routed
        Optional<String> existing = findExistingRouting(orderId);
        if (existing.isPresent()) {
            log.info("OrderRouting: idempotent return for orderId={} routingId={}", orderId, existing.get());
            return existing.get();
        }

        String routingId;
        try {
            routingId = emsPort.route(orderId, clientId, instrumentId, exchange,
                    side, quantity, limitPrice, orderType, timeInForce);
        } catch (EmsPort.EmsRejectException e) {
            emsRejections.increment();
            log.warn("EMS rejected orderId={} reason={}", orderId, e.getMessage());
            updateOrderStatus(orderId, "REJECTED", e.getMessage());
            eventPublisher.accept(new OrderEmsRejectedEvent(orderId, clientId, e.getMessage()));
            return null;
        }

        saveRouting(orderId, routingId, exchange);
        updateOrderStatus(orderId, "ROUTED", null);
        ordersRouted.increment();

        log.info("OrderRouting: routed orderId={} routingId={} exchange={}", orderId, routingId, exchange);
        eventPublisher.accept(new OrderRoutedEvent(orderId, clientId, routingId, exchange));
        return routingId;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Optional<String> findExistingRouting(String orderId) {
        String sql = "SELECT routing_id FROM order_routings WHERE order_id = ? LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString("routing_id"));
            }
        } catch (SQLException e) {
            log.error("DB error checking existing routing for orderId={}", orderId, e);
        }
        return Optional.empty();
    }

    private void saveRouting(String orderId, String routingId, String exchange) {
        String sql = "INSERT INTO order_routings(routing_id, order_id, exchange, routed_at, status) "
                   + "VALUES(?,?,?,?,?) ON CONFLICT(routing_id) DO NOTHING";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, routingId);
            ps.setString(2, orderId);
            ps.setString(3, exchange);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setString(5, "ROUTED");
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("DB error saving routing record orderId={}", orderId, e);
        }
    }

    private void updateOrderStatus(String orderId, String status, String reason) {
        String sql = "UPDATE orders SET status = ?, rejection_reason = ?, updated_at = ? WHERE order_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reason);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setString(4, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("DB error updating order status orderId={}", orderId, e);
        }
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface EmsPort {
        String route(String orderId, String clientId, String instrumentId, String exchange,
                     String side, long quantity, BigDecimal limitPrice,
                     String orderType, String timeInForce);

        class EmsRejectException extends RuntimeException {
            public EmsRejectException(String reason) { super(reason); }
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OrderRoutedEvent(String orderId, String clientId, String routingId, String exchange) {}
    public record OrderEmsRejectedEvent(String orderId, String clientId, String reason) {}
}
