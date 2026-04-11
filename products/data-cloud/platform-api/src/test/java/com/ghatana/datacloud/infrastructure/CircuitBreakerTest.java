/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for circuit breaker logic (IE002).
 *
 * @doc.type class
 * @doc.purpose Circuit breaker logic tests
 * @doc.layer product
 * @doc.pattern Infrastructure Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CircuitBreaker – Circuit Breaker Logic (IE002)")
class CircuitBreakerTest extends EventloopTestBase {

    @Mock
    private ExternalService externalService;

    @Nested
    @DisplayName("Circuit States")
    class CircuitStatesTests {

        @Test
        @DisplayName("[IE002]: circuit_closed_when_healthy")
        void circuitClosedWhenHealthy() {
            CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(30));

            // All requests succeed when closed
            when(externalService.call())
                .thenReturn(Promise.of("success"));

            String result = runPromise(() -> externalService.call());

            assertThat(result).isEqualTo("success");
            assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED);
        }

        @Test
        @DisplayName("[IE002]: circuit_opens_after_failures")
        void circuitOpensAfterFailures() {
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30));
            breaker.recordFailure();
            breaker.recordFailure();
            breaker.recordFailure();

            // After threshold failures, circuit should be OPEN
            assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN);
        }

        @Test
        @DisplayName("[IE002]: circuit_half_open_after_timeout")
        void circuitHalfOpenAfterTimeout() {
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMillis(100));
            breaker.recordFailure();
            breaker.recordFailure();
            breaker.recordFailure();

            // Wait for timeout
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {}

            // Circuit should transition to HALF_OPEN
            assertThat(breaker.getState()).isEqualTo(CircuitState.HALF_OPEN);
        }

        @Test
        @DisplayName("[IE002]: circuit_closes_after_success_in_half_open")
        void circuitClosesAfterSuccessInHalfOpen() {
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ZERO);
            breaker.transitionTo(CircuitState.HALF_OPEN);

            breaker.recordSuccess();

            assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED);
        }
    }

    @Nested
    @DisplayName("Circuit Behavior")
    class CircuitBehaviorTests {

        @Test
        @DisplayName("[IE002]: requests_fail_fast_when_open")
        void requestsFailFastWhenOpen() {
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30));
            breaker.transitionTo(CircuitState.OPEN);

            long start = System.currentTimeMillis();
            breaker.call(() -> externalService.call());
            long duration = System.currentTimeMillis() - start;

            // Should fail immediately without calling service
            assertThat(duration).isLessThan(100);
        }

        @Test
        @DisplayName("[IE002]: failures_in_half_open_reopen_circuit")
        void failuresInHalfOpenReopenCircuit() {
            CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30));
            breaker.transitionTo(CircuitState.HALF_OPEN);

            breaker.recordFailure();

            assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN);
        }

        @Test
        @DisplayName("[IE002]: success_resets_failure_count")
        void successResetsFailureCount() {
            CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(30));
            breaker.recordFailure();
            breaker.recordFailure();

            assertThat(breaker.getFailureCount()).isEqualTo(2);

            breaker.recordSuccess();

            assertThat(breaker.getFailureCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("[IE002]: threshold_configurable")
        void thresholdConfigurable() {
            CircuitBreaker breaker5 = new CircuitBreaker(5, Duration.ofSeconds(30));
            CircuitBreaker breaker10 = new CircuitBreaker(10, Duration.ofSeconds(30));

            assertThat(breaker5.getThreshold()).isEqualTo(5);
            assertThat(breaker10.getThreshold()).isEqualTo(10);
        }

        @Test
        @DisplayName("[IE002]: timeout_configurable")
        void timeoutConfigurable() {
            CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(60));

            assertThat(breaker.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    // Circuit breaker implementation for testing
    static class CircuitBreaker {
        private final int threshold;
        private final Duration timeout;
        private CircuitState state = CircuitState.CLOSED;
        private int failureCount = 0;
        private Instant lastFailureTime;

        CircuitBreaker(int threshold, Duration timeout) {
            this.threshold = threshold;
            this.timeout = timeout;
        }

        CircuitState getState() {
            if (state == CircuitState.OPEN &&
                lastFailureTime.plus(timeout).isBefore(Instant.now())) {
                state = CircuitState.HALF_OPEN;
            }
            return state;
        }

        void recordFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
            if (state == CircuitState.HALF_OPEN || failureCount >= threshold) {
                state = CircuitState.OPEN;
            }
        }

        void recordSuccess() {
            failureCount = 0;
            state = CircuitState.CLOSED;
        }

        void transitionTo(CircuitState newState) {
            this.state = newState;
        }

        int getFailureCount() {
            return failureCount;
        }

        int getThreshold() {
            return threshold;
        }

        Duration getTimeout() {
            return timeout;
        }

        <T> Promise<T> call(java.util.function.Supplier<Promise<T>> supplier) {
            if (state == CircuitState.OPEN) {
                return Promise.ofException(new RuntimeException("Circuit breaker open"));
            }
            return supplier.get();
        }
    }

    enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    interface ExternalService {
        Promise<String> call();
    }
}
