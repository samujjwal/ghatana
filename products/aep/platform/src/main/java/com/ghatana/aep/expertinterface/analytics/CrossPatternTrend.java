package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents cross-pattern trend analysis data.
 *
 * <p>CrossPatternTrend captures correlations and trends across multiple
 * patterns, enabling holistic analysis of pattern interactions.
 *
 * @doc.type class
 * @doc.purpose Captures cross-pattern correlation and trend data
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class CrossPatternTrend {
    private String trendType;
    public String getTrendType() { return trendType; }
}
