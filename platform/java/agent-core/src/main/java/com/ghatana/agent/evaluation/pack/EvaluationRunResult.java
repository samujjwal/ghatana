/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a completed {@link EvaluationRun}.
 *
 * <p>An {@code EvaluationRunResult} records the aggregate pass/fail metrics,
 * per-case outcomes, and evidence refs that downstream promotion policies
 * use to decide whether to transition a {@code MasteryItem} to a higher state.
 *
 * @doc.type record
 * @doc.purpose Outcome of a completed evaluation run
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationRunResult(
        @NotNull String runId,
        @NotNull String evaluationPackId,
        @NotNull String tenantId,
        int totalCases,
        int passedCases,
        int failedCases,
        /**
         * Weighted pass rate in [0.0, 1.0].
         */
        double passRate,
        /**
         * {@code true} when {@link #passRate} &ge; {@link EvaluationPack#minPassRate()}.
         */
        boolean meetsMinPassRate,
        /**
         * {@code true} when all regression cases passed (or none were required).
         */
        boolean regressionPassed,
        /**
         * {@code true} when all safety cases passed (or none were required).
         */
        boolean safetyPassed,
        @NotNull List<CaseResult> caseResults,
        /**
         * Evidence refs produced by this run, keyed by evidence type.
         */
        @NotNull Map<String, String> evidenceRefs,
        @NotNull Instant completedAt
) {
    public EvaluationRunResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(evaluationPackId, "evaluationPackId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(caseResults, "caseResults must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (passRate < 0.0 || passRate > 1.0) {
            throw new IllegalArgumentException("passRate must be in [0.0, 1.0]");
        }
        caseResults = List.copyOf(caseResults);
        evidenceRefs = Map.copyOf(evidenceRefs);
    }

    /**
     * Returns {@code true} if all pass constraints are satisfied — the pack is considered
     * to have passed and its evidence can be used to drive mastery promotion.
     *
     * @return true when the overall result satisfies all pack constraints
     */
    public boolean passed() {
        return meetsMinPassRate && regressionPassed && safetyPassed;
    }

    /**
     * Outcome of a single {@link EvaluationCase} within a run.
     *
     * @param caseId       ID of the evaluated case
     * @param name         human-readable name of the case
     * @param passed       whether the case passed
     * @param actualOutput the actual output produced during evaluation
     * @param errorMessage empty string when the case passed; error details otherwise
     * @param durationMs   wall-clock duration of this case in milliseconds
     * @param required     mirrors {@link EvaluationCase#required()} for quick reference
     */
    public record CaseResult(
            @NotNull String caseId,
            @NotNull String name,
            boolean passed,
            @NotNull String actualOutput,
            @NotNull String errorMessage,
            long durationMs,
            boolean required
    ) {
        public CaseResult {
            Objects.requireNonNull(caseId, "caseId must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(actualOutput, "actualOutput must not be null");
            Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        }

        /**
         * Convenience factory for a passing case result.
         *
         * @param caseId       evaluated case ID
         * @param name         case name
         * @param actualOutput actual output
         * @param durationMs   wall-clock duration
         * @param required     whether the case was required
         * @return passing result
         */
        @NotNull
        public static CaseResult pass(
                @NotNull String caseId,
                @NotNull String name,
                @NotNull String actualOutput,
                long durationMs,
                boolean required) {
            return new CaseResult(caseId, name, true, actualOutput, "", durationMs, required);
        }

        /**
         * Convenience factory for a failing case result.
         *
         * @param caseId       evaluated case ID
         * @param name         case name
         * @param actualOutput actual output (may be partial)
         * @param errorMessage description of the failure
         * @param durationMs   wall-clock duration
         * @param required     whether the case was required
         * @return failing result
         */
        @NotNull
        public static CaseResult fail(
                @NotNull String caseId,
                @NotNull String name,
                @NotNull String actualOutput,
                @NotNull String errorMessage,
                long durationMs,
                boolean required) {
            return new CaseResult(caseId, name, false, actualOutput, errorMessage, durationMs, required);
        }
    }
}
