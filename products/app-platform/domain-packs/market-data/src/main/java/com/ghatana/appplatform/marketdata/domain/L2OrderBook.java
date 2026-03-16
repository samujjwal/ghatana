package com.ghatana.appplatform.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @doc.type    Domain Object (Record)
 * @doc.purpose Immutable snapshot of an instrument's Level 2 (depth-of-book) order book (D04-005).
 *              Holds the top-N price levels (configurable, default 10) on each side.
 *              Updated in-memory on each book-change event and published to
 *              {@code siddhanta.marketdata.l2}.
 * @doc.layer   Domain
 * @doc.pattern Value Object (immutable snapshot)
 *
 * @param instrumentId  Exchange instrument symbol.
 * @param bids          Top-N bid levels, sorted descending by price (best bid first).
 * @param asks          Top-N ask levels, sorted ascending by price (best ask first).
 * @param sequence      Monotonically increasing sequence number for ordering updates.
 * @param updatedAt     Wall-clock timestamp when this snapshot was captured.
 */
public record L2OrderBook(
        String instrumentId,
        List<PriceLevel> bids,
        List<PriceLevel> asks,
        long sequence,
        Instant updatedAt
) {
    /** Best bid price, or null when no bids exist. */
    public BigDecimal bestBid() {
        return bids.isEmpty() ? null : bids.get(0).price();
    }

    /** Best ask price, or null when no asks exist. */
    public BigDecimal bestAsk() {
        return asks.isEmpty() ? null : asks.get(0).price();
    }

    /** Mid-point price, or null when either side is empty. */
    public BigDecimal midPrice() {
        BigDecimal bid = bestBid();
        BigDecimal ask = bestAsk();
        if (bid == null || ask == null) return null;
        return bid.add(ask).divide(BigDecimal.TWO, 4, java.math.RoundingMode.HALF_EVEN);
    }

    /**
     * A single aggregated price level in the order book.
     *
     * @param price         Price for this level.
     * @param totalQuantity Sum of all order quantities at this price.
     * @param orderCount    Number of individual orders constituting this level.
     */
    public record PriceLevel(BigDecimal price, long totalQuantity, int orderCount) {}
}
