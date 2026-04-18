/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import java.util.List;
import java.util.Map;

/**
 * Service for suggesting pipeline stages based on event types and context.
 * <p>
 * This service provides intelligent recommendations for pipeline stages based on
 * the event types being processed and the use case. Suggestions can be enhanced
 * with ML models in the future.
 *
 * @doc.type interface
 * @doc.purpose Provide stage suggestions for pipeline building
 * @doc.layer core
 * @doc.pattern Service
 */
public interface StageSuggestionService {

    /**
     * Suggest stages for a pipeline based on event types and context.
     *
     * @param eventTypes the event types the pipeline will process
     * @param context additional context (e.g., use case, industry, constraints)
     * @return list of suggested stages with confidence scores
     */
    List<StageSuggestion> suggestStages(List<String> eventTypes, Map<String, Object> context);

    /**
     * Get stage templates for a specific stage type.
     *
     * @param stageType the type of stage (e.g., "filter", "transform", "detect")
     * @return list of stage templates
     */
    List<StageTemplate> getStageTemplates(String stageType);

    /**
     * Represents a suggested stage with confidence score.
     */
    record StageSuggestion(
        String stageType,
        String stageName,
        String description,
        double confidence,
        Map<String, Object> suggestedConfig,
        List<String> dependencies
    ) {}

    /**
     * Represents a reusable stage template.
     */
    record StageTemplate(
        String id,
        String name,
        String description,
        String stageType,
        Map<String, Object> defaultConfig,
        List<String> applicableEventTypes
    ) {}
}
