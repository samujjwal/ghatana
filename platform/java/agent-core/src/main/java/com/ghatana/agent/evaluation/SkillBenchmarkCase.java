/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * A single test case within a skill benchmark.
 *
 * @doc.type record
 * @doc.purpose Skill benchmark test case
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record SkillBenchmarkCase(
        @NotNull String caseId,
        @NotNull String name,
        @NotNull String description,
        @NotNull Map<String, Object> input,
        @NotNull Map<String, Object> expectedOutput,
        @NotNull String category,
        double weight
) {
    public SkillBenchmarkCase {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(expectedOutput, "expectedOutput must not be null");
        Objects.requireNonNull(category, "category must not be null");
        input = Map.copyOf(input);
        expectedOutput = Map.copyOf(expectedOutput);
    }

    /**
     * Creates a skill benchmark case.
     *
     * @param name case name
     * @param description case description
     * @param input test input
     * @param expectedOutput expected output
     * @return skill benchmark case
     */
    @NotNull
    public static SkillBenchmarkCase of(
            @NotNull String name,
            @NotNull String description,
            @NotNull Map<String, Object> input,
            @NotNull Map<String, Object> expectedOutput
    ) {
        return new SkillBenchmarkCase(
                java.util.UUID.randomUUID().toString(),
                name,
                description,
                input,
                expectedOutput,
                "general",
                1.0
        );
    }

    /**
     * Creates a skill benchmark case with custom weight.
     *
     * @param name case name
     * @param description case description
     * @param input test input
     * @param expectedOutput expected output
     * @param weight case weight for scoring
     * @return skill benchmark case
     */
    @NotNull
    public static SkillBenchmarkCase withWeight(
            @NotNull String name,
            @NotNull String description,
            @NotNull Map<String, Object> input,
            @NotNull Map<String, Object> expectedOutput,
            double weight
    ) {
        return new SkillBenchmarkCase(
                java.util.UUID.randomUUID().toString(),
                name,
                description,
                input,
                expectedOutput,
                "general",
                weight
        );
    }
}
