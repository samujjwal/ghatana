/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * A graded evaluation of an agent execution trace.
 *
 * <p>Trace grades evaluate execution quality across multiple dimensions:
 * task success, tool correctness, memory relevance, negative knowledge usage,
 * version compatibility, and safety.
 *
 * @doc.type record
 * @doc.purpose Graded evaluation of agent execution trace
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record TraceGrade(
        @NotNull String traceId,
        @NotNull String agentId,
        @NotNull String tenantId,
        @NotNull TaskSuccessGrade taskSuccess,
        @NotNull ToolCorrectnessGrade toolCorrectness,
        @NotNull MemoryRelevanceGrade memoryRelevance,
        @NotNull NegativeKnowledgeGrade negativeKnowledge,
        @NotNull VersionCompatibilityGrade versionCompatibility,
        @NotNull SafetyGrade safety,
        double overallScore,
        @NotNull GradeLevel overallLevel,
        @NotNull Map<String, String> metadata
) {
    public TraceGrade {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskSuccess, "taskSuccess must not be null");
        Objects.requireNonNull(toolCorrectness, "toolCorrectness must not be null");
        Objects.requireNonNull(memoryRelevance, "memoryRelevance must not be null");
        Objects.requireNonNull(negativeKnowledge, "negativeKnowledge must not be null");
        Objects.requireNonNull(versionCompatibility, "versionCompatibility must not be null");
        Objects.requireNonNull(safety, "safety must not be null");
        Objects.requireNonNull(overallLevel, "overallLevel must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (overallScore < 0.0 || overallScore > 1.0) {
            throw new IllegalArgumentException("overallScore must be between 0.0 and 1.0");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * Grade for task success dimension.
     */
    public record TaskSuccessGrade(
            double score,
            @NotNull String reason,
            @NotNull GradeLevel level
    ) {
        public TaskSuccessGrade {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(level, "level must not be null");
        }
    }

    /**
     * Grade for tool correctness dimension.
     */
    public record ToolCorrectnessGrade(
            double score,
            @NotNull String reason,
            @NotNull GradeLevel level,
            int correctToolCalls,
            int totalToolCalls
    ) {
        public ToolCorrectnessGrade {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(level, "level must not be null");
            if (correctToolCalls < 0) {
                throw new IllegalArgumentException("correctToolCalls must be non-negative");
            }
            if (totalToolCalls < 0) {
                throw new IllegalArgumentException("totalToolCalls must be non-negative");
            }
        }
    }

    /**
     * Grade for memory relevance dimension.
     */
    public record MemoryRelevanceGrade(
            double score,
            @NotNull String reason,
            @NotNull GradeLevel level,
            int relevantMemories,
            int totalMemories
    ) {
        public MemoryRelevanceGrade {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(level, "level must not be null");
            if (relevantMemories < 0) {
                throw new IllegalArgumentException("relevantMemories must be non-negative");
            }
            if (totalMemories < 0) {
                throw new IllegalArgumentException("totalMemories must be non-negative");
            }
        }
    }

    /**
     * Grade for negative knowledge usage dimension.
     */
    public record NegativeKnowledgeGrade(
            double score,
            @NotNull String reason,
            @NotNull GradeLevel level,
            boolean usedNegativeKnowledge,
            boolean avoidedKnownFailure
    ) {
        public NegativeKnowledgeGrade {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(level, "level must not be null");
        }
    }

    /**
     * Grade for version compatibility dimension.
     */
    public record VersionCompatibilityGrade(
            double score,
            @NotNull String reason,
            @NotNull GradeLevel level,
            @NotNull String versionScope,
            boolean compatible
    ) {
        public VersionCompatibilityGrade {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(versionScope, "versionScope must not be null");
        }
    }

    /**
     * Grade for safety dimension.
     */
    public record SafetyGrade(
            double score,
            @NotNull String reason,
            @NotNull GradeLevel level,
            boolean safetyViolation,
            @NotNull String violationType
    ) {
        public SafetyGrade {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0.0 and 1.0");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(violationType, "violationType must not be null");
        }
    }

    /**
     * Overall grade level.
     */
    public enum GradeLevel {
        /** Excellent performance (0.9-1.0) */
        EXCELLENT,
        /** Good performance (0.7-0.9) */
        GOOD,
        /** Acceptable performance (0.5-0.7) */
        ACCEPTABLE,
        /** Poor performance (0.3-0.5) */
        POOR,
        /** Unacceptable performance (0.0-0.3) */
        UNACCEPTABLE;

        /**
         * Converts a score to a GradeLevel.
         *
         * @param score score between 0.0 and 1.0
         * @return corresponding grade level
         */
        @NotNull
        public static GradeLevel fromScore(double score) {
            if (score >= 0.9) return EXCELLENT;
            if (score >= 0.7) return GOOD;
            if (score >= 0.5) return ACCEPTABLE;
            if (score >= 0.3) return POOR;
            return UNACCEPTABLE;
        }
    }
}
