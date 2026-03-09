package com.ghatana.aep.expertinterface.analytics;

import java.time.Instant;
import java.util.Map;

/**
 * Metric data container.
 * 
 * @doc.type class
 * @doc.purpose Metric data model
 * @doc.layer analytics
 */
public class MetricData {
    private final String name;
    private final MetricType type;
    private final double value;
    private final Map<String, String> tags;
    private final Instant timestamp;
    private final String unit;
    
    public MetricData(String name, MetricType type, double value, 
                     Map<String, String> tags, Instant timestamp, String unit) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.tags = tags;
        this.timestamp = timestamp;
        this.unit = unit;
    }
    
    public String getName() { return name; }
    public MetricType getType() { return type; }
    public double getValue() { return value; }
    public Map<String, String> getTags() { return tags; }
    public Instant getTimestamp() { return timestamp; }
    public String getUnit() { return unit; }
    
    /**
     * Metric type enumeration.
     */
    public enum MetricType {
        COUNTER, TIMER, GAUGE, DISTRIBUTION_SUMMARY
    }
}
