/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("K18-002/003 CircuitBreaker — Fallback and Profiles")
class CircuitBreakerProfilesTest extends EventloopTestBase {

    @Test
    @DisplayName("execute_open_fallback: returns fallback value when circuit is OPEN")
    void execute_open_fallback_returnsFallbackValue() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(2)
                .resetTimeout(Duration.ofSeconds(60))
                .build();

        // force circuit open
        RuntimeException boom = new RuntimeException("boom");
        runBlocking(() -> {
            cb.execute(eventloop(), () -> Promise.ofException(boom));
            cb.execute(eventloop(), () -> Promise.ofException(boom));
        });
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // execute with fallback — should return fallback, not throw
        String result = runPromise(() ->
                cb.execute(eventloop(), () -> Promise.of("live"), () -> "fallback"));
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    @DisplayName("execute_open_noFallback_throws: throws CircuitBreakerOpenException when open without fallback")
    void execute_open_noFallback_throws() {
        CircuitBreaker cb = CircuitBreaker.builder("test")
                .failureThreshold(2)
                .resetTimeout(Duration.ofSeconds(60))
                .build();

        RuntimeException boom = new RuntimeException("boom");
        runBlocking(() -> {
            cb.execute(eventloop(), () -> Promise.ofException(boom));
            cb.execute(eventloop(), () -> Promise.ofException(boom));
        });
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> runPromise(() ->
                cb.execute(eventloop(), () -> Promise.of("live"))))
                .isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class)
                .hasMessageContaining("test");
    }

    @Test
    @DisplayName("execute_closed_withFallback: executes normally (ignores fallback) when CLOSED")
    void execute_closed_withFallback_executesNormally() {
        CircuitBreaker cb = CircuitBreaker.builder("test").build();

        String result = runPromise(() ->
                cb.execute(eventloop(), () -> Promise.of("live"), () -> "fallback"));
        assertThat(result).isEqualTo("live");
    }

    // ─── Profile tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("profile_strict: failureThreshold=3, resetTimeout=60s")
    void profile_strict_hasCorrectDefaults() {
        CircuitBreaker cb = CircuitBreakerProfiles.strict("ledger");
        assertThat(cb.getName()).isEqualTo("ledger");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Trip after exactly 3 failures
        RuntimeException boom = new RuntimeException("boom");
        runBlocking(() -> {
            cb.execute(eventloop(), () -> Promise.ofException(boom));
            cb.execute(eventloop(), () -> Promise.ofException(boom));
        });
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // not yet

        runBlocking(() ->
                cb.execute(eventloop(), () -> Promise.ofException(boom)));
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("profile_standard: failureThreshold=5, circuit opens at 5th failure")
    void profile_standard_opensAtThreshold() {
        CircuitBreaker cb = CircuitBreakerProfiles.standard("market-data");
        RuntimeException boom = new RuntimeException("boom");

        for (int i = 0; i < 4; i++) {
            int idx = i;
            runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom)));
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom)));
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("profile_relaxed: failureThreshold=10, tolerates more failures")
    void profile_relaxed_toleratesMoreFailures() {
        CircuitBreaker cb = CircuitBreakerProfiles.relaxed("reporting");
        RuntimeException boom = new RuntimeException("boom");

        for (int i = 0; i < 9; i++) {
            runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom)));
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // not yet at 10

        runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom)));
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("profile_override_fromConfig: forProfile selects correct preset")
    void profile_override_fromConfig() {
        CircuitBreaker strict = CircuitBreakerProfiles.forProfile("cb1", "STRICT");
        CircuitBreaker standard = CircuitBreakerProfiles.forProfile("cb2", "STANDARD");
        CircuitBreaker relaxed = CircuitBreakerProfiles.forProfile("cb3", "RELAXED");
        CircuitBreaker unknown = CircuitBreakerProfiles.forProfile("cb4", "unknown");

        // All should start in CLOSED
        assertThat(strict.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(standard.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(relaxed.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        // unknown falls back to STANDARD
        assertThat(unknown.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
