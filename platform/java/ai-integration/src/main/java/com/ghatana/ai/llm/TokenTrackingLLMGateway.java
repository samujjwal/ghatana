/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;

/**
 * LLMGateway decorator that tracks token usage.
 * Wraps any LLMGateway implementation and records usage metrics.
 *
 * @doc.type class
 * @doc.purpose Decorator for tracking LLM token usage
 * @doc.layer infrastructure
 * @doc.pattern Decorator
 */
public final class TokenTrackingLLMGateway implements LLMGateway {

    private final LLMGateway delegate;
    private final TokenUsageTrackingService trackingService;

    /**
     * Creates a token tracking gateway decorator.
     *
     * @param delegate the underlying LLM gateway
     * @param trackingService the token usage tracking service
     */
    public TokenTrackingLLMGateway(LLMGateway delegate, TokenUsageTrackingService trackingService) {
        this.delegate = delegate;
        this.trackingService = trackingService;
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        String tenantId = extractTenantId(request);
        String provider = delegate.getDefaultProvider();
        
        return delegate.complete(request)
            .then(result -> {
                trackingService.recordUsage(tenantId, provider, result.getModelUsed(), result);
                return Promise.of(result);
            });
    }

    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
        String tenantId = extractTenantId(request);
        String provider = delegate.getDefaultProvider();
        
        return delegate.completeWithTools(request, tools)
            .then(result -> {
                trackingService.recordUsage(tenantId, provider, result.getModelUsed(), result);
                return Promise.of(result);
            });
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) {
        String tenantId = extractTenantId(request);
        String provider = delegate.getDefaultProvider();
        
        return delegate.continueWithToolResults(request, toolResults)
            .then(result -> {
                trackingService.recordUsage(tenantId, provider, result.getModelUsed(), result);
                return Promise.of(result);
            });
    }

    @Override
    public Promise<EmbeddingResult> embed(String text) {
        // Embedding typically has different pricing, track separately if needed
        return delegate.embed(text);
    }

    @Override
    public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) {
        return delegate.embedBatch(texts);
    }

    @Override
    public Promise<TokenStream> stream(CompletionRequest request) {
        String tenantId = extractTenantId(request);
        String provider = delegate.getDefaultProvider();
        
        return delegate.stream(request)
            .then(stream -> {
                return Promise.of(new TokenStream() {
                    private boolean tracked;

                    @Override
                    public TokenStream onToken(java.util.function.Consumer<String> callback) {
                        stream.onToken(callback);
                        return this;
                    }

                    @Override
                    public TokenStream onComplete(java.lang.Runnable callback) {
                        stream.onComplete(() -> {
                            if (!tracked) {
                                tracked = true;
                                String accumulatedText = stream.getAccumulatedText();
                                int estimatedTokens = accumulatedText.length() / 4;
                                CompletionResult result = CompletionResult.builder()
                                    .text(accumulatedText)
                                    .tokensUsed(estimatedTokens)
                                    .promptTokens(estimatedTokens / 2)
                                    .completionTokens(estimatedTokens / 2)
                                    .modelUsed("streaming-model")
                                    .build();
                                trackingService.recordUsage(tenantId, provider, result.getModelUsed(), result);
                            }
                            callback.run();
                        });
                        return this;
                    }

                    @Override
                    public TokenStream onError(java.util.function.Consumer<Throwable> callback) {
                        stream.onError(callback);
                        return this;
                    }

                    @Override
                    public void cancel() {
                        stream.cancel();
                    }

                    @Override
                    public String getAccumulatedText() {
                        return stream.getAccumulatedText();
                    }
                });
            });
    }

    @Override
    public MetricsCollector getMetrics() {
        return delegate.getMetrics();
    }

    @Override
    public String getDefaultProvider() {
        return delegate.getDefaultProvider();
    }

    @Override
    public List<String> getAvailableProviders() {
        return delegate.getAvailableProviders();
    }

    @Override
    public boolean isProviderAvailable(String providerName) {
        return delegate.isProviderAvailable(providerName);
    }

    /**
     * Gets the underlying delegate gateway.
     *
     * @return the delegate gateway
     */
    public LLMGateway getDelegate() {
        return delegate;
    }

    /**
     * Gets the token usage tracking service.
     *
     * @return the tracking service
     */
    public TokenUsageTrackingService getTrackingService() {
        return trackingService;
    }

    /**
     * Extracts tenant ID from request metadata.
     *
     * @param request the completion request
     * @return tenant ID or "default" if not found
     */
    private String extractTenantId(CompletionRequest request) {
        if (request.getMetadata() != null) {
            Object tenantId = request.getMetadata().get("tenantId");
            if (tenantId != null) {
                return tenantId.toString();
            }
        }
        return "default";
    }
}
