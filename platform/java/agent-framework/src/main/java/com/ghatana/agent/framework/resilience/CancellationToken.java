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

import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A cooperative cancellation signal for agent executions.
 *
 * <p>A {@code CancellationToken} gives callers a lightweight handle to signal
 * that an ongoing agent turn should abort at the next safe checkpoint. It is
 * intentionally simple — checks are cooperative, not preemptive:
 * <ul>
 *   <li>The generator or any lifecycle phase can poll {@link #isCancelled()} and
 *       short-circuit by returning an exception promise.</li>
 *   <li>{@link #cancel()} resolves the optional {@link #asCancelSignal()} promise so
 *       that reactive code can chain cleanup steps.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * CancellationToken token = new CancellationToken();
 *
 * // Kick off a long-running pipeline
 * Promise<String> work = pipeline.executeWithPolicy(input, ctx, policy);
 *
 * // Some time later, request cancellation from another coroutine:
 * token.cancel().whenComplete((v, e) -> log.info("cancellation issued"));
 *
 * // Inside the generator, poll the token:
 * if (token.isCancelled()) {
 *     return Promise.ofException(new CancelledException("work aborted by caller"));
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Cooperative cancellation flag for agent turn pipelines
 * @doc.layer framework
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle reason
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final SettablePromise<Void> cancelSignal = new SettablePromise<>();

    /**
     * Signals cancellation.
     *
     * <p>After this call:
     * <ul>
     *   <li>{@link #isCancelled()} returns {@code true}.</li>
     *   <li>The promise returned by {@link #asCancelSignal()} is resolved.</li>
     * </ul>
     *
     * <p>Calling {@code cancel()} more than once is a no-op — only the first
     * invocation resolves the signal promise.
     *
     * @return a completed {@link Promise} that callers can chain cleanup work onto
     */
    @NotNull
    public Promise<Void> cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelSignal.set(null);
        }
        return Promise.complete();
    }

    /**
     * Returns {@code true} if cancellation has been requested.
     *
     * <p>Use this in tight loops or at pipeline phase boundaries to detect
     * early-exit conditions without blocking.
     *
     * @return {@code true} once {@link #cancel()} has been called
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Returns a {@link Promise} that resolves when {@link #cancel()} is called.
     *
     * <p>If cancellation has already been requested before this method is called,
     * the returned promise will be resolved immediately.
     *
     * <p>This method allows reactive code to compose cleanup handlers:
     * <pre>{@code
     * token.asCancelSignal()
     *      .then(() -> cleanupResources())
     *      .whenComplete((v, e) -> log.info("resources released"));
     * }</pre>
     *
     * @return a promise that resolves to {@code null} on cancellation
     */
    @NotNull
    public Promise<Void> asCancelSignal() {
        return cancelSignal;
    }

    /**
     * Creates a new token that has been pre-cancelled.
     * Useful in tests that verify cancellation-aware code short-circuits immediately.
     *
     * @return a {@code CancellationToken} with {@code isCancelled() == true}
     */
    @NotNull
    public static CancellationToken alreadyCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        return token;
    }

    /**
     * Creates a new uncancelled token.
     *
     * @return a fresh {@code CancellationToken}
     */
    @NotNull
    public static CancellationToken create() {
        return new CancellationToken();
    }

    @Override
    public String toString() {
        return "CancellationToken{cancelled=" + cancelled.get() + "}";
    }
}
