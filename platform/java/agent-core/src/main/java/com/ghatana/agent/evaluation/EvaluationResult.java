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
 * Result of running an evaluation pack.
 *
 * @doc.type record
 * @doc.purpose Result of evaluation pack execution
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationResult(
        @NotNull String resultId,
        @NotNull String packId,
        @NotNull String targetArtifactId,
        @NotNull String deltaId,
        @NotNull Instant startedAt,
        @NotNull Instant completedAt,
        int totalTests,
        int passedTests,
        int failedTests,
        int skippedTests,
        double overallScore,
        @NotNull List<TestCaseResult> caseResults,
        @NotNull Map<String, String> metadata
) {
    public EvaluationResult {
        Objects.requireNonNull(resultId, "resultId must not be null");
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        Objects.requireNonNull(deltaId, "deltaId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(caseResults, "caseResults must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        caseResults = List.copyOf(caseResults);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Returns true if all tests passed.
     *
     * @return true if all tests passed
     */
    public boolean allPassed() {
        return failedTests == 0 && skippedTests == 0;
    }

    /**
     * Returns the pass rate as a percentage.
     *
     * @return pass rate percentage
     */
    public double passRate() {
        return totalTests == 0 ? 0.0 : (passedTests * 100.0) / totalTests;
    }

    /**
     * Result of a single test case.
     */
    public record TestCaseResult(
            @NotNull String caseId,
            @NotNull String name,
            boolean passed,
            @NotNull String actualOutput,
            @NotNull String errorMessage,
            long durationMs
    ) {
        public TestCaseResult {
            Objects.requireNonNull(caseId, "caseId must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(actualOutput, "actualOutput must not be null");
            Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        }
    }
}
