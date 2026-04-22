/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.framework.resilience;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AgentTurnPipeline;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.async.exception.AsyncTimeoutException;
import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AgentTurnPipeline} resilience features and {@link CancellationToken}.
 *
 * <p>Covers plan items 2.4.4 (CancellationToken) and 2.4.5 (AgentTimeoutTest). // GH-90000
 *
 * <h2>2.4.5 — Timeout contract</h2>
 * When {@link ResiliencePolicy#timeout()} is shorter than the generator's execution // GH-90000
 * time, {@link AgentTurnPipeline#executeWithPolicy} must fail with an
 * {@link AsyncTimeoutException} well before the generator finishes.
 *
 * <h2>2.4.4 — CancellationToken contract</h2>
 * {@link CancellationToken#cancel()} must set the cancelled flag atomically and // GH-90000
 * resolve the cancel-signal promise exactly once.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentTurnPipeline resilience: timeout, retry, cancellation
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("AgentTurnPipeline Resilience Tests (2.4.4 · 2.4.5) [GH-90000]")
class AgentTurnPipelineResilienceTest extends EventloopTestBase {

    // -------------------------------------------------------------------------
    // Helper — never-resolving promise simulates a hung generator
    // -------------------------------------------------------------------------

    /**
     * Returns a promise that will never resolve — it holds an open SettablePromise
     * whose resolver is never invoked. This simulates a generator that blocks
     * indefinitely (e.g., waiting for a slow LLM response). // GH-90000
     */
    private static <T> SettablePromise<T> hangingPromise() { // GH-90000
        return new SettablePromise<>();  // no resolver called → never completes // GH-90000
    }

    // =========================================================================
    // 2.4.5 — Agent Timeout Tests
    // =========================================================================

    @Nested
    @DisplayName("2.4.5 — Agent execution timeout [GH-90000]")
    class AgentTimeoutTests {

        @Test
        @DisplayName("timeout fires when reason phase never resolves (slow mock generator) [GH-90000]")
        void shouldTimeoutWhenGeneratorIsStuck() { // GH-90000
            // GIVEN: a pipeline whose reason phase hangs indefinitely
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("timeout-test-agent [GH-90000]")
                    .reason((input, ctx) -> hangingPromise()) // GH-90000
                    .build(); // GH-90000

            // 50 ms timeout — the hanging generator would block forever
            ResiliencePolicy policy = new ResiliencePolicy(1, 0L, 50L); // GH-90000
            AgentContext ctx = AgentContext.empty(); // GH-90000

            long start = System.currentTimeMillis(); // GH-90000

            // WHEN / THEN: executeWithPolicy must fail with AsyncTimeoutException
            assertThatThrownBy(() -> runPromise(() -> pipeline.executeWithPolicy("hello", ctx, policy))) // GH-90000
                    .satisfiesAnyOf( // GH-90000
                            // ActiveJ propagates AsyncTimeoutException directly for RuntimeException subtypes
                            e -> assertThat(e).isInstanceOf(AsyncTimeoutException.class), // GH-90000
                            // ...or wraps it in RuntimeException when it is a checked exception
                            e -> assertThat(e.getCause()).isInstanceOf(AsyncTimeoutException.class) // GH-90000
                    );

            long elapsed = System.currentTimeMillis() - start; // GH-90000

            // The timeout must fire well before the test infrastructure times out.
            // We allow a 3 s window (50 ms policy + test overhead + GC jitter). // GH-90000
            assertThat(elapsed) // GH-90000
                    .as("Timeout must fire in < 3 000 ms, not wait for the 5 s+ generator [GH-90000]")
                    .isLessThan(3_000L); // GH-90000
        }

        @Test
        @DisplayName("no timeout when reason phase resolves quickly [GH-90000]")
        void shouldSucceedWhenGeneratorIsWithinTimeout() { // GH-90000
            // GIVEN: a fast pipeline
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("fast-test-agent [GH-90000]")
                    .reason((input, ctx) -> Promise.of("# " + input)) // GH-90000
                    .build(); // GH-90000

            // 5 s timeout — generous for a synchronous generator
            ResiliencePolicy policy = new ResiliencePolicy(1, 0L, 5_000L); // GH-90000
            AgentContext ctx = AgentContext.empty(); // GH-90000

            // WHEN
            String result = runPromise(() -> pipeline.executeWithPolicy("hello", ctx, policy)); // GH-90000

            // THEN
            assertThat(result).isEqualTo("# hello [GH-90000]");
        }

        @Test
        @DisplayName("retry succeeds on second attempt after first times out [GH-90000]")
        void shouldSucceedOnRetryAfterFirstAttemptTimesOut() { // GH-90000
            // GIVEN: a generator that hangs on first call then succeeds on second
            AtomicBoolean firstCall = new AtomicBoolean(true); // GH-90000
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("retry-test-agent [GH-90000]")
                    .reason((input, ctx) -> { // GH-90000
                        if (firstCall.compareAndSet(true, false)) { // GH-90000
                            return hangingPromise(); // first attempt → hangs // GH-90000
                        }
                        return Promise.of("retry-success [GH-90000]"); // second attempt → fast
                    })
                    .build(); // GH-90000

            // 2 attempts, 50 ms per-attempt timeout
            ResiliencePolicy policy = new ResiliencePolicy(2, 0L, 50L); // GH-90000
            AgentContext ctx = AgentContext.empty(); // GH-90000

            // WHEN
            String result = runPromise(() -> pipeline.executeWithPolicy("input", ctx, policy)); // GH-90000

            // THEN
            assertThat(result).isEqualTo("retry-success [GH-90000]");
        }

        @Test
        @DisplayName("all retries exhausted propagates last timeout exception [GH-90000]")
        void shouldFailAfterAllRetriesExhausted() { // GH-90000
            // GIVEN: generator always hangs
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("exhaust-test-agent [GH-90000]")
                    .reason((input, ctx) -> hangingPromise()) // GH-90000
                    .build(); // GH-90000

            // 2 attempts, 50 ms each
            ResiliencePolicy policy = new ResiliencePolicy(2, 0L, 50L); // GH-90000
            AgentContext ctx = AgentContext.empty(); // GH-90000

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> pipeline.executeWithPolicy("x", ctx, policy))) // GH-90000
                    .satisfiesAnyOf( // GH-90000
                            e -> assertThat(e).isInstanceOf(AsyncTimeoutException.class), // GH-90000
                            e -> assertThat(e.getCause()).isInstanceOf(AsyncTimeoutException.class) // GH-90000
                    );
        }
    }

    // =========================================================================
    // 2.4.4 — CancellationToken Tests
    // =========================================================================

    @Nested
    @DisplayName("2.4.4 — CancellationToken [GH-90000]")
    class CancellationTokenTests {

        @Test
        @DisplayName("fresh token is not cancelled [GH-90000]")
        void freshTokenIsNotCancelled() { // GH-90000
            CancellationToken token = CancellationToken.create(); // GH-90000
            assertThat(token.isCancelled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("cancel() sets isCancelled to true [GH-90000]")
        void cancelSetsFlag() { // GH-90000
            CancellationToken token = CancellationToken.create(); // GH-90000
            token.cancel(); // GH-90000
            assertThat(token.isCancelled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("cancel() returns a resolved promise [GH-90000]")
        void cancelReturnsResolvedPromise() { // GH-90000
            CancellationToken token = CancellationToken.create(); // GH-90000
            Promise<Void> result = token.cancel(); // GH-90000
            // The returned promise is Promise.complete() — it is already resolved // GH-90000
            assertThat(result.isResult()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("asCancelSignal resolves when cancel() is called [GH-90000]")
        void cancelSignalResolvesOnCancel() { // GH-90000
            CancellationToken token = CancellationToken.create(); // GH-90000
            Promise<Void> signal = token.asCancelSignal(); // GH-90000

            // Before cancel: signal is not yet resolved
            assertThat(signal.isComplete()).isFalse(); // GH-90000

            // After cancel: signal resolves
            token.cancel(); // GH-90000
            assertThat(signal.isResult()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("cancel() is idempotent — multiple calls change nothing after first [GH-90000]")
        void cancelIsIdempotent() { // GH-90000
            CancellationToken token = CancellationToken.create(); // GH-90000
            token.cancel(); // GH-90000
            token.cancel();  // second call is no-op // GH-90000

            assertThat(token.isCancelled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("alreadyCancelled() factory creates a pre-cancelled token [GH-90000]")
        void alreadyCancelledFactory() { // GH-90000
            CancellationToken token = CancellationToken.alreadyCancelled(); // GH-90000
            assertThat(token.isCancelled()).isTrue(); // GH-90000
            assertThat(token.asCancelSignal().isResult()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("pipeline phase can check cancellation and short-circuit [GH-90000]")
        void phaseCanShortCircuitOnCancellation() { // GH-90000
            CancellationToken token = CancellationToken.alreadyCancelled(); // GH-90000

            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("cancel-aware-agent [GH-90000]")
                    .reason((input, ctx) -> { // GH-90000
                        if (token.isCancelled()) { // GH-90000
                            return Promise.ofException( // GH-90000
                                    new RuntimeException("Cancelled before processing [GH-90000]"));
                        }
                        return Promise.of("should-not-reach [GH-90000]");
                    })
                    .build(); // GH-90000

            ResiliencePolicy policy = ResiliencePolicy.defaultPolicy(); // GH-90000
            AgentContext ctx = AgentContext.empty(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> pipeline.executeWithPolicy("x", ctx, policy))) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("Cancelled [GH-90000]");
        }

        @Test
        @DisplayName("A-2: cancellation signal propagates to waiting phase")
        void cancellationSignalPropagatesToPipelinePhase() {
            CancellationToken token = CancellationToken.create();

            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                .<String, String>builder("cancel-propagation-agent")
                .reason((input, ctx) -> token.asCancelSignal().map(ignored -> "cancelled:" + input))
                .build();

            String result = runPromise(() -> {
                Promise<String> execution = pipeline.executeWithPolicy("payload", AgentContext.empty(), ResiliencePolicy.defaultPolicy());
                token.cancel();
                return execution;
            });

            assertThat(result).isEqualTo("cancelled:payload");
        }
    }
}
