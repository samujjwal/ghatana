package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents an alert generated when pattern trends exceed thresholds.
 *
 * <p>TrendAlert captures alerts triggered by significant changes in pattern
 * performance trends, enabling proactive monitoring and expert notification.
 *
 * @doc.type class
 * @doc.purpose Represents trend-based alerts for pattern performance anomalies
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TrendAlert {
    private String alertType;
    public String getAlertType() { return alertType; }
}
