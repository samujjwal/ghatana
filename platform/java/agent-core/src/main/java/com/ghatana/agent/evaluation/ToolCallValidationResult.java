/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Result of tool call validation during evaluation.
 *
 * @doc.type record
 * @doc.purpose Tool call validation result
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ToolCallValidationResult(
        boolean passed,
        int correctCalls,
        int totalCalls,
        @NotNull String reason,
        @NotNull Map<String, String> metadata
) {
    public ToolCallValidationResult {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (correctCalls < 0) {
            throw new IllegalArgumentException("correctCalls must be non-negative");
        }
        if (totalCalls < 0) {
            throw new IllegalArgumentException("totalCalls must be non-negative");
        }
        metadata = Map.copyOf(metadata);
    }
}
