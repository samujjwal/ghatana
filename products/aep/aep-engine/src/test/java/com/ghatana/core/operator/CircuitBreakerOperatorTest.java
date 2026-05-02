/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("CircuitBreakerOperator")
class CircuitBreakerOperatorTest extends EventloopTestBase {

    private static final String CB_NAME = "test-circuit";

    private UnifiedOperator mockDelegate() { 
        UnifiedOperator delegate = mock(UnifiedOperator.class); 
        // default lifecycle promises
        when(delegate.initialize(org.mockito.ArgumentMatchers.any())).thenReturn(Promise.complete()); 
        when(delegate.start()).thenReturn(Promise.complete()); 
        when(delegate.stop()).thenReturn(Promise.complete()); 
        when(delegate.isHealthy()).thenReturn(true); 
        return delegate;
    }

    private Event mockEvent() { 
        Event event = mock(Event.class); 
        when(event.getId()).thenReturn(mock(com.ghatana.platform.domain.event.EventId.class)); 
        return event;
    }

    @Nested
    @DisplayName("CLOSED state — normal operation")
    class ClosedStateTests {

        @Test
        @DisplayName("forwards event to delegate and returns success result")
        void shouldForwardToDelegate() { 
            UnifiedOperator delegate = mockDelegate(); 
            Event event = mockEvent(); 
            when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.of(event))); 

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
                .name(CB_NAME) 
                .operator(delegate) 
                .eventloop(eventloop()) 
                .failureThreshold(5) 
                .build(); 

            OperatorResult result = runPromise(() -> cb.process(event)); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(cb.getCircuitState()).isEqualTo( 
                com.ghatana.platform.resilience.CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("opens circuit after failureThreshold consecutive delegate failures")
        void shouldOpenAfterThreshold() { 
            UnifiedOperator delegate = mockDelegate(); 
            Event event = mockEvent(); 
            when(delegate.process(event)) 
                .thenReturn(Promise.ofException(new RuntimeException("downstream-down")));

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
                .name(CB_NAME + "-open") 
                .operator(delegate) 
                .eventloop(eventloop()) 
                .failureThreshold(3) 
                .resetTimeout(Duration.ofMinutes(10)) // won't reset during test 
                .build(); 

            // Exhaust failure threshold
            for (int i = 0; i < 3; i++) { 
                OperatorResult r = runPromise(() -> cb.process(event)); 
                assertThat(r.isSuccess()).isFalse(); 
            }

            assertThat(cb.getCircuitState()).isEqualTo( 
                com.ghatana.platform.resilience.CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("OPEN state — fast-fail behaviour")
    class OpenStateTests {

        @Test
        @DisplayName("returns circuit-open failure without calling delegate when circuit is OPEN")
        void shouldFailFastWhenOpen() { 
            UnifiedOperator delegate = mockDelegate(); 
            Event event = mockEvent(); 
            AtomicInteger delegateCalls = new AtomicInteger(); 
            when(delegate.process(event)).thenAnswer(inv -> { 
                delegateCalls.incrementAndGet(); 
                return Promise.ofException(new RuntimeException("boom"));
            });

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
                .name(CB_NAME + "-fast-fail") 
                .operator(delegate) 
                .eventloop(eventloop()) 
                .failureThreshold(2) 
                .resetTimeout(Duration.ofMinutes(10)) 
                .build(); 

            // Trip the breaker
            runPromise(() -> cb.process(event)); 
            runPromise(() -> cb.process(event)); 
            assertThat(cb.getCircuitState()).isEqualTo( 
                com.ghatana.platform.resilience.CircuitBreaker.State.OPEN);

            // Reset call counter after tripping
            delegateCalls.set(0); 

            // Now fast-fail — delegate must NOT be called
            OperatorResult fastFail = runPromise(() -> cb.process(event)); 
            assertThat(fastFail.isSuccess()).isFalse(); 
            assertThat(fastFail.getErrorMessage()).contains("circuit-open");
            assertThat(delegateCalls.get()).isZero(); 
        }
    }

    @Nested
    @DisplayName("isHealthy()")
    class HealthTests {

        @Test
        @DisplayName("returns false when circuit is OPEN")
        void shouldBeUnhealthyWhenOpen() { 
            UnifiedOperator delegate = mockDelegate(); 
            Event event = mockEvent(); 
            when(delegate.process(event)).thenReturn( 
                Promise.ofException(new RuntimeException("fail")));

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
                .name(CB_NAME + "-health") 
                .operator(delegate) 
                .eventloop(eventloop()) 
                .failureThreshold(1) 
                .resetTimeout(Duration.ofMinutes(10)) 
                .build(); 

            runPromise(() -> cb.process(event)); 
            assertThat(cb.isHealthy()).isFalse(); 
        }

        @Test
        @DisplayName("returns true when circuit is CLOSED and delegate is healthy")
        void shouldBeHealthyWhenClosed() { 
            UnifiedOperator delegate = mockDelegate(); 
            Event event = mockEvent(); 
            when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.empty())); 

            CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
                .name(CB_NAME + "-healthy") 
                .operator(delegate) 
                .eventloop(eventloop()) 
                .build(); 

            runPromise(() -> cb.process(event)); 
            assertThat(cb.isHealthy()).isTrue(); 
        }
    }

    @Test
    @DisplayName("getInternalState() includes circuit metrics")
    void shouldExposeInternalState() { 
        UnifiedOperator delegate = mockDelegate(); 
        Event event = mockEvent(); 
        when(delegate.process(event)).thenReturn(Promise.of(OperatorResult.empty())); 

        CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
            .name(CB_NAME + "-state") 
            .operator(delegate) 
            .eventloop(eventloop()) 
            .build(); 

        runPromise(() -> cb.process(event)); 

        var state = cb.getInternalState(); 
        assertThat(state).containsKey("circuit_state");
        assertThat(state).containsKey("circuit_metrics");
        assertThat((String) state.get("circuit_state")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("profile(STRICT) applies strict settings")
    void shouldApplyStrictProfile() { 
        UnifiedOperator delegate = mockDelegate(); 
        Event event = mockEvent(); 
        when(delegate.process(event)).thenReturn( 
            Promise.ofException(new RuntimeException("fail")));

        CircuitBreakerOperator cb = CircuitBreakerOperator.builder() 
            .name(CB_NAME + "-strict") 
            .operator(delegate) 
            .eventloop(eventloop()) 
            .profile("STRICT")   // failureThreshold=3
            .resetTimeout(Duration.ofMinutes(10)) 
            .build(); 

        // STRICT threshold = 3 — needs 3 failures to open
        runPromise(() -> cb.process(event)); 
        runPromise(() -> cb.process(event)); 
        assertThat(cb.getCircuitState()).isEqualTo( 
            com.ghatana.platform.resilience.CircuitBreaker.State.CLOSED);

        runPromise(() -> cb.process(event)); 
        assertThat(cb.getCircuitState()).isEqualTo( 
            com.ghatana.platform.resilience.CircuitBreaker.State.OPEN);
    }
}
