package com.ghatana.pipeline.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * Retry configuration for failed pipeline operations.
 *
 * <p>Defines exponential backoff strategy with configurable parameters
 * for retrying failed operations.
 *
 * @doc.type class
 * @doc.purpose Retry configuration with exponential backoff
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryConfig {

    /**
     * Maximum number of retry attempts.
     */
    @Builder.Default
    private int maxAttempts = 3;

    /**
     * Initial backoff duration before first retry.
     */
    @Builder.Default
    private Duration initialBackoff = Duration.ofMillis(100);

    /**
     * Maximum backoff duration between retries.
     */
    @Builder.Default
    private Duration maxBackoff = Duration.ofSeconds(30);

    /**
     * Backoff multiplier for exponential backoff.
     */
    @Builder.Default
    private double backoffMultiplier = 2.0;

    /**
     * Calculate backoff duration for a given attempt number.
     *
     * @param attemptNumber the attempt number (1-based)
     * @return the backoff duration
     */
    public Duration calculateBackoff(int attemptNumber) {
        if (attemptNumber <= 1) {
            return initialBackoff;
        }

        long backoffMs = (long) (initialBackoff.toMillis() *
                                Math.pow(backoffMultiplier, attemptNumber - 1));

        return Duration.ofMillis(Math.min(backoffMs, maxBackoff.toMillis()));
    }

    /**
     * Check if retries should be attempted for the given attempt number.
     *
     * @param attemptNumber the attempt number (1-based)
     * @return true if more retries should be attempted
     */
    public boolean shouldRetry(int attemptNumber) {
        return attemptNumber < maxAttempts;
    }
}

