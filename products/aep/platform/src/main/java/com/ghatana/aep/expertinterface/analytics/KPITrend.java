package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents the trend direction of a Key Performance Indicator.
 *
 * <p>This class captures the directional movement (increasing, decreasing, stable)
 * of KPI values over time for trend analysis.
 *
 * @doc.type class
 * @doc.purpose Captures KPI trend direction for analytics tracking
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPITrend {
    private TrendDirection direction;
    public TrendDirection getDirection() { return direction; }
}
