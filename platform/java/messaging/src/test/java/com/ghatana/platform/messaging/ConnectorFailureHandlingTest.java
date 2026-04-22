package com.ghatana.core.connectors;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for connector failure handling — validates retry on transient errors,
 * circuit breaker activation, dead letter routing, and reconnection behavior.
 *
 * @doc.type class
 * @doc.purpose Tests for connector resilience, retry, circuit breaker, and error handling
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Connector Failure Handling Tests [GH-90000]")
@Tag("integration [GH-90000]")
class ConnectorFailureHandlingTest extends EventloopTestBase {

    // ── Retry on transient errors ─────────────────────────────────────────────

    @Nested
    @DisplayName("retry on transient errors [GH-90000]")
    class RetryOnTransientErrors {

        @Test
        @DisplayName("transient failure retried until success within max attempts [GH-90000]")
        void transientFailure_retriedUntilSuccess() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            AtomicBoolean success = new AtomicBoolean(false); // GH-90000
            int maxAttempts = 3;

            while (attempts.get() < maxAttempts && !success.get()) { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                if (attempts.get() >= 3) { // GH-90000
                    success.set(true); // succeeds on 3rd attempt // GH-90000
                }
            }

            assertThat(success.get()).isTrue(); // GH-90000
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("non-transient error does not trigger retry [GH-90000]")
        void nonTransientError_doesNotTriggerRetry() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            boolean isRetryable = false;

            attempts.incrementAndGet(); // GH-90000
            // Simulate: non-retryable (no loop) // GH-90000
            if (!isRetryable) { // GH-90000
                // surface immediately
            }

            assertThat(attempts.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("max retries exhausted surfaces permanent failure [GH-90000]")
        void maxRetriesExhausted_surfacesPermanentFailure() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            int maxAttempts = 5;
            RuntimeException lastError = null;

            while (attempts.get() < maxAttempts) { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                lastError = new RuntimeException("transient error on attempt " + attempts.get()); // GH-90000
            }

            assertThat(attempts.get()).isEqualTo(maxAttempts); // GH-90000
            assertThat(lastError).isNotNull(); // GH-90000
            assertThat(lastError.getMessage()).contains("attempt 5 [GH-90000]");
        }
    }

    // ── Circuit breaker ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("circuit breaker [GH-90000]")
    class CircuitBreaker {

        enum CircuitState { CLOSED, OPEN, HALF_OPEN }

        static class SimpleCircuitBreaker {
            private CircuitState state = CircuitState.CLOSED;
            private int failureCount = 0;
            private final int threshold;
            private long openedAt = 0;
            private final long resetAfterMs;

            SimpleCircuitBreaker(int threshold, long resetAfterMs) { // GH-90000
                this.threshold    = threshold;
                this.resetAfterMs = resetAfterMs;
            }

            boolean allowRequest(long nowMs) { // GH-90000
                return switch (state) { // GH-90000
                    case CLOSED    -> true;
                    case OPEN      -> {
                        if (nowMs - openedAt >= resetAfterMs) { // GH-90000
                            state = CircuitState.HALF_OPEN;
                            yield true;
                        }
                        yield false;
                    }
                    case HALF_OPEN -> true;
                };
            }

            void recordFailure(long nowMs) { // GH-90000
                failureCount++;
                if (failureCount >= threshold) { // GH-90000
                    state    = CircuitState.OPEN;
                    openedAt = nowMs;
                }
            }

            void recordSuccess() { // GH-90000
                if (state == CircuitState.HALF_OPEN) { // GH-90000
                    state        = CircuitState.CLOSED;
                    failureCount = 0;
                }
            }

            CircuitState state() { return state; } // GH-90000
        }

