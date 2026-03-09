package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents the direction of a metric trend (upward or downward).
 * 
 * <p>This class provides type-safe constants for trend directions, used in analytics
 * to indicate whether a metric is increasing or decreasing over time.
 *
 * @doc.type class
 * @doc.purpose Defines trend direction constants for analytics visualization
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TrendDirection {
    public static final TrendDirection UP = new TrendDirection();
    public static final TrendDirection DOWN = new TrendDirection();
}
