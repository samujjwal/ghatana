/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Service for grading agent execution traces across multiple dimensions.
 *
 * <p>Computes grades for task success, tool correctness, memory relevance,
 * negative knowledge usage, version compatibility, and safety.
 *
 * @doc.type interface
 * @doc.purpose Trace grading service
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface TraceGrader {

    /**
     * Grades a trace execution across all dimensions.
     *
     * @param traceId trace ID
     * @param agentId agent ID
     * @param tenantId tenant ID
     * @param executionData execution data including inputs, outputs, tool calls, memory access
     * @param versionContext version context for compatibility checking
     * @return trace grade with all dimension scores
     */
    @NotNull
    TraceGrade grade(
            @NotNull String traceId,
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull Map<String, Object> executionData,
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext);

    /**
     * Grades task success dimension.
     *
     * @param executionData execution data
     * @return task success grade
     */
    @NotNull
    TraceGrade.TaskSuccessGrade gradeTaskSuccess(@NotNull Map<String, Object> executionData);

    /**
     * Grades tool correctness dimension.
     *
     * @param executionData execution data
     * @return tool correctness grade
     */
    @NotNull
    TraceGrade.ToolCorrectnessGrade gradeToolCorrectness(@NotNull Map<String, Object> executionData);

    /**
     * Grades memory relevance dimension.
     *
     * @param executionData execution data
     * @return memory relevance grade
     */
    @NotNull
    TraceGrade.MemoryRelevanceGrade gradeMemoryRelevance(@NotNull Map<String, Object> executionData);

    /**
     * Grades negative knowledge usage dimension.
     *
     * @param executionData execution data
     * @return negative knowledge grade
     */
    @NotNull
    TraceGrade.NegativeKnowledgeGrade gradeNegativeKnowledge(@NotNull Map<String, Object> executionData);

    /**
     * Grades version compatibility dimension.
     *
     * @param executionData execution data
     * @param versionContext version context
     * @return version compatibility grade
     */
    @NotNull
    TraceGrade.VersionCompatibilityGrade gradeVersionCompatibility(
            @NotNull Map<String, Object> executionData,
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext);

    /**
     * Grades safety dimension.
     *
     * @param executionData execution data
     * @return safety grade
     */
    @NotNull
    TraceGrade.SafetyGrade gradeSafety(@NotNull Map<String, Object> executionData);
}
