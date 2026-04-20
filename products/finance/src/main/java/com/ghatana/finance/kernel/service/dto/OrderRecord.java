package com.ghatana.finance.kernel.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Order record DTO with strong typing
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class OrderRecord {
    private final String orderId;
    private final String traderId;
    private final String symbol;
    private final String side; // BUY or SELL
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final String status; // NEW, PENDING, FILLED, CANCELLED, etc.
    private final Instant createdAt;
    private final Instant lastUpdated;

    public OrderRecord(String orderId, String traderId, String symbol, String side,
                      BigDecimal quantity, BigDecimal price, String status,
                      Instant createdAt, Instant lastUpdated) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.traderId = Objects.requireNonNull(traderId, "traderId cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.side = Objects.requireNonNull(side, "side cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "quantity cannot be null");
        this.price = Objects.requireNonNull(price, "price cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
    }

    public String getOrderId() { return orderId; }
    public String getTraderId() { return traderId; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUpdated() { return lastUpdated; }

    public BigDecimal getExposure() {
        return quantity.multiply(price);
    }

    public boolean isOpen() {
        return "NEW".equals(status) || "PENDING".equals(status);
    }

    @Override
    public String toString() {
        return "OrderRecord{" + "orderId='" + orderId + '\'' + ", traderId='" + traderId + '\'' +
               ", symbol='" + symbol + '\'' + ", side='" + side + '\'' + ", quantity=" + quantity +
               ", status='" + status + '\'' + '}';
    }
}
