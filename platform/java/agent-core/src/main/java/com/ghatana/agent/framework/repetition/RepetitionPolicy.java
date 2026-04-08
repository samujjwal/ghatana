/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.repetition;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Policy governing iteration, recursion, and retry behaviour for an agent workflow.
 *
 * <p>Limits are enforced by a {@code RepetitionGovernor} in the tool-runtime layer.
 * A {@code maxIterations} of {@code 0} means unlimited iterations. A {@code maxRetries} of
 * {@code 0} means no retries (fail on first error unless strategy is NONE).
 *
 * @param policyId              unique identifier for this policy
 * @param maxIterations         maximum loop/planning iterations (0 = unlimited)
 * @param maxRecursionDepth     maximum recursive sub-agent depth
 * @param maxRetries            maximum retry attempts on failure
 * @param retryStrategy         retry delay strategy
 * @param retryDelay            base delay for FIXED_DELAY and EXPONENTIAL_BACKOFF strategies
 * @param terminationConditions set of conditions that end repetition (at least one required)
 * @param confidenceThreshold   minimum confidence for {@link TerminationCondition#ON_CONFIDENCE_THRESHOLD} (0–1)
 *
 * @doc.type record
 * @doc.purpose Iteration/recursion/retry boundary contract
 * @doc.layer platform
 * @doc.pattern Policy
 */
public record RepetitionPolicy(
        @NotNull String policyId,
        int maxIterations,
        int maxRecursionDepth,
        int maxRetries,
        @NotNull RetryStrategy retryStrategy,
        @NotNull Duration retryDelay,
        @NotNull Set<TerminationCondition> terminationConditions,
        double confidenceThreshold) {

    public RepetitionPolicy {
        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(retryStrategy, "retryStrategy");
        Objects.requireNonNull(retryDelay, "retryDelay");
        Objects.requireNonNull(terminationConditions, "terminationConditions");
        if (terminationConditions.isEmpty()) {
            throw new IllegalArgumentException("terminationConditions must not be empty");
        }
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be >= 0 (0 = unlimited)");
        }
        if (maxRecursionDepth < 1) {
            throw new IllegalArgumentException("maxRecursionDepth must be >= 1");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("confidenceThreshold must be in [0, 1]");
        }
        terminationConditions = Set.copyOf(terminationConditions);
    }

    /** Sensible default: one iteration, no retries, terminates on success or error. */
    @NotNull
    public static RepetitionPolicy singleShot(@NotNull String policyId) {
        return new RepetitionPolicy(policyId, 1, 1, 0,
                RetryStrategy.NONE, Duration.ZERO,
                Set.of(TerminationCondition.ON_SUCCESS, TerminationCondition.ON_ERROR),
                0.0);
    }

    /** Policy allowing up to {@code iterations} loop turns with an exponential-backoff retry. */
    @NotNull
    public static RepetitionPolicy iterative(
            @NotNull String policyId, int iterations, int retries, @NotNull Duration retryDelay) {
        return new RepetitionPolicy(policyId, iterations, 5, retries,
                RetryStrategy.EXPONENTIAL_BACKOFF, retryDelay,
                EnumSet.of(TerminationCondition.ON_SUCCESS,
                        TerminationCondition.ON_MAX_ITERATIONS,
                        TerminationCondition.ON_ERROR),
                0.0);
    }
}
