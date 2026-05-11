/**
 * Metrics Standardization Service
 * 
 * Standardizes metric names and tags for observability.
 * Ensures metrics follow naming conventions and have consistent tags.
 * 
 * @doc.type interface
 * @doc.purpose Metrics standardization
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.observability;

import java.util.Map;

/**
 * Service interface for standardizing metrics.
 */
public interface MetricsStandardizationService {

    /**
     * Standardizes a metric name.
     * 
     * @param metricName The metric name to standardize
     * @return Standardized metric name
     */
    String standardizeMetricName(String metricName);

    /**
     * Standardizes metric tags.
     * 
     * @param tags The tags to standardize
     * @return Standardized tags
     */
    Map<String, String> standardizeTags(Map<String, String> tags);

    /**
     * Validates a metric name.
     * 
     * @param metricName The metric name to validate
     * @return true if valid, false otherwise
     */
    boolean isValidMetricName(String metricName);

    /**
     * Validates metric tags.
     * 
     * @param tags The tags to validate
     * @return MetricValidationResult containing validation status and any errors
     */
    MetricValidationResult validateTags(Map<String, String> tags);
}

/**
 * Metric validation result.
 */
record MetricValidationResult(
    boolean isValid,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public MetricValidationResult {
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}
