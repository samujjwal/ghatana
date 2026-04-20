package com.ghatana.finance.kernel.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Position record DTO with strong typing
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class PositionRecord {
    private final String traderId;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal averageCost;
    private final BigDecimal currentPrice;
    private final BigDecimal unrealizedPnL;
    private final Instant lastUpdated;

    public PositionRecord(String traderId, String symbol, BigDecimal quantity, BigDecimal averageCost,
                         BigDecimal currentPrice, BigDecimal unrealizedPnL, Instant lastUpdated) {
        this.traderId = Objects.requireNonNull(traderId, "traderId cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "quantity cannot be null");
        this.averageCost = Objects.requireNonNull(averageCost, "averageCost cannot be null");
        this.currentPrice = Objects.requireNonNull(currentPrice, "currentPrice cannot be null");
        this.unrealizedPnL = Objects.requireNonNull(unrealizedPnL, "unrealizedPnL cannot be null");
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
    }

    public String getTraderId() { return traderId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAverageCost() { return averageCost; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public Instant getLastUpdated() { return lastUpdated; }

    public BigDecimal getValue() {
        return quantity.multiply(currentPrice);
    }

    @Override
    public String toString() {
        return "PositionRecord{" + "traderId='" + traderId + '\'' + ", symbol='" + symbol + '\'' +
               ", quantity=" + quantity + ", value=" + getValue() + '}';
    }
}
