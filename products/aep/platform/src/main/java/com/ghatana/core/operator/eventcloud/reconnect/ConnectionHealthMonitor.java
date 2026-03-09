package com.ghatana.core.operator.eventcloud.reconnect;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors the health and state of EventCloud connections.
 * <p>
 * Tracks connection state transitions, failure counts, and emits health events.
 * Thread-safe implementation using atomic variables.
 * </p>
 *
 * @since 2.0
 */
public final class ConnectionHealthMonitor {

    /**
     * Connection state enumeration.
     
 *
 * @doc.type enum
 * @doc.purpose Connection state
 * @doc.layer platform
 * @doc.pattern Enumeration
*/
    public enum ConnectionState {
        DISCONNECTED(0, "Disconnected"),
        CONNECTING(1, "Connecting"),
        CONNECTED(2, "Connected"),
        RECONNECTING(3, "Reconnecting"),
        FAILED(4, "Failed");

        private final int code;
        private final String displayName;

        ConnectionState(int code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private volatile Throwable lastError;

    /**
     * Creates a new connection health monitor.
     */
    public ConnectionHealthMonitor() {
    }

    /**
     * Records a successful connection.
     */
    public synchronized void recordSuccess() {
        state.set(ConnectionState.CONNECTED);
        consecutiveFailures.set(0);
        lastSuccessTime.set(System.currentTimeMillis());
        totalSuccesses.incrementAndGet();
        lastError = null;
    }

    /**
     * Records a connection failure.
     *
     * @param error The error that caused the failure
     */
    public synchronized void recordFailure(Throwable error) {
        state.set(ConnectionState.RECONNECTING);
        consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        totalFailures.incrementAndGet();
        lastError = error;
    }

    /**
     * Marks the connection as attempting to connect.
     */
    public void recordConnecting() {
        state.set(ConnectionState.CONNECTING);
    }

    /**
     * Marks the connection as disconnected.
     */
    public void recordDisconnected() {
        state.set(ConnectionState.DISCONNECTED);
    }

    /**
     * Marks the connection as permanently failed.
     */
    public void recordFailed() {
        state.set(ConnectionState.FAILED);
    }

    /**
     * Gets the current connection state.
     *
     * @return Current state
     */
    public ConnectionState getState() {
        return state.get();
    }

    /**
     * Gets the number of consecutive failures.
     *
     * @return Consecutive failure count
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Gets the time of the last failure.
     *
     * @return Timestamp in milliseconds since epoch, or 0 if no failures
     */
    public long getLastFailureTime() {
        return lastFailureTime.get();
    }

    /**
     * Gets the time of the last successful connection.
     *
     * @return Timestamp in milliseconds since epoch, or 0 if never connected
     */
    public long getLastSuccessTime() {
        return lastSuccessTime.get();
    }

    /**
     * Gets the total number of failures since creation.
     *
     * @return Total failure count
     */
    public long getTotalFailures() {
        return totalFailures.get();
    }

    /**
     * Gets the total number of successful connections since creation.
     *
     * @return Total success count
     */
    public long getTotalSuccesses() {
        return totalSuccesses.get();
    }

    /**
     * Gets the last error that occurred.
     *
     * @return Last error, or null if none
     */
    public Throwable getLastError() {
        return lastError;
    }

    /**
     * Checks if the connection is currently healthy (connected).
     *
     * @return true if state is CONNECTED
     */
    public boolean isHealthy() {
        return state.get() == ConnectionState.CONNECTED;
    }

    /**
     * Checks if the connection is currently attempting to reconnect.
     *
     * @return true if state is RECONNECTING or CONNECTING
     */
    public boolean isReconnecting() {
        ConnectionState current = state.get();
        return current == ConnectionState.RECONNECTING || current == ConnectionState.CONNECTING;
    }

    /**
     * Checks if the connection has permanently failed.
     *
     * @return true if state is FAILED
     */
    public boolean hasFailed() {
        return state.get() == ConnectionState.FAILED;
    }

    /**
     * Gets the uptime since the last successful connection.
     *
     * @return Uptime in milliseconds, or 0 if never connected
     */
    public long getUptimeMs() {
        long lastSuccess = lastSuccessTime.get();
        if (lastSuccess == 0) {
            return 0;
        }
        return System.currentTimeMillis() - lastSuccess;
    }

    /**
     * Gets the time since the last failure.
     *
     * @return Time in milliseconds since last failure, or Long.MAX_VALUE if no failures
     */
    public long getTimeSinceLastFailureMs() {
        long lastFailure = lastFailureTime.get();
        if (lastFailure == 0) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - lastFailure;
    }

    /**
     * Resets all monitoring state.
     */
    public synchronized void reset() {
        state.set(ConnectionState.DISCONNECTED);
        consecutiveFailures.set(0);
        lastFailureTime.set(0);
        lastSuccessTime.set(0);
        totalFailures.set(0);
        totalSuccesses.set(0);
        lastError = null;
    }

    /**
     * Gets a snapshot of the current health status.
     *
     * @return Health status snapshot
     */
    public HealthSnapshot getSnapshot() {
        return new HealthSnapshot(
                state.get(),
                consecutiveFailures.get(),
                lastFailureTime.get(),
                lastSuccessTime.get(),
                totalFailures.get(),
                totalSuccesses.get(),
                lastError
        );
    }

    /**
     * Immutable snapshot of connection health status.
     */
    public static final class HealthSnapshot {
        private final ConnectionState state;
        private final int consecutiveFailures;
        private final long lastFailureTime;
        private final long lastSuccessTime;
        private final long totalFailures;
        private final long totalSuccesses;
        private final Throwable lastError;

        public HealthSnapshot(
                ConnectionState state,
                int consecutiveFailures,
                long lastFailureTime,
                long lastSuccessTime,
                long totalFailures,
                long totalSuccesses,
                Throwable lastError
        ) {
            this.state = Objects.requireNonNull(state);
            this.consecutiveFailures = consecutiveFailures;
            this.lastFailureTime = lastFailureTime;
            this.lastSuccessTime = lastSuccessTime;
            this.totalFailures = totalFailures;
            this.totalSuccesses = totalSuccesses;
            this.lastError = lastError;
        }

        public ConnectionState getState() {
            return state;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public long getLastFailureTime() {
            return lastFailureTime;
        }

        public long getLastSuccessTime() {
            return lastSuccessTime;
        }

        public long getTotalFailures() {
            return totalFailures;
        }

        public long getTotalSuccesses() {
            return totalSuccesses;
        }

        public Throwable getLastError() {
            return lastError;
        }

        public boolean isHealthy() {
            return state == ConnectionState.CONNECTED;
        }

        @Override
        public String toString() {
            return "HealthSnapshot{" +
                    "state=" + state +
                    ", failures=" + consecutiveFailures +
                    ", total=" + totalFailures +
                    ", successes=" + totalSuccesses +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ConnectionHealthMonitor{" +
                "state=" + state.get() +
                ", failures=" + consecutiveFailures.get() +
                ", lastFailure=" + Instant.ofEpochMilli(lastFailureTime.get()) +
                ", lastSuccess=" + Instant.ofEpochMilli(lastSuccessTime.get()) +
                '}';
    }
}
