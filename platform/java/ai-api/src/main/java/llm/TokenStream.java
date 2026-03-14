/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.ai.llm;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Represents a streaming token response from an LLM provider.
 *
 * <p>Unlike the batch {@link CompletionResult}, a {@code TokenStream} delivers
 * tokens incrementally as they are generated. This enables:
 * <ul>
 *   <li>Lower time-to-first-token latency</li>
 *   <li>Progressive UI rendering</li>
 *   <li>Early termination if the output is clearly wrong</li>
 *   <li>Memory efficiency for very long outputs</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * gateway.stream(request).whenResult(stream -> {
 *     stream.onToken(token -> System.out.print(token));
 *     stream.onComplete(() -> System.out.println("\n[DONE]"));
 *     stream.onError(err -> System.err.println("Error: " + err));
 * });
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * Callbacks are invoked on the ActiveJ eventloop thread. Do not block.
 *
 * @doc.type interface
 * @doc.purpose Streaming LLM token response
 * @doc.layer platform
 * @doc.pattern Observer / Callback
 *
 * @since 2.4.0
 */
public interface TokenStream {

    /**
     * Registers a callback invoked for each generated token.
     *
     * @param tokenConsumer receives each token fragment as a String
     * @return this stream for chaining
     */
    @NotNull TokenStream onToken(@NotNull Consumer<String> tokenConsumer);

    /**
     * Registers a callback invoked when generation completes normally.
     *
     * @param onComplete called once when the stream finishes
     * @return this stream for chaining
     */
    @NotNull TokenStream onComplete(@NotNull Runnable onComplete);

    /**
     * Registers a callback invoked when an error occurs during generation.
     *
     * @param onError receives the error
     * @return this stream for chaining
     */
    @NotNull TokenStream onError(@NotNull Consumer<Throwable> onError);

    /**
     * Cancels the stream. No further tokens will be delivered.
     * Providers that support cancellation will stop generation.
     */
    void cancel();

    /**
     * Returns the accumulated text so far. Useful for collecting
     * the full response after the stream completes.
     *
     * @return all tokens concatenated
     */
    @NotNull String getAccumulatedText();
}
