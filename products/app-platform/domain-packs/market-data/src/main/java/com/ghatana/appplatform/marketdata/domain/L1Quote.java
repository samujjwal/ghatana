package com.ghatana.appplatform.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @doc.type       Domain Entity (immutable record)
 * @doc.purpose    Level-1 top-of-book quote for a single instrument.
 *                 Maintained in-memory (ConcurrentHashMap) and in Redis.
 *                 Published to the Kafka topic {@code siddhanta.marketdata.l1}
 *                 on every update.
 *                 D04-004: L1 quote.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Record
 */
public record L1Quote(
        String instrumentId,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal lastPrice,
        long volume,
        Instant updatedAt
) {}
