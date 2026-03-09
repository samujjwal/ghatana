/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import java.time.Duration;
import java.util.Objects;

/**
 * Pipeline-level error handling configuration.
 *
 * <p>Defines the error handling strategy for a pipeline or stage, including:
 * <ul>
 *   <li><b>Strategy</b> — How failures are handled (fail-fast, retry, dead-letter, continue)</li>
 *   <li><b>Retry settings</b> — Max retries and retry delay</li>
 *   <li><b>DLQ routing</b> — Dead letter queue ID for unrecoverable failures</li>
 *   <li><b>Timeout</b> — Maximum processing time per event</li>
 * </ul>
 *
 * <p>Immutable; use factory methods for common patterns:
 * <pre>
 * ErrorHandlingConfig.failFast()
 * ErrorHandlingConfig.retry(3, Duration.ofSeconds(1))
 * ErrorHandlingConfig.deadLetter("fraud-dlq")
 * ErrorHandlingConfig.continueOnError()
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Configuration for error handling and recovery strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class ErrorHandlingConfig {

    public enum ErrorStrategy {
        /** Propagate failure immediately; abort pipeline execution. */
        FAIL_FAST,
        /** Retry the failed operation with configurable backoff. */
        RETRY,
        /** Route failed events to a dead letter queue. */
        DEAD_LETTER,
        /** Log the failure and continue processing the next event. */
        CONTINUE
    }

    private final ErrorStrategy strategy;
    private final int maxRetries;
    private final Duration retryDelay;
    private final String deadLetterQueueId;
    private final boolean continueOnError;
    private final Duration timeout;

    private ErrorHandlingConfig(Builder builder) {
        this.strategy = builder.strategy;
        this.maxRetries = builder.maxRetries;
        this.retryDelay = builder.retryDelay;
        this.deadLetterQueueId = builder.deadLetterQueueId;
        this.continueOnError = builder.continueOnError;
        this.timeout = builder.timeout;
    }

    // ──── Factory methods ──────────────────────────────────────────────

    public static ErrorHandlingConfig failFast() {
        return builder().strategy(ErrorStrategy.FAIL_FAST).build();
    }

    public static ErrorHandlingConfig retry(int maxRetries) {
        return retry(maxRetries, Duration.ofSeconds(1));
    }

    public static ErrorHandlingConfig retry(int maxRetries, Duration retryDelay) {
        return builder()
                .strategy(ErrorStrategy.RETRY)
                .maxRetries(maxRetries)
                .retryDelay(retryDelay)
                .build();
    }

    public static ErrorHandlingConfig deadLetter(String queueId) {
        return builder()
                .strategy(ErrorStrategy.DEAD_LETTER)
                .deadLetterQueueId(queueId)
                .build();
    }

    public static ErrorHandlingConfig continueOnError() {
        return builder()
                .strategy(ErrorStrategy.CONTINUE)
                .continueOnError(true)
                .build();
    }

    // ──── Accessors ────────────────────────────────────────────────────

    public ErrorStrategy getStrategy() { return strategy; }
    public int getMaxRetries() { return maxRetries; }
    public Duration getRetryDelay() { return retryDelay; }
    public String getDeadLetterQueueId() { return deadLetterQueueId; }
    public boolean isContinueOnError() { return continueOnError; }
    public Duration getTimeout() { return timeout; }

    /** Whether events should be routed to DLQ on unrecoverable failure. */
    public boolean hasDlq() {
        return deadLetterQueueId != null && !deadLetterQueueId.isEmpty();
    }

    @Override
    public String toString() {
        return "ErrorHandlingConfig{strategy=" + strategy +
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                (deadLetterQueueId != null ? ", dlq=" + deadLetterQueueId : "") +
                (timeout != null ? ", timeout=" + timeout : "") +
                '}';
    }

    // ──── Builder ──────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ErrorStrategy strategy = ErrorStrategy.FAIL_FAST;
        private int maxRetries = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
        private String deadLetterQueueId;
        private boolean continueOnError = false;
        private Duration timeout;

        public Builder strategy(ErrorStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy);
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder deadLetterQueueId(String id) {
            this.deadLetterQueueId = id;
            return this;
        }

        public Builder continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ErrorHandlingConfig build() {
            return new ErrorHandlingConfig(this);
        }
    }
}
