package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents performance trend data for pattern analytics.
 *
 * <p>PerformanceTrend captures performance metrics over time to identify
 * patterns in system behavior and pattern effectiveness.
 *
 * @doc.type class
 * @doc.purpose Captures performance trend metrics for pattern analysis
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class PerformanceTrend {
    private double performance;
    public double getPerformance() { return performance; }
}
