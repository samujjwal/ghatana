package com.ghatana.aep.expertinterface.analytics.kpi;

import java.util.List;

/**
 * Represents comprehensive KPI trend data with slope and direction analysis.
 *
 * <p>This class aggregates trend data points for a specific KPI, calculating
 * the slope and direction to provide actionable trend insights.
 *
 * @doc.type class
 * @doc.purpose Aggregates KPI trend data with slope and direction analysis
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPITrend {
    private final String kpiName;
    private final List<KPITrendPoint> dataPoints;
    private final double slope;
    private final String direction;
    
    public KPITrend() {
        this("", List.of(), 0.0, "STABLE");
    }
    
    public KPITrend(String kpiName, List<KPITrendPoint> dataPoints, double slope, String direction) {
        this.kpiName = kpiName;
        this.dataPoints = dataPoints;
        this.slope = slope;
        this.direction = direction;
    }
    
    public String getKpiName() { return kpiName; }
    public List<KPITrendPoint> getDataPoints() { return dataPoints; }
    public double getSlope() { return slope; }
    public String getDirection() { return direction; }
}
