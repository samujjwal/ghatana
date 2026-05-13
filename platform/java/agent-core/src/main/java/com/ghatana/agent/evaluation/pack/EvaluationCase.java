/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import com.ghatana.agent.evaluation.EvaluationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * A single evaluation case within a skill-scoped {@link EvaluationPack}.
 *
 * <p>Unlike the general {@code EvaluationTestCase}, an {@code EvaluationCase} is
 * anchored to a specific skill and mastery context and carries a weight and
 * required-pass flag that drive the pack's pass-rate calculation.
 *
 * @doc.type record
 * @doc.purpose Single evaluation case within a mastery-scoped evaluation pack
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationCase(
        @NotNull String caseId,
        @NotNull String name,
        @NotNull String description,
        @NotNull EvaluationType category,
        @NotNull String input,
        @NotNull String expectedOutput,
        @NotNull Map<String, String> context,
        /**
         * Relative weight contributing to the pack's overall pass-rate calculation.
         * Defaults to {@code 1}; higher-priority cases may carry more weight.
         */
        int weight,
        /**
         * When {@code true} this case must pass regardless of {@link EvaluationPack#minPassRate()}.
         */
        boolean required,
        /**
         * Optional human-readable rationale for why this case exists.
         */
        @Nullable String rationale
) {
    public EvaluationCase {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(expectedOutput, "expectedOutput must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be >= 1");
        }
        context = Map.copyOf(context);
    }

    /**
     * Creates a standard case with weight {@code 1}, non-required, and no rationale.
     *
     * @param caseId         unique case identifier
     * @param name           human-readable name
     * @param description    description of what is evaluated
     * @param category       evaluation category
     * @param input          input to the agent
     * @param expectedOutput expected agent output or behaviour
     * @return evaluation case
     */
    @NotNull
    public static EvaluationCase standard(
            @NotNull String caseId,
            @NotNull String name,
            @NotNull String description,
            @NotNull EvaluationType category,
            @NotNull String input,
            @NotNull String expectedOutput) {
        return new EvaluationCase(caseId, name, description, category, input, expectedOutput,
                Map.of(), 1, false, null);
    }

    /**
     * Creates a required case with weight {@code 1}.
     *
     * @param caseId         unique case identifier
     * @param name           human-readable name
     * @param description    description of what is evaluated
     * @param category       evaluation category
     * @param input          input to the agent
     * @param expectedOutput expected agent output or behaviour
     * @param rationale      rationale for requiring this case
     * @return required evaluation case
     */
    @NotNull
    public static EvaluationCase required(
            @NotNull String caseId,
            @NotNull String name,
            @NotNull String description,
            @NotNull EvaluationType category,
            @NotNull String input,
            @NotNull String expectedOutput,
            @Nullable String rationale) {
        return new EvaluationCase(caseId, name, description, category, input, expectedOutput,
                Map.of(), 1, true, rationale);
    }
}
