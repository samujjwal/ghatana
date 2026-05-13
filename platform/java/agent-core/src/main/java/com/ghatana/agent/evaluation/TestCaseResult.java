/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a single test case execution during evaluation.
 *
 * @doc.type record
 * @doc.purpose Test case execution result
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record TestCaseResult(
        @NotNull String caseId,
        @NotNull EvaluationType type,
        boolean passed,
        @NotNull OutputComparisonResult outputResult,
        @NotNull ToolCallValidationResult toolResult,
        @NotNull SafetyGateCheckResult safetyResult,
        @NotNull String selectedVersion,
        @NotNull Instant completedAt,
        @NotNull Map<String, String> metadata
) {
    public TestCaseResult {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(outputResult, "outputResult must not be null");
        Objects.requireNonNull(toolResult, "toolResult must not be null");
        Objects.requireNonNull(safetyResult, "safetyResult must not be null");
        Objects.requireNonNull(selectedVersion, "selectedVersion must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }
}
