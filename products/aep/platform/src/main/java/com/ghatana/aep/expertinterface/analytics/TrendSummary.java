package com.ghatana.aep.expertinterface.analytics;

/**
 * Summarizes trend analysis results for analytics reporting.
 *
 * <p>This class encapsulates a textual summary of trend data, providing
 * a human-readable description of trend patterns detected in analytics.
 *
 * @doc.type class
 * @doc.purpose Encapsulates trend analysis summary for expert interface analytics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TrendSummary {
    private String summary;
    public String getSummary() { return summary; }
}
