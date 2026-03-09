package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Gateway interface for multi-provider LLM access with routing and fallback.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a unified interface for accessing multiple LLM providers (OpenAI,
 * Anthropic, etc.) with intelligent routing based on task type, cost
 * optimization, and fallback handling.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * LLMGateway gateway = LLMGateway.builder()
 *     .addProvider("openai", openAIService)
 *     .addProvider("anthropic", anthropicService)
 *     .defaultProvider("openai")
 *     .build();
 *
 * // Simple completion
 * CompletionResult result = gateway.complete(request).getResult();
 *
 * // Completion with tools
 * CompletionResult result = gateway.completeWithTools(request, tools).getResult();
 *
 * // Generate embeddings
 * List<Float> embedding = gateway.embed("search query").getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Multi-provider LLM gateway with routing and fallback
 * @doc.layer infrastructure
 * @doc.pattern Gateway
 */
public interface LLMGateway {

    /**
     * Generates a completion using the default or routed provider.
     *
     * @param request The completion request
     * @return Promise completing with the result
     */
    Promise<CompletionResult> complete(CompletionRequest request);

    /**
     * Generates a completion with tool/function calling support.
     *
     * @param request The completion request
     * @param tools Available tools for the LLM
     * @return Promise completing with the result (may contain tool calls)
     */
    Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools);

    /**
     * Continues a conversation after tool execution.
     *
     * @param request The original request
     * @param toolResults Results from executed tool calls
     * @return Promise completing with the next response
     */
    Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> toolResults
    );

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text The text to embed
     * @return Promise completing with the embedding vector
     */
    Promise<EmbeddingResult> embed(String text);

    /**
     * Streams a completion token-by-token from the default or routed provider.
     *
     * <p>Unlike {@link #complete(CompletionRequest)}, the response is delivered
     * incrementally via the returned {@link TokenStream}. This is the preferred
     * API for interactive/chat use cases where time-to-first-token matters.
     *
     * <h3>Usage</h3>
     * <pre>{@code
     * gateway.stream(request).whenResult(stream -> {
     *     stream.onToken(token -> System.out.print(token));
     *     stream.onComplete(() -> System.out.println("\n[DONE]"));
     *     stream.onError(err -> log.error("Streaming error", err));
     * });
     * }</pre>
     *
     * @param request The completion request
     * @return Promise resolving to a TokenStream that delivers tokens incrementally
     * @since 2.4.0
     */
    Promise<TokenStream> stream(CompletionRequest request);

    /**
     * Generates embeddings for multiple texts.
     *
     * @param texts The texts to embed
     * @return Promise completing with the embedding results
     */
    Promise<List<EmbeddingResult>> embedBatch(List<String> texts);

    /**
     * Gets the metrics collector for this gateway.
     *
     * @return The metrics collector
     */
    MetricsCollector getMetrics();

    /**
     * Gets the name of the currently active default provider.
     *
     * @return The default provider name
     */
    String getDefaultProvider();

    /**
     * Gets the list of available provider names.
     *
     * @return List of provider names
     */
    List<String> getAvailableProviders();

    /**
     * Checks if a specific provider is available and healthy.
     *
     * @param providerName The provider name
     * @return true if the provider is available
     */
    boolean isProviderAvailable(String providerName);
}
