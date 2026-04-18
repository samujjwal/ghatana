/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import java.util.Map;

/**
 * Service for pre-filling pipeline configuration from historical data.
 * <p>
 * Suggests configuration values based on similar historical pipelines.
 * This can be enhanced with ML-based recommendation in the future.
 *
 * @doc.type interface
 * @doc.purpose Pre-fill pipeline configuration from history
 * @doc.layer core
 * @doc.pattern Service
 */
public interface ConfigurationPrefillService {

    /**
     * Get suggested configuration for a pipeline stage.
     *
     * @param stageType type of stage (e.g., "filter", "detect")
     * @param eventType event type being processed
     * @param context additional context (e.g., industry, use case)
     * @return suggested configuration values
     */
    Map<String, Object> suggestConfiguration(String stageType, String eventType, Map<String, Object> context);

    /**
     * Get confidence score for a configuration suggestion.
     *
     * @param stageType type of stage
     * @param eventType event type
     * @param configKey configuration key
     * @return confidence score (0.0 to 1.0)
     */
    double getConfidence(String stageType, String eventType, String configKey);

    /**
     * Configuration suggestion with metadata.
     */
    record ConfigSuggestion(
        String key,
        Object value,
        double confidence,
        String reason
    ) {}
}
