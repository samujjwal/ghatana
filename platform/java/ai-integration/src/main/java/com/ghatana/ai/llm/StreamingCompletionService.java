/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.ai.llm;

import io.activej.promise.Promise;

/**
 * Optional capability interface for LLM providers that support native
 * token-by-token streaming.
 *
 * <p>Providers that implement this interface deliver tokens incrementally
 * via a {@link DefaultTokenStream}. Providers that do <em>not</em> implement
 * this interface will have their responses adapted automatically by the
 * gateway (batch completion emitted as a single-token stream).
 *
 * <h2>Implementation Contract</h2>
 * <ol>
 *   <li>Create a {@link DefaultTokenStream} instance.</li>
 *   <li>Return it immediately inside a resolved {@link Promise}.</li>
 *   <li>On a background / IO thread, call
 *       {@link DefaultTokenStream#emitToken(String)} for each chunk,
 *       then {@link DefaultTokenStream#complete()} or
 *       {@link DefaultTokenStream#error(Throwable)}.</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class MyStreamingProvider implements ToolAwareCompletionService,
 *                                             StreamingCompletionService {
 *     @Override
 *     public Promise<TokenStream> stream(CompletionRequest request) {
 *         DefaultTokenStream ts = new DefaultTokenStream();
 *         // kick off async HTTP SSE call that feeds ts.emitToken(...)
 *         startSseCall(request, ts);
 *         return Promise.of(ts);
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose SPI for LLM providers with native streaming
 * @doc.layer platform
 * @doc.pattern Capability / SPI
 *
 * @since 2.4.0
 */
public interface StreamingCompletionService {

    /**
     * Streams a completion token-by-token.
     *
     * @param request The completion request
     * @return Promise that resolves immediately to a {@link TokenStream};
     *         tokens arrive asynchronously via callbacks
     */
    Promise<TokenStream> stream(CompletionRequest request);
}
