/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("K18-002/003 CircuitBreaker — Fallback and Profiles [GH-90000]")
class CircuitBreakerProfilesTest extends EventloopTestBase {

    @Test
    @DisplayName("execute_open_fallback: returns fallback value when circuit is OPEN [GH-90000]")
    void execute_open_fallback_returnsFallbackValue() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test [GH-90000]")
                .failureThreshold(2) // GH-90000
                .resetTimeout(Duration.ofSeconds(60)) // GH-90000
                .build(); // GH-90000

        // force circuit open
        RuntimeException boom = new RuntimeException("boom [GH-90000]");
        runBlocking(() -> { // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(boom)); // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(boom)); // GH-90000
        });
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        // execute with fallback — should return fallback, not throw
        String result = runPromise(() -> // GH-90000
                cb.execute(eventloop(), () -> Promise.of("live [GH-90000]"), () -> "fallback"));
        assertThat(result).isEqualTo("fallback [GH-90000]");
    }

    @Test
    @DisplayName("execute_open_noFallback_throws: throws CircuitBreakerOpenException when open without fallback [GH-90000]")
    void execute_open_noFallback_throws() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test [GH-90000]")
                .failureThreshold(2) // GH-90000
                .resetTimeout(Duration.ofSeconds(60)) // GH-90000
                .build(); // GH-90000

        RuntimeException boom = new RuntimeException("boom [GH-90000]");
        runBlocking(() -> { // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(boom)); // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(boom)); // GH-90000
        });
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                cb.execute(eventloop(), () -> Promise.of("live [GH-90000]"))))
                .isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class) // GH-90000
                .hasMessageContaining("test [GH-90000]");
    }

    @Test
    @DisplayName("execute_closed_withFallback: executes normally (ignores fallback) when CLOSED [GH-90000]")
    void execute_closed_withFallback_executesNormally() { // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("test [GH-90000]").build();

        String result = runPromise(() -> // GH-90000
                cb.execute(eventloop(), () -> Promise.of("live [GH-90000]"), () -> "fallback"));
        assertThat(result).isEqualTo("live [GH-90000]");
    }

    // ─── Profile tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("profile_strict: failureThreshold=3, resetTimeout=60s [GH-90000]")
    void profile_strict_hasCorrectDefaults() { // GH-90000
        CircuitBreaker cb = CircuitBreakerProfiles.strict("ledger [GH-90000]");
        assertThat(cb.getName()).isEqualTo("ledger [GH-90000]");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000

        // Trip after exactly 3 failures
        RuntimeException boom = new RuntimeException("boom [GH-90000]");
        runBlocking(() -> { // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(boom)); // GH-90000
            cb.execute(eventloop(), () -> Promise.ofException(boom)); // GH-90000
        });
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // not yet // GH-90000

        runBlocking(() -> // GH-90000
                cb.execute(eventloop(), () -> Promise.ofException(boom))); // GH-90000
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
    }

    @Test
    @DisplayName("profile_standard: failureThreshold=5, circuit opens at 5th failure [GH-90000]")
    void profile_standard_opensAtThreshold() { // GH-90000
        CircuitBreaker cb = CircuitBreakerProfiles.standard("market-data [GH-90000]");
        RuntimeException boom = new RuntimeException("boom [GH-90000]");

        for (int i = 0; i < 4; i++) { // GH-90000
            int idx = i;
            runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom))); // GH-90000
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000

        runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom))); // GH-90000
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
    }

    @Test
    @DisplayName("profile_relaxed: failureThreshold=10, tolerates more failures [GH-90000]")
    void profile_relaxed_toleratesMoreFailures() { // GH-90000
        CircuitBreaker cb = CircuitBreakerProfiles.relaxed("reporting [GH-90000]");
        RuntimeException boom = new RuntimeException("boom [GH-90000]");

        for (int i = 0; i < 9; i++) { // GH-90000
            runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom))); // GH-90000
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // not yet at 10 // GH-90000

        runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(boom))); // GH-90000
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
    }

    @Test
    @DisplayName("profile_override_fromConfig: forProfile selects correct preset [GH-90000]")
    void profile_override_fromConfig() { // GH-90000
        CircuitBreaker strict = CircuitBreakerProfiles.forProfile("cb1", "STRICT"); // GH-90000
        CircuitBreaker standard = CircuitBreakerProfiles.forProfile("cb2", "STANDARD"); // GH-90000
        CircuitBreaker relaxed = CircuitBreakerProfiles.forProfile("cb3", "RELAXED"); // GH-90000
        CircuitBreaker unknown = CircuitBreakerProfiles.forProfile("cb4", "unknown"); // GH-90000

        // All should start in CLOSED
        assertThat(strict.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(standard.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(relaxed.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        // unknown falls back to STANDARD
        assertThat(unknown.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
    }
}
