/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CircuitBreakerOperator}.
 *
 * @doc.type class
 * @doc.purpose Verify circuit breaker opens/closes correctly and fails fast when OPEN
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CircuitBreakerOperator [GH-90000]")
class CircuitBreakerOperatorTest extends EventloopTestBase {

    private static final String CB_NAME = "test-circuit";

    private UnifiedOperator mockDelegate() { // GH-90000
        UnifiedOperator delegate = mock(UnifiedOperator.class); // GH-90000
        // default lifecycle promises
        when(delegate.initialize(org.mockito.ArgumentMatchers.any())).thenReturn(Promise.complete()); // GH-90000
        when(delegate.start()).thenReturn(Promise.complete()); // GH-90000
        when(delegate.stop()).thenReturn(Promise.complete()); // GH-90000
        when(delegate.isHealthy()).thenReturn(true); // GH-90000
        return delegate;
    }

    private Event mockEvent() { // GH-90000
        Event event = mock(Event.class); // GH-90000
        when(event.getId()).thenReturn(mock(com.ghatana.platform.domain.event.EventId.class)); // GH-90000
        return event;
    }

    @Nested
    @DisplayName("CLOSED state — normal operation [GH-90000]")
    class ClosedStateTests {

        @Test
        @DisplayName("forwards event to delegate and returns success result [GH-90000]")
        void shouldForwardToDelegate() { // GH-90000
            UnifiedOperator delegate = mockDelegate(); // GH-90000
            Event event = mockEvent(); // GH-90000
            when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.of(event))); // GH-90000

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
                .name(CB_NAME) // GH-90000
                .operator(delegate) // GH-90000
                .eventloop(eventloop()) // GH-90000
                .failureThreshold(5) // GH-90000
                .build(); // GH-90000

