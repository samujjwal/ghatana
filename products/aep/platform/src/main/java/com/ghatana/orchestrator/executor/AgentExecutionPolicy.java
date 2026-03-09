/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import java.time.Duration;

/**
 * Day 40: Configuration for agent step execution policies.
 * Defines timeout, retry, and backoff strategies for reliable agent execution.
 */
public class AgentExecutionPolicy {

    private final Duration stepTimeout;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double backoffMultiplier;
    private final boolean jitterEnabled;
    private final Duration deadLetterDelay;
    private final boolean emitResultEvents;
    private final int concurrentExecutions;

    private AgentExecutionPolicy(Builder builder) {
        this.stepTimeout = builder.stepTimeout;
        this.maxRetries = builder.maxRetries;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.jitterEnabled = builder.jitterEnabled;
        this.deadLetterDelay = builder.deadLetterDelay;
        this.emitResultEvents = builder.emitResultEvents;
        this.concurrentExecutions = builder.concurrentExecutions;
    }

    // Getters
    public Duration getStepTimeout() {
        return stepTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    public Duration getDeadLetterDelay() {
        return deadLetterDelay;
    }

    public boolean isEmitResultEvents() {
        return emitResultEvents;
    }

    public int getConcurrentExecutions() {
        return concurrentExecutions;
    }

    /**
     * Calculate backoff duration for retry attempt.
     */
    public Duration calculateBackoff(int retryAttempt) {
        if (retryAttempt <= 0) {
            return initialBackoff;
        }

        // Exponential backoff with multiplier
        long backoffMs = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, retryAttempt - 1));
        Duration backoff = Duration.ofMillis(Math.min(backoffMs, maxBackoff.toMillis()));

        // Add jitter if enabled (±25% random variation)
        if (jitterEnabled) {
            double jitter = 0.5 + (Math.random() * 0.5); // 0.5 to 1.0
            backoff = Duration.ofMillis((long) (backoff.toMillis() * jitter));
        }

        return backoff;
    }

    /**
     * Check if retry should be attempted.
     */
    public boolean shouldRetry(int currentAttempt) {
        // Allow retries up to and including maxRetries so total attempts = maxRetries + 1
        return currentAttempt <= maxRetries;
    }

    /**
     * Default policy for general agent steps.
     */
    public static AgentExecutionPolicy defaultPolicy() {
        return builder()
                .stepTimeout(Duration.ofMinutes(10))
                .maxRetries(3)
                .initialBackoff(Duration.ofSeconds(1))
                .maxBackoff(Duration.ofMinutes(5))
                .backoffMultiplier(2.0)
                .jitterEnabled(true)
                .deadLetterDelay(Duration.ofMinutes(30))
                .emitResultEvents(true)
                .concurrentExecutions(5)
                .build();
    }

    /**
     * Policy for long-running agent steps.
     */
    public static AgentExecutionPolicy longRunningPolicy() {
        return builder()
                .stepTimeout(Duration.ofHours(1))
                .maxRetries(2)
                .initialBackoff(Duration.ofMinutes(1))
                .maxBackoff(Duration.ofMinutes(15))
                .backoffMultiplier(1.5)
                .jitterEnabled(true)
                .deadLetterDelay(Duration.ofHours(2))
                .emitResultEvents(true)
                .concurrentExecutions(2)
                .build();
    }

    /**
     * Policy for fast, lightweight agent steps.
     */
    public static AgentExecutionPolicy fastPolicy() {
        return builder()
                .stepTimeout(Duration.ofMinutes(2))
                .maxRetries(5)
                .initialBackoff(Duration.ofSeconds(1))
                .maxBackoff(Duration.ofMinutes(1))
                .backoffMultiplier(1.5)
                .jitterEnabled(true)
                .deadLetterDelay(Duration.ofMinutes(10))
                .emitResultEvents(true)
                .concurrentExecutions(10)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration stepTimeout = Duration.ofMinutes(10);
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private double backoffMultiplier = 2.0;
        private boolean jitterEnabled = true;
        private Duration deadLetterDelay = Duration.ofMinutes(30);
        private boolean emitResultEvents = true;
        private int concurrentExecutions = 5;

        public Builder stepTimeout(Duration stepTimeout) {
            this.stepTimeout = stepTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder jitterEnabled(boolean jitterEnabled) {
            this.jitterEnabled = jitterEnabled;
            return this;
        }

        public Builder deadLetterDelay(Duration deadLetterDelay) {
            this.deadLetterDelay = deadLetterDelay;
            return this;
        }

        public Builder emitResultEvents(boolean emitResultEvents) {
            this.emitResultEvents = emitResultEvents;
            return this;
        }

        public Builder concurrentExecutions(int concurrentExecutions) {
            this.concurrentExecutions = concurrentExecutions;
            return this;
        }

        public AgentExecutionPolicy build() {
            return new AgentExecutionPolicy(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "AgentExecutionPolicy{timeout=%s, maxRetries=%d, initialBackoff=%s, maxBackoff=%s, " +
            "multiplier=%.1f, jitter=%s, dlDelay=%s, emitEvents=%s, concurrent=%d}",
            stepTimeout, maxRetries, initialBackoff, maxBackoff, backoffMultiplier,
            jitterEnabled, deadLetterDelay, emitResultEvents, concurrentExecutions
        );
    }
}