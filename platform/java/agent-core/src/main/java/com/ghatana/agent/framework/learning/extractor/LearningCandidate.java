/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.extractor;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A learning candidate extracted from episodes by a typed extractor.
 * Phase 5 FIX: Typed learning candidate to replace heuristic synthesis.
 *
 * @doc.type record
 * @doc.purpose Typed learning candidate
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record LearningCandidate(
        @NotNull LearningType type,
        @NotNull String situation,
        @NotNull String action,
        @NotNull String content,
        double confidence,
        @NotNull Map<String, Object> metadata
) {
    public LearningCandidate {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (situation == null || situation.isBlank()) {
            throw new IllegalArgumentException("situation must not be blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a learning candidate for a semantic fact.
     */
    @NotNull
    public static LearningCandidate fact(
            @NotNull String situation,
            @NotNull String action,
            @NotNull String content,
            double confidence) {
        return new LearningCandidate(
                LearningType.SEMANTIC_FACT,
                situation,
                action,
                content,
                confidence,
                Map.of("extractor", "fact-extractor")
        );
    }

    /**
     * Creates a learning candidate for a procedural skill.
     */
    @NotNull
    public static LearningCandidate procedure(
            @NotNull String situation,
            @NotNull String action,
            @NotNull String content,
            double confidence) {
        return new LearningCandidate(
                LearningType.PROCEDURAL_SKILL,
                situation,
                action,
                content,
                confidence,
                Map.of("extractor", "procedure-extractor")
        );
    }

    /**
     * Creates a learning candidate for negative knowledge.
     */
    @NotNull
    public static LearningCandidate negativeKnowledge(
            @NotNull String situation,
            @NotNull String action,
            @NotNull String content,
            double confidence) {
        return new LearningCandidate(
                LearningType.NEGATIVE_KNOWLEDGE,
                situation,
                action,
                content,
                confidence,
                Map.of("extractor", "negative-knowledge-extractor")
        );
    }

    /**
     * Creates a learning candidate for a failure mode.
     */
    @NotNull
    public static LearningCandidate failureMode(
            @NotNull String situation,
            @NotNull String action,
            @NotNull String content,
            double confidence) {
        return new LearningCandidate(
                LearningType.FAILURE_MODE,
                situation,
                action,
                content,
                confidence,
                Map.of("extractor", "failure-mode-extractor")
        );
    }
}
