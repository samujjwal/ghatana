/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for creating pipelines from natural language descriptions.
 * <p>
 * Parses natural language descriptions and generates pipeline specifications.
 * Currently uses rule-based parsing; can be enhanced with ML models in the future.
 *
 * @doc.type interface
 * @doc.purpose Create pipelines from natural language
 * @doc.layer core
 * @doc.pattern Service
 */
public interface NaturalLanguagePipelineService {

    /**
     * Generate a pipeline specification from natural language description.
     *
     * @param description natural language description (e.g., "Create fraud detection pipeline")
     * @param context additional context (e.g., event types, constraints)
     * @return generated pipeline specification
     */
    PipelineSpec generatePipeline(String description, Map<String, Object> context);

    /**
     * Validate a natural language description.
     *
     * @param description natural language description
     * @return validation result with any errors
     */
    ValidationResult validateDescription(String description);

    /**
     * Pipeline specification generated from natural language.
     */
    record PipelineSpec(
        String name,
        String description,
        String eventType,
        List<StageSpec> stages
    ) {}

    /**
     * Stage specification.
     */
    record StageSpec(
        String id,
        String type,
        String name,
        Map<String, Object> config,
        List<String> dependencies
    ) {}

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}
}
