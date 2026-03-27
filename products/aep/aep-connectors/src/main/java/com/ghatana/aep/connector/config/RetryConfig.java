/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.config;

import java.time.Duration;

/**
 * Shared retry configuration used across all connector types.
 *
 * @doc.type record
 * @doc.purpose Shared retry configuration for connector resilience
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public record RetryConfig(
        int maxAttempts,
        Duration initialDelay,
        double backoffMultiplier,
        Duration maxDelay
) {
    /** No retry — fail immediately on first error. */
    public static final RetryConfig NO_RETRY =
        new RetryConfig(1, Duration.ZERO, 1.0, Duration.ZERO);

    /** Default retry policy: 3 attempts, 100ms initial delay, 2× backoff, max 30s. */
    public static final RetryConfig DEFAULT =
        new RetryConfig(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(30));

    public RetryConfig {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (backoffMultiplier < 1.0) throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private double backoffMultiplier = 2.0;
        private Duration maxDelay = Duration.ofSeconds(30);

        public Builder maxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; return this; }
        public Builder initialDelay(Duration d) { this.initialDelay = d; return this; }
        public Builder backoffMultiplier(double m) { this.backoffMultiplier = m; return this; }
        public Builder maxDelay(Duration d) { this.maxDelay = d; return this; }

        public RetryConfig build() {
            return new RetryConfig(maxAttempts, initialDelay, backoffMultiplier, maxDelay);
        }
    }
}
