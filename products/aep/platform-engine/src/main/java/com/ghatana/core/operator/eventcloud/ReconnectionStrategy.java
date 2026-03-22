package com.ghatana.core.operator.eventcloud;

import java.time.Duration;
import java.util.Objects;

/**
 * Strategy for reconnecting to EventCloud after connection failures.
 * <p>
 * Implements exponential backoff with configurable parameters to avoid
 * overwhelming the EventCloud service during temporary outages.
 * </p>
 *
 * @since 2.0
 */
public final class ReconnectionStrategy {

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final int maxAttempts;
    private final boolean jitter;

    private ReconnectionStrategy(Builder builder) {
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.multiplier = builder.multiplier;
        this.maxAttempts = builder.maxAttempts;
        this.jitter = builder.jitter;
    }

    /**
     * Creates a default reconnection strategy with exponential backoff.
     * <p>
     * Defaults:
     * <ul>
     *   <li>Initial delay: 1 second</li>
     *   <li>Max delay: 60 seconds</li>
     *   <li>Multiplier: 2.0 (exponential)</li>
     *   <li>Max attempts: 10</li>
     *   <li>Jitter: enabled</li>
     * </ul>
     *
     * @return Default strategy
     */
    public static ReconnectionStrategy defaultStrategy() {
        return builder().build();
    }

    /**
     * Creates a reconnection strategy with no retry (fail fast).
     *
     * @return No-retry strategy
     */
    public static ReconnectionStrategy noRetry() {
        return builder()
                .maxAttempts(1)
                .build();
    }

    /**
     * Creates a reconnection strategy with unlimited retries.
     *
     * @return Unlimited retry strategy
     */
    public static ReconnectionStrategy unlimitedRetry() {
        return builder()
                .maxAttempts(Integer.MAX_VALUE)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Calculates the delay for a given retry attempt.
     *
     * @param attempt The attempt number (0-based)
     * @return The delay duration
     */
    public Duration calculateDelay(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("Attempt must be >= 0");
        }

        // Calculate exponential delay
        long delayMillis = (long) (initialDelay.toMillis() * Math.pow(multiplier, attempt));

        // Cap at max delay
        delayMillis = Math.min(delayMillis, maxDelay.toMillis());

        // Add jitter if enabled (±25%)
        if (jitter) {
            double jitterFactor = 0.75 + (Math.random() * 0.5); // 0.75 to 1.25
            delayMillis = (long) (delayMillis * jitterFactor);
        }

        return Duration.ofMillis(delayMillis);
    }

    /**
     * Checks if another retry attempt should be made.
     *
     * @param attempt The current attempt number (0-based)
     * @return true if should retry
     */
    public boolean shouldRetry(int attempt) {
        return attempt < maxAttempts;
    }

    /**
     * Gets the maximum number of retry attempts.
     *
     * @return Max attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public boolean isJitterEnabled() {
        return jitter;
    }

    @Override
    public String toString() {
        return "ReconnectionStrategy{" +
                "initialDelay=" + initialDelay +
                ", maxDelay=" + maxDelay +
                ", multiplier=" + multiplier +
                ", maxAttempts=" + maxAttempts +
                ", jitter=" + jitter +
                '}';
    }

    public static final class Builder {
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(60);
        private double multiplier = 2.0;
        private int maxAttempts = 10;
        private boolean jitter = true;

        private Builder() {
        }

        /**
         * Sets the initial delay before the first retry.
         *
         * @param initialDelay Initial delay
         * @return This builder
         */
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = Objects.requireNonNull(initialDelay, "Initial delay must not be null");
            if (initialDelay.isNegative() || initialDelay.isZero()) {
                throw new IllegalArgumentException("Initial delay must be positive");
            }
            return this;
        }

        /**
         * Sets the maximum delay between retries.
         *
         * @param maxDelay Maximum delay
         * @return This builder
         */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = Objects.requireNonNull(maxDelay, "Max delay must not be null");
            if (maxDelay.isNegative() || maxDelay.isZero()) {
                throw new IllegalArgumentException("Max delay must be positive");
            }
            return this;
        }

        /**
         * Sets the backoff multiplier for exponential backoff.
         *
         * @param multiplier Backoff multiplier (> 1.0 for exponential)
         * @return This builder
         */
        public Builder multiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("Multiplier must be >= 1.0");
            }
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxAttempts Maximum attempts (1 = no retries)
         * @return This builder
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("Max attempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Enables or disables jitter in delay calculations.
         * <p>
         * Jitter adds randomness (±25%) to prevent thundering herd.
         * </p>
         *
         * @param jitter Whether to enable jitter
         * @return This builder
         */
        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public ReconnectionStrategy build() {
            if (initialDelay.compareTo(maxDelay) > 0) {
                throw new IllegalStateException("Initial delay cannot be greater than max delay");
            }
            return new ReconnectionStrategy(this);
        }
    }
}

