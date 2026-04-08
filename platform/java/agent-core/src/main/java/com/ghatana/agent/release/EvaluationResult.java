/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing a single agent evaluation result.
 *
 * <p>Evaluation results capture the outcome of an automated or human-scored
 * assessment of an {@link AgentRelease}. They form the evidence base used by
 * the promotion pipeline to gate a release from staging to production.
 *
 * @param evaluationId   globally unique evaluation record ID
 * @param agentReleaseId the release this result was produced for
 * @param tenantId       tenant scope
 * @param evaluatorType  identifier of the evaluator (e.g., {@code "llm-judge"}, {@code "rubric"})
 * @param score          normalised score in [0.0, 1.0]
 * @param passed         whether the evaluation reached the pass threshold
 * @param rubricName     optional name of the rubric applied
 * @param runId          optional run ID if the evaluation was tied to a specific run
 * @param traceId        optional OpenTelemetry trace ID for diagnostic correlation
 * @param evaluatedAt    when the evaluation was produced
 * @param data           additional structured metadata
 *
 * @doc.type class
 * @doc.purpose Immutable evaluation result for agent release evidence
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record EvaluationResult(
        @NotNull String evaluationId,
        @NotNull String agentReleaseId,
        @NotNull String tenantId,
        @NotNull String evaluatorType,
        double score,
        boolean passed,
        @Nullable String rubricName,
        @Nullable String runId,
        @Nullable String traceId,
        @NotNull Instant evaluatedAt,
        @NotNull Map<String, Object> data
) {

    /** Validates required fields and normalises the data map to an immutable copy. */
    public EvaluationResult {
        Objects.requireNonNull(evaluationId, "evaluationId");
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(evaluatorType, "evaluatorType");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(data, "data");

        if (evaluationId.isBlank()) throw new IllegalArgumentException("evaluationId must not be blank");
        if (agentReleaseId.isBlank()) throw new IllegalArgumentException("agentReleaseId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (evaluatorType.isBlank()) throw new IllegalArgumentException("evaluatorType must not be blank");
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("score must be in [0.0, 1.0]");

        data = Map.copyOf(data);
    }

    /**
     * Creates a minimal evaluation result without optional fields.
     *
     * @param evaluationId   unique ID
     * @param agentReleaseId the evaluated release
     * @param tenantId       tenant scope
     * @param evaluatorType  evaluator identifier
     * @param score          normalised score
     * @param passed         pass/fail outcome
     * @param evaluatedAt    evaluation timestamp
     * @return a new {@code EvaluationResult} with empty data map
     */
    public static EvaluationResult of(
            String evaluationId,
            String agentReleaseId,
            String tenantId,
            String evaluatorType,
            double score,
            boolean passed,
            Instant evaluatedAt) {
        return new EvaluationResult(evaluationId, agentReleaseId, tenantId,
                evaluatorType, score, passed, null, null, null, evaluatedAt, Map.of());
    }
}
