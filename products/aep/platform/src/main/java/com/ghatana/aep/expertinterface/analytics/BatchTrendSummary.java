package com.ghatana.aep.expertinterface.analytics;

/**
 * Summarizes batch trend analysis results across multiple data sets.
 *
 * <p>This class provides aggregated trend summaries for batch processing
 * scenarios where multiple trend analyses are performed together.
 *
 * @doc.type class
 * @doc.purpose Encapsulates batch trend analysis summary for aggregated analytics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class BatchTrendSummary {
    private String summary;
    public String getSummary() { return summary; }
}
