/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Result of output comparison during evaluation.
 *
 * @doc.type record
 * @doc.purpose Output comparison result
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record OutputComparisonResult(
        boolean passed,
        double similarityScore,
        @NotNull String reason,
        @NotNull Map<String, String> metadata
) {
    public OutputComparisonResult {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException("similarityScore must be between 0.0 and 1.0");
        }
        metadata = Map.copyOf(metadata);
    }
}
