/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of an evaluation pack execution.
 *
 * @doc.type record
 * @doc.purpose Evaluation pack execution result
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationRunResult(
        @NotNull String runId,
        @NotNull String packId,
        @NotNull Instant startedAt,
        @NotNull Instant completedAt,
        @NotNull List<TestCaseResult> results,
        boolean passed,
        double overallScore,
        @NotNull Map<String, String> metadata
) {
    public EvaluationRunResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(results, "results must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        results = List.copyOf(results);
        metadata = Map.copyOf(metadata);
        if (overallScore < 0.0 || overallScore > 1.0) {
            throw new IllegalArgumentException("overallScore must be between 0.0 and 1.0");
        }
    }
}
