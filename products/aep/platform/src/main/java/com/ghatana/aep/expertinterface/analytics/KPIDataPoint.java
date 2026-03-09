package com.ghatana.aep.expertinterface.analytics;

import java.time.Instant;

/**
 * Represents a single KPI measurement at a specific point in time.
 *
 * <p>This class combines a KPI value with its timestamp for time-series
 * analytics and trend tracking of key performance indicators.
 *
 * @doc.type class
 * @doc.purpose Represents a timestamped KPI measurement for time-series analytics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPIDataPoint {
    private double value;
    private Instant timestamp;
    public double getValue() { return value; }
    public Instant getTimestamp() { return timestamp; }
}
