package com.ghatana.core.operator.eventcloud;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracks the state of reconnection attempts for EventCloud tail operator.
 * <p>
 * Maintains counters for attempts, failures, and timing information
 * to support reconnection logic and alerting.
 * </p>
 *
 * @since 2.0
 */
public final class ReconnectionState {

    private int attemptCount;
    private int consecutiveFailures;
    private Instant lastAttemptTime;
    private Instant lastSuccessTime;
    private Throwable lastError;
    private boolean connected;

    public ReconnectionState() {
        this.attemptCount = 0;
        this.consecutiveFailures = 0;
        this.lastAttemptTime = null;
        this.lastSuccessTime = null;
        this.lastError = null;
        this.connected = false;
    }

    /**
     * Records a reconnection attempt.
     */
    public void recordAttempt() {
        this.attemptCount++;
        this.lastAttemptTime = Instant.now();
    }

    /**
     * Records a successful connection.
     */
    public void recordSuccess() {
        this.connected = true;
        this.consecutiveFailures = 0;
        this.lastSuccessTime = Instant.now();
        this.lastError = null;
    }

    /**
     * Records a failed connection attempt.
     *
     * @param error The error that caused the failure
     */
    public void recordFailure(Throwable error) {
        Objects.requireNonNull(error, "Error must not be null");
        this.connected = false;
        this.consecutiveFailures++;
        this.lastError = error;
    }

    /**
     * Resets the state for a fresh reconnection cycle.
     */
    public void reset() {
        this.attemptCount = 0;
        this.consecutiveFailures = 0;
        this.lastAttemptTime = null;
        this.lastError = null;
        // Keep lastSuccessTime and connected status
    }

    /**
     * Gets the total number of reconnection attempts.
     *
     * @return Attempt count
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Gets the number of consecutive failures since last success.
     *
     * @return Consecutive failure count
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Gets the time of the last reconnection attempt.
     *
     * @return Last attempt time, or empty if no attempts
     */
    public Optional<Instant> getLastAttemptTime() {
        return Optional.ofNullable(lastAttemptTime);
    }

    /**
     * Gets the time of the last successful connection.
     *
     * @return Last success time, or empty if never connected
     */
    public Optional<Instant> getLastSuccessTime() {
        return Optional.ofNullable(lastSuccessTime);
    }

    /**
     * Gets the last error that caused a connection failure.
     *
     * @return Last error, or empty if no failures
     */
    public Optional<Throwable> getLastError() {
        return Optional.ofNullable(lastError);
    }

    /**
     * Checks if currently connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Checks if the failure threshold is exceeded.
     * <p>
     * Used for alerting when reconnection attempts repeatedly fail.
     * </p>
     *
     * @param threshold The failure threshold
     * @return true if consecutive failures >= threshold
     */
    public boolean isFailureThresholdExceeded(int threshold) {
        return consecutiveFailures >= threshold;
    }

    @Override
    public String toString() {
        return "ReconnectionState{" +
                "attempts=" + attemptCount +
                ", consecutiveFailures=" + consecutiveFailures +
                ", connected=" + connected +
                ", lastAttempt=" + lastAttemptTime +
                ", lastSuccess=" + lastSuccessTime +
                '}';
    }
}

