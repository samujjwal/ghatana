package com.ghatana.appplatform.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @doc.type       Domain Entity (immutable record)
 * @doc.purpose    A single market data event (trade or quote update) for one
 *                 instrument.  The tick is the atomic write unit ingested from
 *                 feed adapters and persisted to the TimescaleDB hypertable.
 *                 anomalyFlag is set by TickValidationService when a price or
 *                 volume value falls outside acceptable bounds, so the tick is
 *                 still stored but marked for review.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Record
 */
public record MarketTick(
        /** UUID of the instrument — refers to instrument_master.id (D11). */
        String instrumentId,

        /** UTC wall-clock timestamp of the tick (hypertable partition key). */
        Instant timestampUtc,

        /**
         * Calendar date in the local (Nepal) timezone — used for daily aggregation.
         * May be null for ticks arriving close to midnight before TZ resolution.
         */
        LocalDate calendarDate,

        /** Best bid price at the time of the tick. */
        BigDecimal bid,

        /** Best ask price at the time of the tick. */
        BigDecimal ask,

        /** Last trade price. */
        BigDecimal last,

        /** Cumulative volume traded during the session so far. */
        long volume,

        /** Session open price. */
        BigDecimal open,

        /** Session high price. */
        BigDecimal high,

        /** Session low price. */
        BigDecimal low,

        /** Session close / previous close for reference. */
        BigDecimal close,

        /** Source of this tick. */
        TickSource source,

        /** Monotonically increasing sequence number from the originating feed. */
        long sequence,

        /**
         * True when TickValidationService detected an anomaly (price ±20% of
         * last close, future timestamp, etc.).  Stored but excluded from
         * real-time L1 distribution.
         */
        boolean anomalyFlag
) {}
