/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for circuit breaker logic (IE002). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Circuit breaker logic tests
 * @doc.layer product
 * @doc.pattern Infrastructure Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("CircuitBreaker – Circuit Breaker Logic (IE002) [GH-90000]")
class CircuitBreakerTest extends EventloopTestBase {

    @Mock
    private ExternalService externalService;

    @Nested
    @DisplayName("Circuit States [GH-90000]")
    class CircuitStatesTests {

        @Test
        @DisplayName("[IE002]: circuit_closed_when_healthy [GH-90000]")
        void circuitClosedWhenHealthy() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(30)); // GH-90000

            // All requests succeed when closed
            when(externalService.call()) // GH-90000
                .thenReturn(Promise.of("success [GH-90000]"));

            String result = runPromise(() -> externalService.call()); // GH-90000

            assertThat(result).isEqualTo("success [GH-90000]");
            assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED); // GH-90000
        }

        @Test
        @DisplayName("[IE002]: circuit_opens_after_failures [GH-90000]")
        void circuitOpensAfterFailures() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30)); // GH-90000
            breaker.recordFailure(); // GH-90000
            breaker.recordFailure(); // GH-90000
            breaker.recordFailure(); // GH-90000

            // After threshold failures, circuit should be OPEN
            assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN); // GH-90000
        }

        @Test
        @DisplayName("[IE002]: circuit_half_open_after_timeout [GH-90000]")
        void circuitHalfOpenAfterTimeout() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMillis(100)); // GH-90000
            breaker.recordFailure(); // GH-90000
            breaker.recordFailure(); // GH-90000
            breaker.recordFailure(); // GH-90000

            // Wait for timeout
            try {
                Thread.sleep(150); // GH-90000
            } catch (InterruptedException ignored) {} // GH-90000

            // Circuit should transition to HALF_OPEN
            assertThat(breaker.getState()).isEqualTo(CircuitState.HALF_OPEN); // GH-90000
        }

        @Test
        @DisplayName("[IE002]: circuit_closes_after_success_in_half_open [GH-90000]")
        void circuitClosesAfterSuccessInHalfOpen() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ZERO); // GH-90000
            breaker.transitionTo(CircuitState.HALF_OPEN); // GH-90000

            breaker.recordSuccess(); // GH-90000

            assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED); // GH-90000
        }
    }

    @Nested
    @DisplayName("Circuit Behavior [GH-90000]")
    class CircuitBehaviorTests {

        @Test
        @DisplayName("[IE002]: requests_fail_fast_when_open [GH-90000]")
        void requestsFailFastWhenOpen() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30)); // GH-90000
            breaker.transitionTo(CircuitState.OPEN); // GH-90000

            long start = System.currentTimeMillis(); // GH-90000
            breaker.call(() -> externalService.call()); // GH-90000
            long duration = System.currentTimeMillis() - start; // GH-90000

            // Should fail immediately without calling service
            assertThat(duration).isLessThan(100); // GH-90000
        }

        @Test
        @DisplayName("[IE002]: failures_in_half_open_reopen_circuit [GH-90000]")
        void failuresInHalfOpenReopenCircuit() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30)); // GH-90000
            breaker.transitionTo(CircuitState.HALF_OPEN); // GH-90000

            breaker.recordFailure(); // GH-90000

            assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN); // GH-90000
        }

        @Test
        @DisplayName("[IE002]: success_resets_failure_count [GH-90000]")
        void successResetsFailureCount() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(30)); // GH-90000
            breaker.recordFailure(); // GH-90000
            breaker.recordFailure(); // GH-90000

            assertThat(breaker.getFailureCount()).isEqualTo(2); // GH-90000

            breaker.recordSuccess(); // GH-90000

            assertThat(breaker.getFailureCount()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Configuration [GH-90000]")
    class ConfigurationTests {

        @Test
        @DisplayName("[IE002]: threshold_configurable [GH-90000]")
        void thresholdConfigurable() { // GH-90000
            CircuitBreaker breaker5 = new CircuitBreaker(5, Duration.ofSeconds(30)); // GH-90000
            CircuitBreaker breaker10 = new CircuitBreaker(10, Duration.ofSeconds(30)); // GH-90000

            assertThat(breaker5.getThreshold()).isEqualTo(5); // GH-90000
            assertThat(breaker10.getThreshold()).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("[IE002]: timeout_configurable [GH-90000]")
        void timeoutConfigurable() { // GH-90000
            CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(60)); // GH-90000

            assertThat(breaker.getTimeout()).isEqualTo(Duration.ofSeconds(60)); // GH-90000
        }
    }

    // Circuit breaker implementation for testing
    static class CircuitBreaker {
        private final int threshold;
        private final Duration timeout;
        private CircuitState state = CircuitState.CLOSED;
        private int failureCount = 0;
        private Instant lastFailureTime;

        CircuitBreaker(int threshold, Duration timeout) { // GH-90000
            this.threshold = threshold;
            this.timeout = timeout;
        }

        CircuitState getState() { // GH-90000
            if (state == CircuitState.OPEN && // GH-90000
                lastFailureTime.plus(timeout).isBefore(Instant.now())) { // GH-90000
                state = CircuitState.HALF_OPEN;
            }
            return state;
        }

        void recordFailure() { // GH-90000
            failureCount++;
            lastFailureTime = Instant.now(); // GH-90000
            if (state == CircuitState.HALF_OPEN || failureCount >= threshold) { // GH-90000
                state = CircuitState.OPEN;
            }
        }

        void recordSuccess() { // GH-90000
            failureCount = 0;
            state = CircuitState.CLOSED;
        }

        void transitionTo(CircuitState newState) { // GH-90000
            this.state = newState;
        }

        int getFailureCount() { // GH-90000
            return failureCount;
        }

        int getThreshold() { // GH-90000
            return threshold;
        }

        Duration getTimeout() { // GH-90000
            return timeout;
        }

        <T> Promise<T> call(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
            if (state == CircuitState.OPEN) { // GH-90000
                return Promise.ofException(new RuntimeException("Circuit breaker open [GH-90000]"));
            }
            return supplier.get(); // GH-90000
        }
    }

    enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    interface ExternalService {
        Promise<String> call(); // GH-90000
    }
}
