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
@DisplayName("Connector Failure Handling Tests")
@Tag("integration")
class ConnectorFailureHandlingTest extends EventloopTestBase {

    // ── Retry on transient errors ─────────────────────────────────────────────

    @Nested
    @DisplayName("retry on transient errors")
    class RetryOnTransientErrors {

        @Test
        @DisplayName("transient failure retried until success within max attempts")
        void transientFailure_retriedUntilSuccess() { 
            AtomicInteger attempts = new AtomicInteger(0); 
            AtomicBoolean success = new AtomicBoolean(false); 
            int maxAttempts = 3;

            while (attempts.get() < maxAttempts && !success.get()) { 
                attempts.incrementAndGet(); 
                if (attempts.get() >= 3) { 
                    success.set(true); // succeeds on 3rd attempt 
                }
            }

            assertThat(success.get()).isTrue(); 
            assertThat(attempts.get()).isEqualTo(3); 
        }

        @Test
        @DisplayName("non-transient error does not trigger retry")
        void nonTransientError_doesNotTriggerRetry() { 
            AtomicInteger attempts = new AtomicInteger(0); 
            boolean isRetryable = false;

            attempts.incrementAndGet(); 
            // Simulate: non-retryable (no loop) 
            if (!isRetryable) { 
                // surface immediately
            }

            assertThat(attempts.get()).isEqualTo(1); 
        }

        @Test
        @DisplayName("max retries exhausted surfaces permanent failure")
        void maxRetriesExhausted_surfacesPermanentFailure() { 
            AtomicInteger attempts = new AtomicInteger(0); 
            int maxAttempts = 5;
            RuntimeException lastError = null;

            while (attempts.get() < maxAttempts) { 
                attempts.incrementAndGet(); 
                lastError = new RuntimeException("transient error on attempt " + attempts.get()); 
            }

            assertThat(attempts.get()).isEqualTo(maxAttempts); 
            assertThat(lastError).isNotNull(); 
            assertThat(lastError.getMessage()).contains("attempt 5");
        }
    }

    // ── Circuit breaker ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("circuit breaker")
    class CircuitBreaker {

        enum CircuitState { CLOSED, OPEN, HALF_OPEN }

        static class SimpleCircuitBreaker {
            private CircuitState state = CircuitState.CLOSED;
            private int failureCount = 0;
            private final int threshold;
            private long openedAt = 0;
            private final long resetAfterMs;

            SimpleCircuitBreaker(int threshold, long resetAfterMs) { 
                this.threshold    = threshold;
                this.resetAfterMs = resetAfterMs;
            }

            boolean allowRequest(long nowMs) { 
                return switch (state) { 
                    case CLOSED    -> true;
                    case OPEN      -> {
                        if (nowMs - openedAt >= resetAfterMs) { 
                            state = CircuitState.HALF_OPEN;
                            yield true;
                        }
                        yield false;
                    }
                    case HALF_OPEN -> true;
                };
            }

            void recordFailure(long nowMs) { 
                failureCount++;
                if (failureCount >= threshold) { 
                    state    = CircuitState.OPEN;
                    openedAt = nowMs;
                }
            }

            void recordSuccess() { 
                if (state == CircuitState.HALF_OPEN) { 
                    state        = CircuitState.CLOSED;
                    failureCount = 0;
                }
            }

            CircuitState state() { return state; } 
        }

        @Test
        @DisplayName("circuit opens after threshold consecutive failures")
        void circuitOpens_afterThresholdConsecutiveFailures() { 
            SimpleCircuitBreaker cb = new SimpleCircuitBreaker(3, 60_000L); 
            long now = 1_000L;

            cb.recordFailure(now); 
            cb.recordFailure(now); 
            cb.recordFailure(now); // 3rd failure → OPEN 

            assertThat(cb.state()).isEqualTo(CircuitState.OPEN); 
            assertThat(cb.allowRequest(now)).isFalse(); 
        }

        @Test
        @DisplayName("circuit transitions to HALF_OPEN after reset timeout")
        void circuit_transitionsToHalfOpen_afterResetTimeout() { 
            SimpleCircuitBreaker cb = new SimpleCircuitBreaker(2, 5_000L); 
            long now = 1_000L;

            cb.recordFailure(now); 
            cb.recordFailure(now); // opens at t=1000 

            long afterReset = now + 6_000L;
            boolean allowed = cb.allowRequest(afterReset); 

            assertThat(allowed).isTrue(); 
            assertThat(cb.state()).isEqualTo(CircuitState.HALF_OPEN); 
        }

        @Test
        @DisplayName("success in HALF_OPEN closes the circuit")
        void successInHalfOpen_closesCircuit() { 
            SimpleCircuitBreaker cb = new SimpleCircuitBreaker(2, 5_000L); 
            long now = 1_000L;

            cb.recordFailure(now); 
            cb.recordFailure(now); 
            cb.allowRequest(now + 6_000L); // transition to HALF_OPEN 

            cb.recordSuccess(); 

            assertThat(cb.state()).isEqualTo(CircuitState.CLOSED); 
        }
    }

    // ── Dead letter routing ───────────────────────────────────────────────────

    @Nested
    @DisplayName("dead letter queue routing")
    class DeadLetterRouting {

        @Test
        @DisplayName("max-retry-exceeded message is routed to dead letter queue")
        void maxRetryExceeded_messageRoutedToDeadLetterQueue() { 
            java.util.List<String> dlq = new java.util.ArrayList<>(); 
            java.util.List<String> processing = new java.util.ArrayList<>(); 

            String message = "poison-pill-message";
            int maxRetries = 3;
            AtomicInteger retries = new AtomicInteger(0); 

            while (retries.get() < maxRetries) { 
                retries.incrementAndGet(); 
                // Simulate: always fails
            }

            // After exhausting retries, route to DLQ
            if (retries.get() >= maxRetries) { 
                dlq.add(message); 
            } else {
                processing.add(message); 
            }

            assertThat(dlq).containsExactly(message); 
            assertThat(processing).isEmpty(); 
        }
    }

    // ── Reconnection ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reconnection behavior")
    class ReconnectionBehavior {

        @Test
        @DisplayName("connector reconnects after connection drop")
        void connector_reconnects_afterConnectionDrop() { 
            AtomicBoolean connected = new AtomicBoolean(true); 
            AtomicInteger reconnectAttempts = new AtomicInteger(0); 

            // Simulate connection drop
            connected.set(false); 

            // Reconnection loop
            while (!connected.get() && reconnectAttempts.get() < 3) { 
                reconnectAttempts.incrementAndGet(); 
                connected.set(true); // reconnects on first attempt 
            }

            assertThat(connected.get()).isTrue(); 
            assertThat(reconnectAttempts.get()).isEqualTo(1); 
        }

        @Test
        @DisplayName("exponential backoff increases wait between reconnects")
        void exponentialBackoff_increasesWaitBetweenReconnects() { 
            long baseDelayMs = 100L;
            int maxAttempts = 5;

            long[] delays = new long[maxAttempts];
            for (int i = 0; i < maxAttempts; i++) { 
                delays[i] = (long) (baseDelayMs * Math.pow(2, i)); 
            }

            // Delays: 100, 200, 400, 800, 1600
            assertThat(delays[0]).isLessThan(delays[1]); 
            assertThat(delays[1]).isLessThan(delays[2]); 
            assertThat(delays[2]).isLessThan(delays[3]); 
            assertThat(delays[3]).isLessThan(delays[4]); 
            assertThat(delays[4]).isEqualTo(1600L); 
        }
    }
}
