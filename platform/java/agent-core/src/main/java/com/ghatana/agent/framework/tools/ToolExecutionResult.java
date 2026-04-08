/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized result of a single tool execution, including policy, approval, and audit decisions.
 *
 * @param invocationId        matches the {@link ToolExecutionEnvelope#invocationId()} of the request
 * @param status              outcome of the execution
 * @param output              the tool's output object; null when {@code status != SUCCESS}
 * @param policyDecision      governance decision applied: {@code "ALLOW"}, {@code "DENY"}, or {@code "CONDITIONAL"}
 * @param approvalDecision    approval gate result: {@code "APPROVED"}, {@code "DENIED"}, {@code "PENDING"}, {@code "N/A"}
 * @param sideEffectSummary   structured summary of side effects for audit purposes
 * @param errorMessage        human-readable error message; null when successful
 * @param correlationId       trace/correlation ID for cross-service observability
 * @param completedAt         timestamp when execution concluded
 * @param executionDuration   wall-clock time for the tool execution
 *
 * @doc.type record
 * @doc.purpose Normalized result of a tool execution including policy and approval decisions
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolExecutionResult(
        String invocationId,
        ToolExecutionStatus status,
        Object output,
        String policyDecision,
        String approvalDecision,
        Map<String, Object> sideEffectSummary,
        String errorMessage,
        String correlationId,
        Instant completedAt,
        Duration executionDuration) {

    public ToolExecutionResult {
        Objects.requireNonNull(invocationId, "invocationId must not be null");
        Objects.requireNonNull(status,       "status must not be null");
        Objects.requireNonNull(completedAt,  "completedAt must not be null");

        if (invocationId.isBlank()) {
            throw new IllegalArgumentException("invocationId must not be blank");
        }

        sideEffectSummary = sideEffectSummary != null ? Map.copyOf(sideEffectSummary) : Map.of();
    }

    /**
     * Creates a successful result.
     *
     * @param invocationId     ID from the envelope
     * @param output           tool output
     * @param sideEffects      recorded side effects
     * @param correlationId    trace correlation ID
     * @param completedAt      completion timestamp
     * @param executionDuration wall-clock duration
     * @return a SUCCESS result
     */
    public static ToolExecutionResult succeeded(
            String invocationId,
            Object output,
            Map<String, Object> sideEffects,
            String correlationId,
            Instant completedAt,
            Duration executionDuration) {
        return new ToolExecutionResult(
                invocationId, ToolExecutionStatus.SUCCESS,
                output, "ALLOW", "N/A",
                sideEffects, null, correlationId,
                completedAt, executionDuration);
    }

    /**
     * Creates a policy-denied result.
     *
     * @param invocationId  ID from the envelope
     * @param reason        human-readable denial reason
     * @param completedAt   decision timestamp
     * @return a DENIED result
     */
    public static ToolExecutionResult denied(
            String invocationId,
            String reason,
            Instant completedAt) {
        return new ToolExecutionResult(
                invocationId, ToolExecutionStatus.DENIED,
                null, "DENY", "N/A",
                Map.of(), reason, null,
                completedAt, Duration.ZERO);
    }

    /**
     * Creates a failed result.
     *
     * @param invocationId      ID from the envelope
     * @param errorMessage      failure detail
     * @param correlationId     trace correlation ID
     * @param completedAt       failure timestamp
     * @param executionDuration wall-clock duration up to failure
     * @return a FAILED result
     */
    public static ToolExecutionResult failed(
            String invocationId,
            String errorMessage,
            String correlationId,
            Instant completedAt,
            Duration executionDuration) {
        return new ToolExecutionResult(
                invocationId, ToolExecutionStatus.FAILED,
                null, "ALLOW", "N/A",
                Map.of(), errorMessage, correlationId,
                completedAt, executionDuration);
    }

    /**
     * Creates a result indicating that the execution is waiting for approval.
     *
     * @param invocationId ID from the envelope
     * @param completedAt  pending timestamp
     * @return an APPROVAL_PENDING result
     */
    public static ToolExecutionResult pendingApproval(String invocationId, Instant completedAt) {
        return new ToolExecutionResult(
                invocationId, ToolExecutionStatus.APPROVAL_PENDING,
                null, "CONDITIONAL", "PENDING",
                Map.of(), null, null,
                completedAt, Duration.ZERO);
    }
}
