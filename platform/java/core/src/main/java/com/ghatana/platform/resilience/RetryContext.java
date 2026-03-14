/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import java.util.Objects;

/**
 * Immutable snapshot of retry execution context for K-18 context propagation.
 *
 * <p>A {@code RetryContext} instance is associated with each attempt of a
 * {@link RetryPolicy} execution. It carries metadata that consumers (loggers,
 * metrics, upstream services) can inspect to understand the retry state.
 *
 * <p>Usage:
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build();
 * policy.executeWithContext(eventloop, attempt -> {
 *     log.info("attempt={}, isRetry={}", attempt.getAttemptNumber(), attempt.isRetry());
 *     return callRemoteService();
 * });
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Retry execution metadata for K-18 context propagation
 * @doc.layer platform
 * @doc.pattern ValueObject
 *
 * @since 2.0.0
 */
public final class RetryContext {

    private final int attemptNumber;    // 1-based (first attempt = 1)
    private final int maxAttempts;
    private final boolean isRetry;
    private final Throwable lastError;  // null on first attempt

    private RetryContext(int attemptNumber, int maxAttempts, Throwable lastError) {
        if (attemptNumber < 1) throw new IllegalArgumentException("attemptNumber must be >= 1");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.attemptNumber = attemptNumber;
        this.maxAttempts = maxAttempts;
        this.isRetry = attemptNumber > 1;
        this.lastError = lastError;
    }

    /**
     * Creates context for the first attempt (no prior error).
     *
     * @param maxAttempts total allowed attempts
     * @return context for attempt 1
     */
    public static RetryContext first(int maxAttempts) {
        return new RetryContext(1, maxAttempts, null);
    }

    /**
     * Creates context for a retry attempt.
     *
     * @param attemptNumber 1-based attempt (>= 2 for retries)
     * @param maxAttempts   total allowed attempts
     * @param lastError     error that triggered this retry (non-null)
     * @return context for subsequent attempt
     */
    public static RetryContext retry(int attemptNumber, int maxAttempts, Throwable lastError) {
        Objects.requireNonNull(lastError, "lastError must be non-null for retry context");
        return new RetryContext(attemptNumber, maxAttempts, lastError);
    }

    /**
     * 1-based attempt number. First attempt = 1, first retry = 2, etc.
     * Includes in log context as {@code retry.attempt}.
     */
    public int getAttemptNumber() { return attemptNumber; }

    /**
     * Total allowed attempts (including first). A value of 3 means: 1 try + 2 retries.
     */
    public int getMaxAttempts() { return maxAttempts; }

    /**
     * True if this is a retry (attemptNumber > 1). False for the initial attempt.
     * Useful for idempotency checks in consumers.
     */
    public boolean isRetry() { return isRetry; }

    /**
     * Attempts remaining (maxAttempts - attemptNumber).
     */
    public int attemptsRemaining() { return maxAttempts - attemptNumber; }

    /**
     * True when this is the last allowed attempt.
     */
    public boolean isLastAttempt() { return attemptNumber >= maxAttempts; }

    /**
     * Error from the previous attempt, or {@code null} on the first attempt.
     */
    public Throwable getLastError() { return lastError; }

    @Override
    public String toString() {
        return "RetryContext{attempt=" + attemptNumber + "/" + maxAttempts +
               ", isRetry=" + isRetry + ", lastError=" +
               (lastError == null ? "null" : lastError.getClass().getSimpleName()) + "}";
    }
}
