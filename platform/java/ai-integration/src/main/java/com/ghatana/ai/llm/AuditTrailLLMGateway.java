/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * LLMGateway decorator that logs full audit trail of LLM calls.
 * Wraps any LLMGateway implementation and records prompts, responses, and decisions.
 *
 * @doc.type class
 * @doc.purpose Decorator for full LLM call audit trail logging
 * @doc.layer infrastructure
 * @doc.pattern Decorator
 */
public final class AuditTrailLLMGateway implements LLMGateway {

    private final LLMGateway delegate;
    private final LLMAuditTrailService auditService;

    /**
     * Creates an audit trail gateway decorator.
     *
     * @param delegate the underlying LLM gateway
     * @param auditService the audit trail service
     */
    public AuditTrailLLMGateway(LLMGateway delegate, LLMAuditTrailService auditService) {
        this.delegate = delegate;
        this.auditService = auditService;
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        String tenantId = extractTenantId(request);
        String requestId = UUID.randomUUID().toString();
        String provider = delegate.getDefaultProvider();
        long startTime = System.currentTimeMillis();

        // Capture prompt from request
        String prompt = extractPrompt(request);

        return delegate.complete(request)
            .then(result -> {
                long latencyMs = System.currentTimeMillis() - startTime;
                
                auditService.logCall(
                    tenantId,
                    requestId,
                    provider,
                    result.getModelUsed(),
                    prompt,
                    result.getText(),
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    latencyMs,
                    java.util.Map.of(
                        "finishReason", result.getFinishReason(),
                        "hasToolCalls", result.hasToolCalls()
                    )
                );
                
                return Promise.of(result);
            });
    }

    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
        String tenantId = extractTenantId(request);
        String requestId = UUID.randomUUID().toString();
        String provider = delegate.getDefaultProvider();
        long startTime = System.currentTimeMillis();

        String prompt = extractPrompt(request);

        return delegate.completeWithTools(request, tools)
            .then(result -> {
                long latencyMs = System.currentTimeMillis() - startTime;
                
                auditService.logCall(
                    tenantId,
                    requestId,
                    provider,
                    result.getModelUsed(),
                    prompt,
                    result.getText(),
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    latencyMs,
                    java.util.Map.of(
                        "finishReason", result.getFinishReason(),
                        "hasToolCalls", result.hasToolCalls(),
                        "toolCount", result.getToolCalls().size(),
                        "toolsProvided", tools.size()
                    )
                );
                
                return Promise.of(result);
            });
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) {
        String tenantId = extractTenantId(request);
        String requestId = UUID.randomUUID().toString();
        String provider = delegate.getDefaultProvider();
        long startTime = System.currentTimeMillis();

        String prompt = extractPrompt(request);

        return delegate.continueWithToolResults(request, toolResults)
            .then(result -> {
                long latencyMs = System.currentTimeMillis() - startTime;
                
                auditService.logCall(
                    tenantId,
                    requestId,
                    provider,
                    result.getModelUsed(),
                    prompt,
                    result.getText(),
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    latencyMs,
                    java.util.Map.of(
                        "finishReason", result.getFinishReason(),
                        "hasToolCalls", result.hasToolCalls(),
                        "toolResultCount", toolResults.size(),
                        "isContinuation", true
                    )
                );
                
                return Promise.of(result);
            });
    }

    @Override
    public Promise<EmbeddingResult> embed(String text) {
        // Embedding calls can also be audited if needed
        return delegate.embed(text);
    }

    @Override
    public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) {
        return delegate.embedBatch(texts);
    }

    @Override
    public Promise<TokenStream> stream(CompletionRequest request) {
        String tenantId = extractTenantId(request);
        String requestId = UUID.randomUUID().toString();
        String provider = delegate.getDefaultProvider();
        long startTime = System.currentTimeMillis();
        String prompt = extractPrompt(request);

        return delegate.stream(request)
            .then(stream -> {
                return Promise.of(new TokenStream() {
                    private boolean audited;

                    @Override
                    public TokenStream onToken(java.util.function.Consumer<String> callback) {
                        stream.onToken(callback);
                        return this;
                    }

                    @Override
                    public TokenStream onComplete(java.lang.Runnable callback) {
                        stream.onComplete(() -> {
                            long latencyMs = System.currentTimeMillis() - startTime;
                            if (!audited) {
                                audited = true;
                                String accumulatedText = stream.getAccumulatedText();
                                int estimatedTokens = accumulatedText.length() / 4;

                                auditService.logCall(
                                    tenantId,
                                    requestId,
                                    provider,
                                    "streaming-model",
                                    prompt,
                                    accumulatedText,
                                    estimatedTokens / 2,
                                    estimatedTokens / 2,
                                    latencyMs,
                                    java.util.Map.of("isStreaming", true)
                                );
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
     * Gets the audit trail service.
     *
     * @return the audit service
     */
    public LLMAuditTrailService getAuditService() {
        return auditService;
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

    /**
     * Extracts prompt text from request.
     *
     * @param request the completion request
     * @return prompt text or empty string
     */
    private String extractPrompt(CompletionRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            // Concatenate all messages to form the prompt
            return request.getMessages().stream()
                .map(Object::toString)
                .reduce("", (a, b) -> a + "\n" + b);
        }
        return "";
    }
}
