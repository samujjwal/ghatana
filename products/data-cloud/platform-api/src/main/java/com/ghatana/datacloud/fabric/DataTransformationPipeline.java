/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.fabric;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Pipeline for data transformation operations.
 *
 * <p>Defines and executes data transformation workflows.
 *
 * @doc.type interface
 * @doc.purpose Data transformation pipeline
 * @doc.layer product
 * @doc.pattern Pipeline, Data Processing
 */
public interface DataTransformationPipeline {

    /**
     * Create a transformation pipeline.
     *
     * @param definition pipeline definition
     * @return promise of created pipeline
     */
    Promise<PipelineDefinition> createPipeline(PipelineDefinition definition);

    /**
     * Execute a pipeline.
     *
     * @param pipelineId pipeline identifier
     * @param input input data
     * @return promise of execution result
     */
    Promise<ExecutionResult> execute(String pipelineId, List<Map<String, Object>> input);

    /**
     * Get pipeline by ID.
     *
     * @param pipelineId pipeline identifier
     * @return promise of pipeline
     */
    Promise<java.util.Optional<PipelineDefinition>> getPipeline(String pipelineId);

    /**
     * List pipelines for tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of pipeline list
     */
    Promise<List<PipelineDefinition>> listPipelines(String tenantId);

    /**
     * Delete a pipeline.
     *
     * @param pipelineId pipeline identifier
     * @return promise completing when deleted
     */
    Promise<Void> deletePipeline(String pipelineId);

    /**
     * Get execution history.
     *
     * @param pipelineId pipeline identifier
     * @return promise of execution history
     */
    Promise<List<ExecutionRecord>> getExecutionHistory(String pipelineId);

    /**
     * Validate a pipeline definition.
     *
     * @param definition pipeline definition
     * @return promise of validation result
     */
    Promise<ValidationResult> validate(PipelineDefinition definition);

    /**
     * Pipeline definition.
     */
    record PipelineDefinition(
        String id,
        String name,
        String description,
        String tenantId,
        List<TransformationStep> steps,
        Map<String, Object> configuration,
        String inputSchema,
        String outputSchema,
        boolean enabled
    ) {}

    /**
     * Transformation step.
     */
    record TransformationStep(
        String id,
        String name,
        StepType type,
        Map<String, Object> configuration,
        String condition,
        List<String> inputColumns,
        List<String> outputColumns
    ) {}

    /**
     * Step types.
     */
    enum StepType {
        FILTER, MAP, AGGREGATE, JOIN, SORT, LIMIT, CUSTOM, VALIDATE, ENRICH
    }

    /**
     * Execution result.
     */
    record ExecutionResult(
        String pipelineId,
        String executionId,
        boolean success,
        List<Map<String, Object>> output,
        int inputCount,
        int outputCount,
        long executionTimeMs,
        List<String> errors,
        List<String> warnings
    ) {}

    /**
     * Execution record.
     */
    record ExecutionRecord(
        String executionId,
        String pipelineId,
        boolean success,
        int inputCount,
        int outputCount,
        long executionTimeMs,
        String triggeredBy,
        java.time.Instant startedAt,
        java.time.Instant completedAt
    ) {}

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<StepValidation> stepValidations
    ) {}

    /**
     * Step validation.
     */
    record StepValidation(
        String stepId,
        boolean valid,
        List<String> errors
    ) {}
}
