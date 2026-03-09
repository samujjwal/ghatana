package com.ghatana.aep.expertinterface.analytics;

import java.util.Map;

/**
 * Container for Key Performance Indicator (KPI) data.
 *
 * <p>This class holds a map of KPI metric names to their numeric values,
 * providing structured access to analytics measurements.
 *
 * @doc.type class
 * @doc.purpose Stores KPI metric data as name-value pairs for analytics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPIData {
    private Map<String, Double> data;
    public Map<String, Double> getData() { return data; }
}
