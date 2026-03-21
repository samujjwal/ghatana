package com.ghatana.core.operator.eventcloud.reconnect;

import java.time.Duration;
import java.util.Objects;

/**
 * Exponential backoff reconnection policy.
 * <p>
 * Implements exponential backoff with configurable initial delay, multiplier, and maximum delay.
 * Each retry attempt increases the backoff duration exponentially until reaching the maximum.
 * </p>
 *
 * <b>Example Backoff Sequence (default config):</b>
 * <ul>
 *   <li>Retry 1: 100ms</li>
 *   <li>Retry 2: 200ms</li>
 *   <li>Retry 3: 400ms</li>
 *   <li>Retry 4: 800ms</li>
 *   <li>Retry 5: 1600ms (1.6s)</li>
 *   <li>Retry 6: 3200ms (3.2s)</li>
 *   <li>Retry 7: 6400ms (6.4s)</li>
 *   <li>Retry 8: 12800ms (12.8s)</li>
 *   <li>Retry 9: 25600ms (25.6s)</li>
 *   <li>Retry 10: 30000ms (30s max)</li>
 * </ul>
 *
 * @since 2.0
 */
public final class ExponentialBackoffPolicy implements ReconnectionPolicy {

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final int maxRetries;
    private final String name;

    /**
     * Creates an exponential backoff policy with default settings.
     * <ul>
     *   <li>Initial delay: 100ms</li>
     *   <li>Max delay: 30 seconds</li>
     *   <li>Multiplier: 2.0x</li>
     *   <li>Max retries: 10</li>
     * </ul>
     */
    public ExponentialBackoffPolicy() {
        this(100, 30_000, 2.0, 10);
    }

    /**
     * Creates an exponential backoff policy with custom settings.
     *
     * @param initialDelayMs Initial backoff delay in milliseconds (must be > 0)
     * @param maxDelayMs Maximum backoff delay in milliseconds (must be >= initialDelayMs)
     * @param multiplier Backoff multiplier per retry (must be > 1.0)
     * @param maxRetries Maximum number of retries (must be > 0)
     */
    public ExponentialBackoffPolicy(long initialDelayMs, long maxDelayMs, double multiplier, int maxRetries) {
        if (initialDelayMs <= 0) {
            throw new IllegalArgumentException("Initial delay must be > 0");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("Max delay must be >= initial delay");
        }
        if (multiplier <= 1.0) {
            throw new IllegalArgumentException("Multiplier must be > 1.0");
        }
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("Max retries must be > 0");
        }

        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.maxRetries = maxRetries;
        this.name = String.format("ExponentialBackoff(init=%dms, max=%dms, mult=%.1f, retries=%d)",
                initialDelayMs, maxDelayMs, multiplier, maxRetries);
    }

    @Override
    public boolean shouldRetry(int failureCount, long lastFailureTime) {
        return failureCount < maxRetries;
    }

    @Override
    public Duration getBackoffDuration(int failureCount) {
        if (failureCount < 0) {
            return Duration.ZERO;
        }

        // Calculate exponential backoff: initialDelay * (multiplier ^ failureCount)
        long delayMs = Math.round(initialDelayMs * Math.pow(multiplier, failureCount));

        // Cap at maximum delay
        delayMs = Math.min(delayMs, maxDelayMs);

        return Duration.ofMillis(delayMs);
    }

    @Override
    public void reset() {
        // No state to reset for exponential backoff
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Gets the initial backoff delay.
     *
     * @return Initial delay in milliseconds
     */
    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    /**
     * Gets the maximum backoff delay.
     *
     * @return Maximum delay in milliseconds
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    /**
     * Gets the backoff multiplier.
     *
     * @return Multiplier value
     */
    public double getMultiplier() {
        return multiplier;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExponentialBackoffPolicy that)) return false;
        return initialDelayMs == that.initialDelayMs &&
                maxDelayMs == that.maxDelayMs &&
                Double.compare(that.multiplier, multiplier) == 0 &&
                maxRetries == that.maxRetries;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialDelayMs, maxDelayMs, multiplier, maxRetries);
    }
}
