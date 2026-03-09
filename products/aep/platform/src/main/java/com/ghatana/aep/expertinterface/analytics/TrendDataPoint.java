package com.ghatana.aep.expertinterface.analytics;

import java.time.Instant;

/**
 * Represents a single data point in a trend time series.
 *
 * <p>This class combines a numeric value with its timestamp to form
 * individual points in trend analysis data sets.
 *
 * @doc.type class
 * @doc.purpose Represents a timestamped value in trend analysis
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TrendDataPoint {
    private double value;
    private Instant timestamp;
    public double getValue() { return value; }
    public Instant getTimestamp() { return timestamp; }
}
