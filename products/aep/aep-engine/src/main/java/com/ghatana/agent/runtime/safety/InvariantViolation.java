/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Records an invariant violation detected during agent execution.
 *
 * <p>Invariant violations are critical safety signals. Depending on severity,
 * they may trigger circuit breakers, kill switches, or escalation to human
 * operators.
 *
 * @doc.type record
 * @doc.purpose Invariant violation event for runtime safety monitoring
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record InvariantViolation(
        /** Unique violation identifier. */
        @NotNull String violationId,

        /** The invariant rule that was violated. */
        @NotNull String invariantId,

        /** Agent that violated the invariant. */
        @NotNull String agentId,

        /** Tenant scope. */
        @NotNull String tenantId,

        /** Severity classification. */
        @NotNull Severity severity,

        /** Human-readable description of the violation. */
        @NotNull String description,

        /** Action taken in response. */
        @NotNull ResponseAction responseTaken,

        /** Structured context about the violation. */
        @NotNull Map<String, String> context,

        /** When the violation was detected. */
        @NotNull Instant detectedAt
) {

    /**
     * Compact constructor with validation.
     */
    public InvariantViolation {
        Objects.requireNonNull(violationId, "violationId");
        Objects.requireNonNull(invariantId, "invariantId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(responseTaken, "responseTaken");
        Objects.requireNonNull(detectedAt, "detectedAt");
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    /**
     * Violation severity levels.
     */
    public enum Severity {
        /** Informational — logged but no enforcement action. */
        INFO,
        /** Warning — may trigger degraded-mode operation. */
        WARNING,
        /** Critical — triggers circuit breaker or turn abort. */
        CRITICAL,
        /** Fatal — triggers immediate kill switch. */
        FATAL
    }

    /**
     * Action taken in response to the violation.
     */
    public enum ResponseAction {
        /** No action; informational only. */
        LOGGED,
        /** Current action was aborted. */
        ACTION_ABORTED,
        /** Agent turn was terminated. */
        TURN_TERMINATED,
        /** Circuit breaker tripped; agent is now degraded. */
        CIRCUIT_BREAKER_TRIPPED,
        /** Kill switch activated; agent is fully stopped. */
        KILL_SWITCH_ACTIVATED,
        /** Escalated to human operator. */
        ESCALATED
    }
}
