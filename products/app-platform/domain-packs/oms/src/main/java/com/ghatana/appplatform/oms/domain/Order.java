package com.ghatana.appplatform.oms.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @doc.type    Record (Immutable Aggregate Root)
 * @doc.purpose Core order aggregate following the 9-state lifecycle (D01-001, D01-004).
 * @doc.layer   Domain
 * @doc.pattern Aggregate Root, Event-Sourced Aggregate
 *
 * <p>Orders are immutable — all mutations return a new Order instance. Dual-calendar
 * timestamps (Gregorian + BS) are assigned at capture time via K-15.
 */
public record Order(
        String orderId,
        String clientId,
        String accountId,
        String instrumentId,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal price,           // null for MARKET orders
        BigDecimal stopPrice,       // null unless STOP or STOP_LIMIT
        OrderStatus status,
        String idempotencyKey,

        // Enriched fields
        String instrumentSymbol,
        String exchange,
        String currency,
        BigDecimal orderValue,      // quantity × price (enriched)
        BigDecimal arrivalPrice,    // market price at order receipt (for TCA)

        // Fill tracking
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal avgFillPrice,
        List<Fill> fills,

        // Timing
        Instant createdAt,
        String createdAtBs,         // Bikram Sambat date from K-15
        Instant updatedAt,

        // Trail
        String rejectionReason,
        String routingId
) {
    /** Orders start life as PENDING (D01-001: captured and validated). */
    public static Order newOrder(String orderId, String clientId, String accountId,
                                  String instrumentId, OrderSide side, OrderType orderType,
                                  TimeInForce timeInForce, BigDecimal quantity,
                                  BigDecimal price, BigDecimal stopPrice,
                                  String idempotencyKey, Instant now, String nowBs) {
        return new Order(orderId, clientId, accountId, instrumentId, side, orderType,
                timeInForce, quantity, price, stopPrice,
                OrderStatus.PENDING, idempotencyKey,
                null, null, null, null, null,
                BigDecimal.ZERO, quantity, null, List.of(),
                now, nowBs, now,
                null, null);
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(orderId, clientId, accountId, instrumentId, side, orderType,
                timeInForce, quantity, price, stopPrice,
                newStatus, idempotencyKey,
                instrumentSymbol, exchange, currency, orderValue, arrivalPrice,
                filledQuantity, remainingQuantity, avgFillPrice, fills,
                createdAt, createdAtBs, Instant.now(),
                rejectionReason, routingId);
    }

    public Order withEnrichment(String symbol, String exch, String curr,
                                 BigDecimal value, BigDecimal arrival) {
        return new Order(orderId, clientId, accountId, instrumentId, side, orderType,
                timeInForce, quantity, price, stopPrice,
                status, idempotencyKey,
                symbol, exch, curr, value, arrival,
                filledQuantity, remainingQuantity, avgFillPrice, fills,
                createdAt, createdAtBs, Instant.now(),
                rejectionReason, routingId);
    }

    public Order withRejection(String reason) {
        return new Order(orderId, clientId, accountId, instrumentId, side, orderType,
                timeInForce, quantity, price, stopPrice,
                OrderStatus.REJECTED, idempotencyKey,
                instrumentSymbol, exchange, currency, orderValue, arrivalPrice,
                filledQuantity, remainingQuantity, avgFillPrice, fills,
                createdAt, createdAtBs, Instant.now(),
                reason, routingId);
    }

    public Order withFill(Fill fill, BigDecimal newAvgPrice) {
        var newFills = new java.util.ArrayList<>(fills);
        newFills.add(fill);
        var newFilled = filledQuantity.add(fill.fillQuantity());
        var newRemaining = quantity.subtract(newFilled);
        var newStatus = newRemaining.compareTo(BigDecimal.ZERO) == 0
                ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        return new Order(orderId, clientId, accountId, instrumentId, side, orderType,
                timeInForce, quantity, price, stopPrice,
                newStatus, idempotencyKey,
                instrumentSymbol, exchange, currency, orderValue, arrivalPrice,
                newFilled, newRemaining, newAvgPrice, List.copyOf(newFills),
                createdAt, createdAtBs, Instant.now(),
                rejectionReason, routingId);
    }
}
