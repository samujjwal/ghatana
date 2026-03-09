package com.ghatana.aep.expertinterface.analytics;

import java.time.Instant;

/**
 * Represents a timestamped data point for pattern analytics.
 *
 * <p>PatternDataPoint captures a single measurement with pattern ID,
 * value, and timestamp for time-series analysis and visualization.
 *
 * @doc.type class
 * @doc.purpose Represents a timestamped metric data point for pattern analytics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class PatternDataPoint {
    private String patternId;
    private double value;
    private Instant timestamp;
    
    public String getPatternId() { return patternId; }
    public double getValue() { return value; }
    public Instant getTimestamp() { return timestamp; }
}