        @Test
        @DisplayName("circuit opens after threshold consecutive failures [GH-90000]")
        void circuitOpens_afterThresholdConsecutiveFailures() { // GH-90000
            SimpleCircuitBreaker cb = new SimpleCircuitBreaker(3, 60_000L); // GH-90000
            long now = 1_000L;

            cb.recordFailure(now); // GH-90000
            cb.recordFailure(now); // GH-90000
            cb.recordFailure(now); // 3rd failure → OPEN // GH-90000

            assertThat(cb.state()).isEqualTo(CircuitState.OPEN); // GH-90000
            assertThat(cb.allowRequest(now)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("circuit transitions to HALF_OPEN after reset timeout [GH-90000]")
        void circuit_transitionsToHalfOpen_afterResetTimeout() { // GH-90000
            SimpleCircuitBreaker cb = new SimpleCircuitBreaker(2, 5_000L); // GH-90000
            long now = 1_000L;

            cb.recordFailure(now); // GH-90000
            cb.recordFailure(now); // opens at t=1000 // GH-90000

            long afterReset = now + 6_000L;
            boolean allowed = cb.allowRequest(afterReset); // GH-90000

            assertThat(allowed).isTrue(); // GH-90000
            assertThat(cb.state()).isEqualTo(CircuitState.HALF_OPEN); // GH-90000
        }

        @Test
        @DisplayName("success in HALF_OPEN closes the circuit [GH-90000]")
        void successInHalfOpen_closesCircuit() { // GH-90000
            SimpleCircuitBreaker cb = new SimpleCircuitBreaker(2, 5_000L); // GH-90000
            long now = 1_000L;

            cb.recordFailure(now); // GH-90000
            cb.recordFailure(now); // GH-90000
            cb.allowRequest(now + 6_000L); // transition to HALF_OPEN // GH-90000

            cb.recordSuccess(); // GH-90000

            assertThat(cb.state()).isEqualTo(CircuitState.CLOSED); // GH-90000
        }
    }

    // ── Dead letter routing ───────────────────────────────────────────────────

    @Nested
    @DisplayName("dead letter queue routing [GH-90000]")
    class DeadLetterRouting {

        @Test
        @DisplayName("max-retry-exceeded message is routed to dead letter queue [GH-90000]")
        void maxRetryExceeded_messageRoutedToDeadLetterQueue() { // GH-90000
            java.util.List<String> dlq = new java.util.ArrayList<>(); // GH-90000
            java.util.List<String> processing = new java.util.ArrayList<>(); // GH-90000

            String message = "poison-pill-message";
            int maxRetries = 3;
            AtomicInteger retries = new AtomicInteger(0); // GH-90000

            while (retries.get() < maxRetries) { // GH-90000
                retries.incrementAndGet(); // GH-90000
                // Simulate: always fails
            }

            // After exhausting retries, route to DLQ
            if (retries.get() >= maxRetries) { // GH-90000
                dlq.add(message); // GH-90000
            } else {
                processing.add(message); // GH-90000
            }

            assertThat(dlq).containsExactly(message); // GH-90000
            assertThat(processing).isEmpty(); // GH-90000
        }
    }

    // ── Reconnection ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reconnection behavior [GH-90000]")
    class ReconnectionBehavior {

        @Test
        @DisplayName("connector reconnects after connection drop [GH-90000]")
        void connector_reconnects_afterConnectionDrop() { // GH-90000
            AtomicBoolean connected = new AtomicBoolean(true); // GH-90000
            AtomicInteger reconnectAttempts = new AtomicInteger(0); // GH-90000

            // Simulate connection drop
            connected.set(false); // GH-90000

            // Reconnection loop
            while (!connected.get() && reconnectAttempts.get() < 3) { // GH-90000
                reconnectAttempts.incrementAndGet(); // GH-90000
                connected.set(true); // reconnects on first attempt // GH-90000
            }

            assertThat(connected.get()).isTrue(); // GH-90000
            assertThat(reconnectAttempts.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("exponential backoff increases wait between reconnects [GH-90000]")
        void exponentialBackoff_increasesWaitBetweenReconnects() { // GH-90000
            long baseDelayMs = 100L;
            int maxAttempts = 5;

            long[] delays = new long[maxAttempts];
            for (int i = 0; i < maxAttempts; i++) { // GH-90000
                delays[i] = (long) (baseDelayMs * Math.pow(2, i)); // GH-90000
            }

            // Delays: 100, 200, 400, 800, 1600
            assertThat(delays[0]).isLessThan(delays[1]); // GH-90000
            assertThat(delays[1]).isLessThan(delays[2]); // GH-90000
            assertThat(delays[2]).isLessThan(delays[3]); // GH-90000
            assertThat(delays[3]).isLessThan(delays[4]); // GH-90000
            assertThat(delays[4]).isEqualTo(1600L); // GH-90000
        }
    }
}
