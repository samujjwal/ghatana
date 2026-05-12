/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Benchmark for evaluating agent skill performance.
 *
 * @doc.type record
 * @doc.purpose Skill benchmark for evaluation
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record SkillBenchmark(
        @NotNull String benchmarkId,
        @NotNull String skillId,
        @NotNull String name,
        @NotNull String description,
        @NotNull List<SkillBenchmarkCase> cases,
        @NotNull Map<String, String> metadata
) {
    public SkillBenchmark {
        Objects.requireNonNull(benchmarkId, "benchmarkId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(cases, "cases must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        cases = List.copyOf(cases);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a skill benchmark.
     *
     * @param skillId skill identifier
     * @param name benchmark name
     * @param description benchmark description
     * @return skill benchmark
     */
    @NotNull
    public static SkillBenchmark of(
            @NotNull String skillId,
            @NotNull String name,
            @NotNull String description
    ) {
        return new SkillBenchmark(
                java.util.UUID.randomUUID().toString(),
                skillId,
                name,
                description,
                List.of(),
                Map.of()
        );
    }

    /**
     * Returns the number of test cases in this benchmark.
     *
     * @return number of test cases
     */
    public int caseCount() {
        return cases.size();
    }
}