            OperatorResult result = runPromise(() -> cb.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(cb.getCircuitState()).isEqualTo( // GH-90000
                com.ghatana.platform.resilience.CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("opens circuit after failureThreshold consecutive delegate failures [GH-90000]")
        void shouldOpenAfterThreshold() { // GH-90000
            UnifiedOperator delegate = mockDelegate(); // GH-90000
            Event event = mockEvent(); // GH-90000
            when(delegate.process(event)) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("downstream-down [GH-90000]")));

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
                .name(CB_NAME + "-open") // GH-90000
                .operator(delegate) // GH-90000
                .eventloop(eventloop()) // GH-90000
                .failureThreshold(3) // GH-90000
                .resetTimeout(Duration.ofMinutes(10)) // won't reset during test // GH-90000
                .build(); // GH-90000

            // Exhaust failure threshold
            for (int i = 0; i < 3; i++) { // GH-90000
                OperatorResult r = runPromise(() -> cb.process(event)); // GH-90000
                assertThat(r.isSuccess()).isFalse(); // GH-90000
            }

            assertThat(cb.getCircuitState()).isEqualTo( // GH-90000
                com.ghatana.platform.resilience.CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("OPEN state — fast-fail behaviour [GH-90000]")
    class OpenStateTests {

        @Test
        @DisplayName("returns circuit-open failure without calling delegate when circuit is OPEN [GH-90000]")
        void shouldFailFastWhenOpen() { // GH-90000
            UnifiedOperator delegate = mockDelegate(); // GH-90000
            Event event = mockEvent(); // GH-90000
            AtomicInteger delegateCalls = new AtomicInteger(); // GH-90000
            when(delegate.process(event)).thenAnswer(inv -> { // GH-90000
                delegateCalls.incrementAndGet(); // GH-90000
                return Promise.ofException(new RuntimeException("boom [GH-90000]"));
            });

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
                .name(CB_NAME + "-fast-fail") // GH-90000
                .operator(delegate) // GH-90000
                .eventloop(eventloop()) // GH-90000
                .failureThreshold(2) // GH-90000
                .resetTimeout(Duration.ofMinutes(10)) // GH-90000
                .build(); // GH-90000

            // Trip the breaker
            runPromise(() -> cb.process(event)); // GH-90000
            runPromise(() -> cb.process(event)); // GH-90000
            assertThat(cb.getCircuitState()).isEqualTo( // GH-90000
                com.ghatana.platform.resilience.CircuitBreaker.State.OPEN);

            // Reset call counter after tripping
            delegateCalls.set(0); // GH-90000

            // Now fast-fail — delegate must NOT be called
            OperatorResult fastFail = runPromise(() -> cb.process(event)); // GH-90000
            assertThat(fastFail.isSuccess()).isFalse(); // GH-90000
            assertThat(fastFail.getErrorMessage()).contains("circuit-open [GH-90000]");
            assertThat(delegateCalls.get()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isHealthy() [GH-90000]")
    class HealthTests {

        @Test
        @DisplayName("returns false when circuit is OPEN [GH-90000]")
        void shouldBeUnhealthyWhenOpen() { // GH-90000
            UnifiedOperator delegate = mockDelegate(); // GH-90000
            Event event = mockEvent(); // GH-90000
            when(delegate.process(event)).thenReturn( // GH-90000
                Promise.ofException(new RuntimeException("fail [GH-90000]")));

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
                .name(CB_NAME + "-health") // GH-90000
                .operator(delegate) // GH-90000
                .eventloop(eventloop()) // GH-90000
                .failureThreshold(1) // GH-90000
                .resetTimeout(Duration.ofMinutes(10)) // GH-90000
                .build(); // GH-90000

            runPromise(() -> cb.process(event)); // GH-90000
            assertThat(cb.isHealthy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("returns true when circuit is CLOSED and delegate is healthy [GH-90000]")
        void shouldBeHealthyWhenClosed() { // GH-90000
            UnifiedOperator delegate = mockDelegate(); // GH-90000
            Event event = mockEvent(); // GH-90000
            when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
                .name(CB_NAME + "-healthy") // GH-90000
                .operator(delegate) // GH-90000
                .eventloop(eventloop()) // GH-90000
                .build(); // GH-90000

            runPromise(() -> cb.process(event)); // GH-90000
            assertThat(cb.isHealthy()).isTrue(); // GH-90000
        }
    }

    @Test
    @DisplayName("getInternalState() includes circuit metrics [GH-90000]")
    void shouldExposeInternalState() { // GH-90000
        UnifiedOperator delegate = mockDelegate(); // GH-90000
        Event event = mockEvent(); // GH-90000
        when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.empty())); // GH-90000

        CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
            .name(CB_NAME + "-state") // GH-90000
            .operator(delegate) // GH-90000
            .eventloop(eventloop()) // GH-90000
            .build(); // GH-90000

        runPromise(() -> cb.process(event)); // GH-90000

        var state = cb.getInternalState(); // GH-90000
        assertThat(state).containsKey("circuit_state [GH-90000]");
        assertThat(state).containsKey("circuit_metrics [GH-90000]");
        assertThat((String) state.get("circuit_state [GH-90000]")).isEqualTo("CLOSED [GH-90000]");
    }

    @Test
    @DisplayName("profile(STRICT) applies strict settings [GH-90000]")
    void shouldApplyStrictProfile() { // GH-90000
        UnifiedOperator delegate = mockDelegate(); // GH-90000
        Event event = mockEvent(); // GH-90000
        when(delegate.process(event)).thenReturn( // GH-90000
            Promise.ofException(new RuntimeException("fail [GH-90000]")));

        CircuitBreakerOperator cb = CircuitBreakerOperator.builder() // GH-90000
            .name(CB_NAME + "-strict") // GH-90000
            .operator(delegate) // GH-90000
            .eventloop(eventloop()) // GH-90000
            .profile("STRICT [GH-90000]")   // failureThreshold=3
            .resetTimeout(Duration.ofMinutes(10)) // GH-90000
            .build(); // GH-90000

        // STRICT threshold = 3 — needs 3 failures to open
        runPromise(() -> cb.process(event)); // GH-90000
        runPromise(() -> cb.process(event)); // GH-90000
        assertThat(cb.getCircuitState()).isEqualTo( // GH-90000
            com.ghatana.platform.resilience.CircuitBreaker.State.CLOSED);

        runPromise(() -> cb.process(event)); // GH-90000
        assertThat(cb.getCircuitState()).isEqualTo( // GH-90000
            com.ghatana.platform.resilience.CircuitBreaker.State.OPEN);
    }
}
