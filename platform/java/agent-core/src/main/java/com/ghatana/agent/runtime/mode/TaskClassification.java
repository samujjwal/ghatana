/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Classification of a task including risk, novelty, and metadata.
 *
 * @doc.type record
 * @doc.purpose Task classification for mode selection
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record TaskClassification(
        @NotNull TaskRiskLevel riskLevel,
        @NotNull TaskNovelty novelty,
        @NotNull Map<String, String> metadata
) {
    public TaskClassification {
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(novelty, "novelty must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a task classification with risk and novelty.
     *
     * @param riskLevel risk level
     * @param novelty novelty
     * @return task classification
     */
    @NotNull
    public static TaskClassification of(@NotNull TaskRiskLevel riskLevel, @NotNull TaskNovelty novelty) {
        return new TaskClassification(riskLevel, novelty, Map.of());
    }

    /**
     * Returns true if this task is high risk or critical.
     *
     * @return true if high risk or critical
     */
    public boolean isHighRisk() {
        return riskLevel == TaskRiskLevel.HIGH || riskLevel == TaskRiskLevel.CRITICAL;
    }

    /**
     * Returns true if this task is novel or similar.
     *
     * @return true if novel or similar
     */
    public boolean isNovel() {
        return novelty == TaskNovelty.NOVEL || novelty == TaskNovelty.SIMILAR;
    }

    /**
     * Returns true if this task is familiar.
     *
     * @return true if familiar
     */
    public boolean isFamiliar() {
        return novelty == TaskNovelty.FAMILIAR;
    }
}
