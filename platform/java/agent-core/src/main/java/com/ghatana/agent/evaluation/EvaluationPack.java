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
 * A pack of evaluation tests for validating learning artifacts.
 *
 * <p>Evaluation packs contain regression, safety, recovery, compatibility,
 * transferability, performance, and security tests.
 *
 * @doc.type record
 * @doc.purpose Pack of evaluation tests for learning artifacts
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationPack(
        @NotNull String packId,
        @NotNull String name,
        @NotNull String description,
        @NotNull String targetArtifactId,
        @NotNull String targetArtifactType,
        @NotNull List<EvaluationTestCase> testCases,
        @NotNull Map<String, String> environmentConfig,
        @NotNull Instant createdAt,
        @NotNull Map<String, String> labels
) {
    public EvaluationPack {
        Objects.requireNonNull(packId, "packId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        Objects.requireNonNull(targetArtifactType, "targetArtifactType must not be null");
        Objects.requireNonNull(testCases, "testCases must not be null");
        Objects.requireNonNull(environmentConfig, "environmentConfig must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        testCases = List.copyOf(testCases);
        environmentConfig = Map.copyOf(environmentConfig);
        labels = Map.copyOf(labels);
    }

    /**
     * Returns the number of test cases of a specific type.
     *
     * @param type evaluation type
     * @return count of test cases
     */
    public int countByType(@NotNull EvaluationType type) {
        return (int) testCases.stream().filter(tc -> tc.type() == type).count();
    }
}
