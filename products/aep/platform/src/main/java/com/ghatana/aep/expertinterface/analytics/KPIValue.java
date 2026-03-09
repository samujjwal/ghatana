package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents a key performance indicator (KPI) value.
 *
 * <p>KPIValue encapsulates a numeric KPI measurement used in
 * pattern analytics and dashboard displays.
 *
 * @doc.type class
 * @doc.purpose Encapsulates KPI metric values for analytics dashboards
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class KPIValue {
    private double value;
    public double getValue() { return value; }
}
