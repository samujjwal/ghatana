package com.ghatana.aep.expertinterface.analytics.kpi;

/**
 * Container for a complete KPI measurement with metadata.
 *
 * <p>This immutable class holds a KPI's name, numeric value, unit of measurement,
 * and current trend direction for comprehensive KPI reporting.
 *
 * @doc.type class
 * @doc.purpose Contains complete KPI measurement with name, value, unit, and trend
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPIValue {
    private final String name;
    private final double value;
    private final String unit;
    private final String trend;
    
    public KPIValue(String name, double value, String unit, String trend) {
        this.name = name;
        this.value = value;
        this.unit = unit;
        this.trend = trend;
    }
    
    public String getName() { return name; }
    public double getValue() { return value; }
    public String getUnit() { return unit; }
    public String getTrend() { return trend; }
}
