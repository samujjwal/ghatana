/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
@DisplayName("CircuitBreaker – State Machine & Error Protection")
class CircuitBreakerTest extends EventloopTestBase {

    @Test
    @Order(1)
    @DisplayName("1. Starts in CLOSED state")
    void startsClosedState() {
        CircuitBreaker cb = CircuitBreaker.builder("test").build();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getName()).isEqualTo("test");
    }

    @Test
    @Order(2)
    @DisplayName("2. Successful calls keep circuit CLOSED")
    void successKeepsClosed() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(3)
                .build();

        runBlocking(() -> {
            for (int i = 0; i < 10; i++) {
                cb.execute(eventloop(), () -> Promise.of("ok"));
            }
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getTotalSuccesses()).isEqualTo(10);
        assertThat(cb.getFailureCount()).isEqualTo(0);
    }

    @Test
    @Order(3)
    @DisplayName("3. Opens after reaching failure threshold")
    void opensAfterFailureThreshold() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(3)
                .build();

        runBlocking(() -> {
            for (int i = 0; i < 3; i++) {
                cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
            }
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getFailureCount()).isEqualTo(3);
    }

    @Test
    @Order(4)
    @DisplayName("4. Rejects calls when OPEN")
    void rejectsWhenOpen() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(1)
                .resetTimeout(Duration.ofMinutes(5))
                .build();

        runBlocking(() -> {
            // Trip the circuit
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
            // Next call should be rejected
            cb.execute(eventloop(), () -> Promise.of("should-not-reach"))
                    .whenException(e -> {
                        assertThat(e).isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class);
                    });
        });

        assertThat(cb.getTotalRejections()).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("5. Manual reset restores CLOSED state")
    void manualReset() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(1)
                .build();

        runBlocking(() -> {
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailureCount()).isEqualTo(0);
    }

    @Test
    @Order(6)
    @DisplayName("6. Success resets failure counter in CLOSED state")
    void successResetsFailureCount() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(3)
                .build();

        runBlocking(() -> {
            // 2 failures (below threshold)
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f1")));
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f2")));
            // 1 success resets counter
            cb.execute(eventloop(), () -> Promise.of("ok"));
            // 2 more failures — still below threshold
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f3")));
            cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("f4")));
        });

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailureCount()).isEqualTo(2);
    }

    @Test
    @Order(7)
    @DisplayName("7. Builder defaults are sensible")
    void builderDefaults() {
        CircuitBreaker cb = CircuitBreaker.builder("defaults").build();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getTotalCalls()).isZero();
        assertThat(cb.getTotalFailures()).isZero();
        assertThat(cb.getTotalSuccesses()).isZero();
        assertThat(cb.getTotalRejections()).isZero();
    }

    @Test
    @Order(8)
    @DisplayName("8. CircuitBreakerOpenException carries circuit name and retry duration")
    void openExceptionDetails() {
        var ex = new CircuitBreaker.CircuitBreakerOpenException("my-cb", Duration.ofSeconds(30));
        assertThat(ex.getCircuitName()).isEqualTo("my-cb");
        assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(30));
        assertThat(ex.getMessage()).contains("my-cb").contains("OPEN");
    }

    @Test
    @Order(9)
    @DisplayName("9. Metrics accumulate across multiple operations")
    void metricsAccumulate() {
        CircuitBreaker cb = CircuitBreaker.builder("metrics")
                .failureThreshold(10)
                .build();

        runBlocking(() -> {
            for (int i = 0; i < 5; i++) cb.execute(eventloop(), () -> Promise.of("ok"));
            for (int i = 0; i < 3; i++) cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException()));
        });

        assertThat(cb.getTotalCalls()).isEqualTo(8);
        assertThat(cb.getTotalSuccesses()).isEqualTo(5);
        assertThat(cb.getTotalFailures()).isEqualTo(3);
    }
}
