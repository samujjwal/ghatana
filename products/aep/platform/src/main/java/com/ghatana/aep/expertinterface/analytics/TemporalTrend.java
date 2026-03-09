package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents temporal trend data with direction indicators.
 *
 * <p>TemporalTrend captures time-based trend information including
 * direction (increasing, decreasing, stable) for pattern metrics.
 *
 * @doc.type class
 * @doc.purpose Captures temporal trend direction for time-series analytics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TemporalTrend {
    private String direction;
    public String getDirection() { return direction; }
}
