/**
 * Metrics Standardization Service Implementation
 * 
 * Production-grade implementation of metrics standardization service.
 * Standardizes metric names and tags for observability.
 * 
 * @doc.type class
 * @doc.purpose Metrics standardization implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Production-grade implementation of metrics standardization service.
 */
public final class MetricsStandardizationServiceImpl implements MetricsStandardizationService {

    private static final Logger log = LoggerFactory.getLogger(MetricsStandardizationServiceImpl.class);
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern TAG_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    @Override
    public String standardizeMetricName(String metricName) {
        if (metricName == null || metricName.isBlank()) {
            log.warn("Metric name is null or blank");
            return "unknown_metric";
        }

        // Convert to lowercase and replace spaces with underscores
        String standardized = metricName.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace(".", "_")
                .replaceAll("[^a-z0-9_]", "");

        // Ensure it starts with a letter
        if (standardized.isEmpty() || !Character.isLetter(standardized.charAt(0))) {
            standardized = "metric_" + standardized;
        }

        // Remove consecutive underscores
        standardized = standardized.replaceAll("_+", "_");

        // Remove leading/trailing underscores
        standardized = standardized.replaceAll("^_+|_+$", "");

        log.debug("Standardized metric name: {} -> {}", metricName, standardized);
        return standardized;
    }

    @Override
    public Map<String, String> standardizeTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Map.of();
        }

        Map<String, String> standardized = new HashMap<>();

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Standardize key
            String standardKey = key.toLowerCase()
                    .replace(" ", "_")
                    .replace("-", "_")
                    .replaceAll("[^a-z0-9_]", "");

            // Ensure key starts with a letter
            if (standardKey.isEmpty() || !Character.isLetter(standardKey.charAt(0))) {
                standardKey = "tag_" + standardKey;
            }

            // Standardize value
            String standardValue = value != null ? value.toLowerCase() : "unknown";
            standardValue = standardValue.replace(" ", "_").replace("-", "_");

            standardized.put(standardKey, standardValue);
        }

        log.debug("Standardized tags: {} -> {}", tags.size(), standardized.size());
        return standardized;
    }

    @Override
    public boolean isValidMetricName(String metricName) {
        if (metricName == null || metricName.isBlank()) {
            return false;
        }
        return METRIC_NAME_PATTERN.matcher(metricName).matches();
    }

    @Override
    public MetricValidationResult validateTags(Map<String, String> tags) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (tags == null || tags.isEmpty()) {
            warnings.add("Tags are empty");
            return new MetricValidationResult(true, errors, warnings);
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (!TAG_KEY_PATTERN.matcher(key).matches()) {
                errors.add("Invalid tag key format: " + key);
            }

            if (value == null || value.isBlank()) {
                warnings.add("Tag value is blank for key: " + key);
            }

            if (value != null && value.length() > 200) {
                warnings.add("Tag value is very long for key: " + key);
            }
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Tag validation failed: errors={}", errors);
        }

        return new MetricValidationResult(isValid, errors, warnings);
    }
}
