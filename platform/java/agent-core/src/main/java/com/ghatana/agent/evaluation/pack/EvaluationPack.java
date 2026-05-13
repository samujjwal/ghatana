/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import com.ghatana.agent.mastery.VersionScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * A skill-scoped, mastery-aware evaluation pack.
 *
 * <p>An {@code EvaluationPack} groups a set of {@link EvaluationCase}s that collectively
 * validate agent mastery of a specific skill at a specific version scope. It declares
 * the minimum pass rate required for promotion and whether regression and safety suites
 * must pass unconditionally.
 *
 * <p>This type lives in the {@code pack} subpackage and is distinct from the more general
 * {@code com.ghatana.agent.evaluation.EvaluationPack}, which evaluates learning artifacts
 * rather than mastery state.
 *
 * @doc.type record
 * @doc.purpose Skill-scoped mastery evaluation pack
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationPack(
        @NotNull String evaluationPackId,
        @NotNull String tenantId,
        @NotNull String skillId,
        @NotNull String version,
        @NotNull VersionScope versionScope,
        @NotNull List<EvaluationCase> cases,
        @NotNull List<String> requiredEvidenceTypes,
        /**
         * Fraction of cases (0.0–1.0) that must pass for the pack to be considered passing.
         * Required cases are evaluated independently of this threshold.
         */
        double minPassRate,
        /**
         * When {@code true}, at least one {@link com.ghatana.agent.evaluation.EvaluationType#REGRESSION}
         * case must be present and must pass.
         */
        boolean requiresRegression,
        /**
         * When {@code true}, at least one {@link com.ghatana.agent.evaluation.EvaluationType#SAFETY}
         * case must be present and must pass.
         */
        boolean requiresSafety
) {
    public EvaluationPack {
        Objects.requireNonNull(evaluationPackId, "evaluationPackId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(versionScope, "versionScope must not be null");
        Objects.requireNonNull(cases, "cases must not be null");
        Objects.requireNonNull(requiredEvidenceTypes, "requiredEvidenceTypes must not be null");
        if (minPassRate < 0.0 || minPassRate > 1.0) {
            throw new IllegalArgumentException("minPassRate must be in [0.0, 1.0]");
        }
        cases = List.copyOf(cases);
        requiredEvidenceTypes = List.copyOf(requiredEvidenceTypes);
    }

    /**
     * Returns the total weighted count of all cases.
     *
     * @return total weight
     */
    public int totalWeight() {
        return cases.stream().mapToInt(EvaluationCase::weight).sum();
    }

    /**
     * Returns {@code true} if at least one case has the given category.
     *
     * @param category evaluation type to check
     * @return whether any case covers that category
     */
    public boolean hasCategoryOf(@NotNull com.ghatana.agent.evaluation.EvaluationType category) {
        return cases.stream().anyMatch(c -> c.category() == category);
    }
}
