/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * A single test case within an evaluation pack.
 *
 * @doc.type record
 * @doc.purpose Single evaluation test case
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationTestCase(
        @NotNull String caseId,
        @NotNull String name,
        @NotNull String description,
        @NotNull EvaluationType type,
        @NotNull String input,
        @NotNull String expectedOutput,
        @NotNull Map<String, String> context,
        @NotNull Map<String, String> metadata
) {
    public EvaluationTestCase {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(expectedOutput, "expectedOutput must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        context = Map.copyOf(context);
        metadata = Map.copyOf(metadata);
    }
}
