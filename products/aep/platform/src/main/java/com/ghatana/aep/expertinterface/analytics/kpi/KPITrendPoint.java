package com.ghatana.aep.expertinterface.analytics.kpi;

import java.time.Instant;

/**
 * Represents a single point in a KPI trend time series.
 *
 * <p>This immutable class combines a timestamp with a numeric value
 * to form individual data points for KPI trend analysis.
 *
 * @doc.type class
 * @doc.purpose Represents a single timestamped point in KPI trend data
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPITrendPoint {
    private final Instant timestamp;
    private final double value;
    
    public KPITrendPoint(Instant timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
    
    public Instant getTimestamp() { return timestamp; }
    public double getValue() { return value; }
}
