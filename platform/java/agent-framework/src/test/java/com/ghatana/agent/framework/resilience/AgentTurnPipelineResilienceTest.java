/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * <p>Covers plan items 2.4.4 (CancellationToken) and 2.4.5 (AgentTimeoutTest).
 *
 * <h2>2.4.5 — Timeout contract</h2>
 * When {@link ResiliencePolicy#timeout()} is shorter than the generator's execution
 * time, {@link AgentTurnPipeline#executeWithPolicy} must fail with an
 * {@link AsyncTimeoutException} well before the generator finishes.
 *
 * <h2>2.4.4 — CancellationToken contract</h2>
 * {@link CancellationToken#cancel()} must set the cancelled flag atomically and
 * resolve the cancel-signal promise exactly once.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentTurnPipeline resilience: timeout, retry, cancellation
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("AgentTurnPipeline Resilience Tests (2.4.4 · 2.4.5)")
class AgentTurnPipelineResilienceTest extends EventloopTestBase {

    // -------------------------------------------------------------------------
    // Helper — never-resolving promise simulates a hung generator
    // -------------------------------------------------------------------------

    /**
     * Returns a promise that will never resolve — it holds an open SettablePromise
     * whose resolver is never invoked. This simulates a generator that blocks
     * indefinitely (e.g., waiting for a slow LLM response).
     */
    private static <T> SettablePromise<T> hangingPromise() {
        return new SettablePromise<>();  // no resolver called → never completes
    }

    // =========================================================================
    // 2.4.5 — Agent Timeout Tests
    // =========================================================================

    @Nested
    @DisplayName("2.4.5 — Agent execution timeout")
    class AgentTimeoutTests {

        @Test
        @DisplayName("timeout fires when reason phase never resolves (slow mock generator)")
        void shouldTimeoutWhenGeneratorIsStuck() {
            // GIVEN: a pipeline whose reason phase hangs indefinitely
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("timeout-test-agent")
                    .reason((input, ctx) -> hangingPromise())
                    .build();

            // 50 ms timeout — the hanging generator would block forever
            ResiliencePolicy policy = new ResiliencePolicy(1, 0L, 50L);
            AgentContext ctx = AgentContext.empty();

            long start = System.currentTimeMillis();

            // WHEN / THEN: executeWithPolicy must fail with AsyncTimeoutException
            assertThatThrownBy(() -> runPromise(() -> pipeline.executeWithPolicy("hello", ctx, policy)))
                    .satisfiesAnyOf(
                            // ActiveJ propagates AsyncTimeoutException directly for RuntimeException subtypes
                            e -> assertThat(e).isInstanceOf(AsyncTimeoutException.class),
                            // ...or wraps it in RuntimeException when it is a checked exception
                            e -> assertThat(e.getCause()).isInstanceOf(AsyncTimeoutException.class)
                    );

            long elapsed = System.currentTimeMillis() - start;

            // The timeout must fire well before the test infrastructure times out.
            // We allow a 3 s window (50 ms policy + test overhead + GC jitter).
            assertThat(elapsed)
                    .as("Timeout must fire in < 3 000 ms, not wait for the 5 s+ generator")
                    .isLessThan(3_000L);
        }

        @Test
        @DisplayName("no timeout when reason phase resolves quickly")
        void shouldSucceedWhenGeneratorIsWithinTimeout() {
            // GIVEN: a fast pipeline
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("fast-test-agent")
                    .reason((input, ctx) -> Promise.of("# " + input))
                    .build();

            // 5 s timeout — generous for a synchronous generator
            ResiliencePolicy policy = new ResiliencePolicy(1, 0L, 5_000L);
            AgentContext ctx = AgentContext.empty();

            // WHEN
            String result = runPromise(() -> pipeline.executeWithPolicy("hello", ctx, policy));

            // THEN
            assertThat(result).isEqualTo("# hello");
        }

        @Test
        @DisplayName("retry succeeds on second attempt after first times out")
        void shouldSucceedOnRetryAfterFirstAttemptTimesOut() {
            // GIVEN: a generator that hangs on first call then succeeds on second
            AtomicBoolean firstCall = new AtomicBoolean(true);
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("retry-test-agent")
                    .reason((input, ctx) -> {
                        if (firstCall.compareAndSet(true, false)) {
                            return hangingPromise(); // first attempt → hangs
                        }
                        return Promise.of("retry-success"); // second attempt → fast
                    })
                    .build();

            // 2 attempts, 50 ms per-attempt timeout
            ResiliencePolicy policy = new ResiliencePolicy(2, 0L, 50L);
            AgentContext ctx = AgentContext.empty();

            // WHEN
            String result = runPromise(() -> pipeline.executeWithPolicy("input", ctx, policy));

            // THEN
            assertThat(result).isEqualTo("retry-success");
        }

        @Test
        @DisplayName("all retries exhausted propagates last timeout exception")
        void shouldFailAfterAllRetriesExhausted() {
            // GIVEN: generator always hangs
            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("exhaust-test-agent")
                    .reason((input, ctx) -> hangingPromise())
                    .build();

            // 2 attempts, 50 ms each
            ResiliencePolicy policy = new ResiliencePolicy(2, 0L, 50L);
            AgentContext ctx = AgentContext.empty();

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> pipeline.executeWithPolicy("x", ctx, policy)))
                    .satisfiesAnyOf(
                            e -> assertThat(e).isInstanceOf(AsyncTimeoutException.class),
                            e -> assertThat(e.getCause()).isInstanceOf(AsyncTimeoutException.class)
                    );
        }
    }

    // =========================================================================
    // 2.4.4 — CancellationToken Tests
    // =========================================================================

    @Nested
    @DisplayName("2.4.4 — CancellationToken")
    class CancellationTokenTests {

        @Test
        @DisplayName("fresh token is not cancelled")
        void freshTokenIsNotCancelled() {
            CancellationToken token = CancellationToken.create();
            assertThat(token.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("cancel() sets isCancelled to true")
        void cancelSetsFlag() {
            CancellationToken token = CancellationToken.create();
            token.cancel();
            assertThat(token.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("cancel() returns a resolved promise")
        void cancelReturnsResolvedPromise() {
            CancellationToken token = CancellationToken.create();
            Promise<Void> result = token.cancel();
            // The returned promise is Promise.complete() — it is already resolved
            assertThat(result.isResult()).isTrue();
        }

        @Test
        @DisplayName("asCancelSignal resolves when cancel() is called")
        void cancelSignalResolvesOnCancel() {
            CancellationToken token = CancellationToken.create();
            Promise<Void> signal = token.asCancelSignal();

            // Before cancel: signal is not yet resolved
            assertThat(signal.isComplete()).isFalse();

            // After cancel: signal resolves
            token.cancel();
            assertThat(signal.isResult()).isTrue();
        }

        @Test
        @DisplayName("cancel() is idempotent — multiple calls change nothing after first")
        void cancelIsIdempotent() {
            CancellationToken token = CancellationToken.create();
            token.cancel();
            token.cancel();  // second call is no-op

            assertThat(token.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("alreadyCancelled() factory creates a pre-cancelled token")
        void alreadyCancelledFactory() {
            CancellationToken token = CancellationToken.alreadyCancelled();
            assertThat(token.isCancelled()).isTrue();
            assertThat(token.asCancelSignal().isResult()).isTrue();
        }

        @Test
        @DisplayName("pipeline phase can check cancellation and short-circuit")
        void phaseCanShortCircuitOnCancellation() {
            CancellationToken token = CancellationToken.alreadyCancelled();

            AgentTurnPipeline<String, String> pipeline = AgentTurnPipeline
                    .<String, String>builder("cancel-aware-agent")
                    .reason((input, ctx) -> {
                        if (token.isCancelled()) {
                            return Promise.ofException(
                                    new RuntimeException("Cancelled before processing"));
                        }
                        return Promise.of("should-not-reach");
                    })
                    .build();

            ResiliencePolicy policy = ResiliencePolicy.defaultPolicy();
            AgentContext ctx = AgentContext.empty();

            assertThatThrownBy(() -> runPromise(() -> pipeline.executeWithPolicy("x", ctx, policy)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Cancelled");
        }
    }
}
