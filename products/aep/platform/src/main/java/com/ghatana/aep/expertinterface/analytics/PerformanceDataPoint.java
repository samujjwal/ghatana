package com.ghatana.aep.expertinterface.analytics;

import java.time.Instant;

/**
 * Performance data point for time series analysis.
 * 
 * @doc.type class
 * @doc.purpose Performance data point
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class PerformanceDataPoint {
    private double value;
    private Instant timestamp;
    
    public PerformanceDataPoint() {
    }
    
    public PerformanceDataPoint(double value, Instant timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }
    
    public double getValue() { 
        return value; 
    }
    
    public void setValue(double value) {
        this.value = value;
    }
    
    public Instant getTimestamp() { 
        return timestamp; 
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
