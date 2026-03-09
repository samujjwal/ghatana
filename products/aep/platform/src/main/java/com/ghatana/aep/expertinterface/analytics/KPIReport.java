package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents a Key Performance Indicator (KPI) report generated from analytics data.
 * 
 * <p>This class encapsulates the results of KPI analysis, including success status
 * and relevant metrics for expert interface dashboards and reporting.
 *
 * @doc.type class
 * @doc.purpose Encapsulates KPI report data for analytics dashboards
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPIReport {
    private boolean success = true;
    public boolean isSuccess() { return success; }
}
