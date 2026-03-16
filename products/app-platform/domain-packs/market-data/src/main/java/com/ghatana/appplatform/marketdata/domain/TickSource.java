package com.ghatana.appplatform.marketdata.domain;

/**
 * @doc.type       Domain Value Object (enum)
 * @doc.purpose    Identifies the origin of a market tick.
 *                 Used by the feed adapter registry to apply source priority rules
 *                 and by the validation service to adjust anomaly thresholds.
 * @doc.layer      Domain
 * @doc.pattern    Value Object
 */
public enum TickSource {
    /** Primary market data feed (e.g. NEPSE live data). Highest priority. */
    PRIMARY,
    /** Secondary / backup feed used on primary failover. */
    SECONDARY,
    /** Manually entered tick for corrections or gap-fill. Lowest priority. */
    MANUAL
}
