package com.ghatana.appplatform.ems.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @doc.type      Record
 * @doc.purpose   Represents a parent order split into child orders and their aggregate status.
 * @doc.layer     Domain
 * @doc.pattern   Immutable Value Object
 */
public record SplitOrder(
        String parentOrderId,
        String clientId,
        String instrumentId,
        ExecutionSide side,
        long totalQuantity,
        BigDecimal limitPrice,
        List<String> childRoutingIds,
        long totalFilledQuantity,
        BigDecimal avgFillPrice,
        ExecutionStatus aggregateStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public long remainingQuantity() {
        return totalQuantity - totalFilledQuantity;
    }
}
