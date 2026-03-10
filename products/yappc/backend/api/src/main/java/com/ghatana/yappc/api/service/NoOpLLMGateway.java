/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.DefaultTokenStream;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.TokenStream;
import com.ghatana.ai.llm.ToolCallResult;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op LLM Gateway for offline development without actual LLM integration.
 *
 * <p>Returns mock responses for all LLM requests. Used as a fallback when no
 * LLM API keys (OPENAI_API_KEY, ANTHROPIC_API_KEY) are configured.
 *
 * @deprecated Move to test fixtures or a dedicated dev-tools module. Production
 *     code should fail fast when LLM integration is required but not configured,
 *     rather than silently returning mock responses.
 * @doc.type class
 * @doc.purpose Mock LLM Gateway for offline development
 * @doc.layer product
 * @doc.pattern Service
 */
@Deprecated(since = "2.4.0", forRemoval = true)
public class NoOpLLMGateway implements LLMGateway {

    private static final Logger logger = LoggerFactory.getLogger(NoOpLLMGateway.class);
    private final MetricsCollector metricsCollector;

    public NoOpLLMGateway(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @NotNull
    @Override
    public Promise<CompletionResult> complete(@NotNull CompletionRequest request) {
        logger.debug("NoOp LLM complete request");
        
        String mockText = "This is a mock response from NoOpLLMGateway. " +
                "Actual LLM integration is not configured.";
        
        return Promise.of(CompletionResult.builder()
            .text(mockText)
            .finishReason("stop")
            .modelUsed("no-op")
            .tokensUsed(0)
            .promptTokens(0)
            .completionTokens(0)
            .latencyMs(0L)
            .build());
    }

    @NotNull
    @Override
    public Promise<CompletionResult> completeWithTools(
            @NotNull CompletionRequest request,
            @NotNull List<ToolDefinition> tools) {
        logger.debug("NoOp LLM completeWithTools request");
        return complete(request);
    }

    @NotNull
    @Override
    public Promise<CompletionResult> continueWithToolResults(
            @NotNull CompletionRequest request,
            @NotNull List<ToolCallResult> toolResults) {
        logger.debug("NoOp LLM continueWithToolResults request");
        return complete(request);
    }

    @NotNull
    @Override
    public Promise<EmbeddingResult> embed(@NotNull String text) {
        logger.debug("NoOp LLM embed request for text length: {}", text.length());
        // Return a mock embedding with empty vector
        return Promise.of(new EmbeddingResult(
            text,
            new float[0],
            "no-op"
        ));
    }

    @NotNull
    @Override
    public Promise<TokenStream> stream(@NotNull CompletionRequest request) {
        logger.debug("NoOp LLM stream request");

        DefaultTokenStream stream = new DefaultTokenStream();
        stream.emitToken("This is a mock streaming response from NoOpLLMGateway.");
        stream.complete();
        return Promise.of(stream);
    }

    @NotNull
    @Override
    public Promise<List<EmbeddingResult>> embedBatch(@NotNull List<String> texts) {
        logger.debug("NoOp LLM embedBatch request for {} texts", texts.size());
        List<EmbeddingResult> results = texts.stream()
            .map(text -> new EmbeddingResult(text, new float[0], "no-op"))
            .toList();
        return Promise.of(results);
    }

    @NotNull
    @Override
    public MetricsCollector getMetrics() {
        return metricsCollector;
    }

    @NotNull
    @Override
    public String getDefaultProvider() {
        return "no-op";
    }

    @NotNull
    @Override
    public List<String> getAvailableProviders() {
        return List.of("no-op");
    }

    @Override
    public boolean isProviderAvailable(@NotNull String providerName) {
        return "no-op".equals(providerName);
    }
}
