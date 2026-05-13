/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of TraceGrader for computing execution trace grades.
 *
 * @doc.type class
 * @doc.purpose Default trace grader implementation
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public class DefaultTraceGrader implements TraceGrader {

    private final TaskSuccessGrader taskSuccessGrader;
    private final ToolCorrectnessGrader toolCorrectnessGrader;
    private final MemoryRelevanceGrader memoryRelevanceGrader;
    private final NegativeKnowledgeGrader negativeKnowledgeGrader;
    private final VersionCompatibilityGrader versionCompatibilityGrader;
    private final SafetyGrader safetyGrader;

    public DefaultTraceGrader(
            @NotNull TaskSuccessGrader taskSuccessGrader,
            @NotNull ToolCorrectnessGrader toolCorrectnessGrader,
            @NotNull MemoryRelevanceGrader memoryRelevanceGrader,
            @NotNull NegativeKnowledgeGrader negativeKnowledgeGrader,
            @NotNull VersionCompatibilityGrader versionCompatibilityGrader,
            @NotNull SafetyGrader safetyGrader) {
        this.taskSuccessGrader = Objects.requireNonNull(taskSuccessGrader, "taskSuccessGrader");
        this.toolCorrectnessGrader = Objects.requireNonNull(toolCorrectnessGrader, "toolCorrectnessGrader");
        this.memoryRelevanceGrader = Objects.requireNonNull(memoryRelevanceGrader, "memoryRelevanceGrader");
        this.negativeKnowledgeGrader = Objects.requireNonNull(negativeKnowledgeGrader, "negativeKnowledgeGrader");
        this.versionCompatibilityGrader = Objects.requireNonNull(versionCompatibilityGrader, "versionCompatibilityGrader");
        this.safetyGrader = Objects.requireNonNull(safetyGrader, "safetyGrader");
    }

    @Override
    @NotNull
    public TraceGrade grade(
            @NotNull String traceId,
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull Map<String, Object> executionData,
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(executionData, "executionData must not be null");
        Objects.requireNonNull(versionContext, "versionContext must not be null");

        TraceGrade.TaskSuccessGrade taskSuccess = taskSuccessGrader.grade(executionData);
        TraceGrade.ToolCorrectnessGrade toolCorrectness = toolCorrectnessGrader.grade(executionData);
        TraceGrade.MemoryRelevanceGrade memoryRelevance = memoryRelevanceGrader.grade(executionData);
        TraceGrade.NegativeKnowledgeGrade negativeKnowledge = negativeKnowledgeGrader.grade(executionData);
        TraceGrade.VersionCompatibilityGrade versionCompatibility = versionCompatibilityGrader.grade(executionData, versionContext);
        TraceGrade.SafetyGrade safety = safetyGrader.grade(executionData);

        // Calculate overall score from dimension scores
        double overallScore = calculateOverallScore(
                taskSuccess.score(),
                toolCorrectness.score(),
                memoryRelevance.score(),
                negativeKnowledge.score(),
                versionCompatibility.score(),
                safety.score()
        );

        TraceGrade.GradeLevel overallLevel = TraceGrade.GradeLevel.fromScore(overallScore);

        return new TraceGrade(
                traceId,
                agentId,
                tenantId,
                taskSuccess,
                toolCorrectness,
                memoryRelevance,
                negativeKnowledge,
                versionCompatibility,
                safety,
                overallScore,
                overallLevel,
                Map.of()
        );
    }

    @Override
    @NotNull
    public TraceGrade.TaskSuccessGrade gradeTaskSuccess(@NotNull Map<String, Object> executionData) {
        return taskSuccessGrader.grade(executionData);
    }

    @Override
    @NotNull
    public TraceGrade.ToolCorrectnessGrade gradeToolCorrectness(@NotNull Map<String, Object> executionData) {
        return toolCorrectnessGrader.grade(executionData);
    }

    @Override
    @NotNull
    public TraceGrade.MemoryRelevanceGrade gradeMemoryRelevance(@NotNull Map<String, Object> executionData) {
        return memoryRelevanceGrader.grade(executionData);
    }

    @Override
    @NotNull
    public TraceGrade.NegativeKnowledgeGrade gradeNegativeKnowledge(@NotNull Map<String, Object> executionData) {
        return negativeKnowledgeGrader.grade(executionData);
    }

    @Override
    @NotNull
    public TraceGrade.VersionCompatibilityGrade gradeVersionCompatibility(
            @NotNull Map<String, Object> executionData,
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext) {
        return versionCompatibilityGrader.grade(executionData, versionContext);
    }

    @Override
    @NotNull
    public TraceGrade.SafetyGrade gradeSafety(@NotNull Map<String, Object> executionData) {
        return safetyGrader.grade(executionData);
    }

    /**
     * Calculates overall score from dimension scores using weighted average.
     */
    private double calculateOverallScore(
            double taskSuccess,
            double toolCorrectness,
            double memoryRelevance,
            double negativeKnowledge,
            double versionCompatibility,
            double safety) {
        // Weighted average with safety having highest weight
        return (taskSuccess * 0.2
                + toolCorrectness * 0.15
                + memoryRelevance * 0.15
                + negativeKnowledge * 0.1
                + versionCompatibility * 0.1
                + safety * 0.3);
    }
}
