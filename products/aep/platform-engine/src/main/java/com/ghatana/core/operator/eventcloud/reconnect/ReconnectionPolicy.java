package com.ghatana.core.operator.eventcloud.reconnect;

import java.time.Duration;

/**
 * Policy for handling reconnection attempts with backoff strategies.
 * <p>
 * Defines how the EventCloudTailOperator should retry connections after failures,
 * including backoff duration calculation and retry limits.
 * </p>
 *
 * @since 2.0
 
 *
 * @doc.type interface
 * @doc.purpose Reconnection policy
 * @doc.layer platform
 * @doc.pattern Interface
*/
public interface ReconnectionPolicy {

    /**
     * Determines whether a retry should be attempted.
     *
     * @param failureCount Number of consecutive failures
     * @param lastFailureTime Time of the last failure in milliseconds since epoch
     * @return true if a retry should be attempted, false if max retries exceeded
     */
    boolean shouldRetry(int failureCount, long lastFailureTime);

    /**
     * Calculates the backoff duration for the given failure count.
     *
     * @param failureCount Number of consecutive failures (0-based)
     * @return Duration to wait before retrying
     */
    Duration getBackoffDuration(int failureCount);

    /**
     * Resets the policy state (e.g., after successful reconnection).
     */
    void reset();

    /**
     * Gets the name of this reconnection policy.
     *
     * @return Policy name
     */
    String getName();

    /**
     * Gets the maximum number of retries allowed.
     *
     * @return Maximum retry count
     */
    int getMaxRetries();
}
