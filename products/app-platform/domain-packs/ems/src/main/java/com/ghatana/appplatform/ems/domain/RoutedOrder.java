package com.ghatana.appplatform.ems.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @doc.type      Record
 * @doc.purpose   Represents an order routed through the EMS to an exchange venue.
 * @doc.layer     Domain
 * @doc.pattern   Immutable Value Object
 */
public record RoutedOrder(
        String routingId,
        String parentOrderId,
        String clientId,
        String instrumentId,
        String exchange,
        ExecutionSide side,
        long quantity,
        BigDecimal limitPrice,
        String orderType,
        String timeInForce,
        ExecutionStatus status,
        long filledQuantity,
        BigDecimal avgFillPrice,
        String externalOrderId,
        Instant routedAt,
        Instant updatedAt,
        List<ExecutionFill> fills
) {
    public boolean isFullyFilled() {
        return filledQuantity >= quantity;
    }

    public long remainingQuantity() {
        return quantity - filledQuantity;
    }
}
