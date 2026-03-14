/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.ai.llm;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Default in-memory implementation of {@link TokenStream}.
 *
 * <p>Provider implementations create a {@code DefaultTokenStream}, register
 * it as the response, and call {@link #emitToken(String)}, {@link #complete()},
 * or {@link #error(Throwable)} as tokens arrive from the provider.
 *
 * @doc.type class
 * @doc.purpose Default TokenStream implementation for LLM providers
 * @doc.layer platform
 * @doc.pattern Observer
 *
 * @since 2.4.0
 */
public final class DefaultTokenStream implements TokenStream {

    private final List<Consumer<String>> tokenConsumers = new ArrayList<>();
    private final List<Runnable> completeCallbacks = new ArrayList<>();
    private final List<Consumer<Throwable>> errorCallbacks = new ArrayList<>();
    private final StringBuilder accumulated = new StringBuilder();
    private volatile boolean cancelled;
    private volatile boolean completed;

    @Override
    @NotNull
    public TokenStream onToken(@NotNull Consumer<String> tokenConsumer) {
        Objects.requireNonNull(tokenConsumer);
        tokenConsumers.add(tokenConsumer);
        return this;
    }

    @Override
    @NotNull
    public TokenStream onComplete(@NotNull Runnable onComplete) {
        Objects.requireNonNull(onComplete);
        completeCallbacks.add(onComplete);
        return this;
    }

    @Override
    @NotNull
    public TokenStream onError(@NotNull Consumer<Throwable> onError) {
        Objects.requireNonNull(onError);
        errorCallbacks.add(onError);
        return this;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    @NotNull
    public String getAccumulatedText() {
        return accumulated.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Producer-side API (called by LLM provider implementations)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emits a single token to all registered consumers.
     *
     * @param token the token fragment
     */
    public void emitToken(@NotNull String token) {
        if (cancelled || completed) return;
        accumulated.append(token);
        for (Consumer<String> consumer : tokenConsumers) {
            consumer.accept(token);
        }
    }

    /**
     * Signals that the stream has completed successfully.
     */
    public void complete() {
        if (cancelled || completed) return;
        completed = true;
        for (Runnable callback : completeCallbacks) {
            callback.run();
        }
    }

    /**
     * Signals that an error occurred during streaming.
     *
     * @param error the error
     */
    public void error(@NotNull Throwable error) {
        if (cancelled || completed) return;
        completed = true;
        for (Consumer<Throwable> callback : errorCallbacks) {
            callback.accept(error);
        }
    }

    /** Whether the stream was cancelled by the consumer. */
    public boolean isCancelled() { return cancelled; }

    /** Whether the stream has completed (success or error). */
    public boolean isCompleted() { return completed; }
}
