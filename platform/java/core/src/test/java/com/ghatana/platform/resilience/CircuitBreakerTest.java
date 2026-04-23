/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class) // GH-90000
@DisplayName("CircuitBreaker – State Machine & Error Protection")
class CircuitBreakerTest extends EventloopTestBase {

    @Test
    @Order(1) // GH-90000
    @DisplayName("1. Starts in CLOSED state")
    void startsClosedState() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test").build();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(cb.getName()).isEqualTo("test");
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("2. Successful calls keep circuit CLOSED")
    void successKeepsClosed() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(3) // GH-90000
                .build(); // GH-90000

        runBlocking(() -> { // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                cb.execute(eventloop(), () -> Promise.of("ok"));
            }
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(cb.getTotalSuccesses()).isEqualTo(10); // GH-90000
        assertThat(cb.getFailureCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("3. Opens after reaching failure threshold")
    void opensAfterFailureThreshold() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(3) // GH-90000
                .build(); // GH-90000

        runBlocking(() -> { // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
            }
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
        assertThat(cb.getFailureCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("4. Rejects calls when OPEN")
    void rejectsWhenOpen() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(1) // GH-90000
                .resetTimeout(Duration.ofMinutes(5)) // GH-90000
                .build(); // GH-90000

        runBlocking(() -> { // GH-90000
            // Trip the circuit
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
            // Next call should be rejected
            cb.execute(eventloop(), () -> Promise.of("should-not-reach"))
                    .whenException(e -> { // GH-90000
                        assertThat(e).isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class); // GH-90000
                    });
        });

        assertThat(cb.getTotalRejections()).isEqualTo(1); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("5. Manual reset restores CLOSED state")
    void manualReset() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(1) // GH-90000
                .build(); // GH-90000

        runBlocking(() -> { // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        cb.reset(); // GH-90000
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(cb.getFailureCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("6. Success resets failure counter in CLOSED state")
    void successResetsFailureCount() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(3) // GH-90000
                .build(); // GH-90000

        runBlocking(() -> { // GH-90000
            // 2 failures (below threshold) // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f1")));
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f2")));
            // 1 success resets counter
            cb.execute(eventloop(), () -> Promise.of("ok"));
            // 2 more failures — still below threshold
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f3")));
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f4")));
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(cb.getFailureCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("7. Builder defaults are sensible")
    void builderDefaults() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("defaults").build();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(cb.getTotalCalls()).isZero(); // GH-90000
        assertThat(cb.getTotalFailures()).isZero(); // GH-90000
        assertThat(cb.getTotalSuccesses()).isZero(); // GH-90000
        assertThat(cb.getTotalRejections()).isZero(); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("8. CircuitBreakerOpenException carries circuit name and retry duration")
    void openExceptionDetails() { // GH-90000
        var ex = new CircuitBreaker.CircuitBreakerOpenException("my-cb", Duration.ofSeconds(30)); // GH-90000
        assertThat(ex.getCircuitName()).isEqualTo("my-cb");
        assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(30)); // GH-90000
        assertThat(ex.getMessage()).contains("my-cb").contains("OPEN");
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("9. Metrics accumulate across multiple operations")
    void metricsAccumulate() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("metrics")
                .failureThreshold(10) // GH-90000
                .build(); // GH-90000

        runBlocking(() -> { // GH-90000
            for (int i = 0; i < 5; i++) cb.execute(eventloop(), () -> Promise.of("ok"));
            for (int i = 0; i < 3; i++) cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException())); // GH-90000
        });

        assertThat(cb.getTotalCalls()).isEqualTo(8); // GH-90000
        assertThat(cb.getTotalSuccesses()).isEqualTo(5); // GH-90000
        assertThat(cb.getTotalFailures()).isEqualTo(3); // GH-90000
    }
}
