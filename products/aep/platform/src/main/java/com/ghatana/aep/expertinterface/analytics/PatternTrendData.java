package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents trend data for a specific pattern.
 *
 * <p>PatternTrendData captures the pattern identifier and its current
 * metric value for trend analysis and visualization.
 *
 * @doc.type class
 * @doc.purpose Captures pattern-specific trend data with current values
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class PatternTrendData {
    private String patternId;
    private double currentValue;
    
    public String getPatternId() { return patternId; }
    public double getCurrentValue() { return currentValue; }
}
