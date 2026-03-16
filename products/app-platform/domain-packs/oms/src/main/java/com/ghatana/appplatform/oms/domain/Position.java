package com.ghatana.appplatform.oms.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose CQRS read-side position record updated via FillReceived events (D01-016).
 * @doc.layer   Domain
 * @doc.pattern CQRS Read Model
 */
public record Position(
        String clientId,
        String instrumentId,
        String accountId,
        BigDecimal quantity,        // net quantity held
        BigDecimal avgCost,         // weighted average cost basis
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        Instant updatedAt
) {
    public Position withFill(BigDecimal fillQty, BigDecimal fillPrice, OrderSide side) {
        BigDecimal newQty;
        BigDecimal newAvgCost;
        BigDecimal newRealizedPnl = realizedPnl;

        if (side == OrderSide.BUY) {
            // Weighted average cost on buys
            var totalCost = avgCost.multiply(quantity).add(fillPrice.multiply(fillQty));
            newQty = quantity.add(fillQty);
            newAvgCost = newQty.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : totalCost.divide(newQty, 8, java.math.RoundingMode.HALF_EVEN);
        } else {
            // Realize P&L on sells
            var pnl = fillPrice.subtract(avgCost).multiply(fillQty);
            newRealizedPnl = realizedPnl.add(pnl);
            newQty = quantity.subtract(fillQty);
            newAvgCost = newQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : avgCost;
        }

        return new Position(clientId, instrumentId, accountId, newQty, newAvgCost,
                unrealizedPnl, newRealizedPnl, Instant.now());
    }
}
