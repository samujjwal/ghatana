/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Service Module
 */
package com.ghatana.yappc.services.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolAwareCompletionService;
import com.ghatana.ai.llm.ToolCallResult;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Adapts {@link OllamaCompletionService} to the {@link ToolAwareCompletionService} contract.
 *
 * <p>Ollama's HTTP API does support tool calling for compatible models (llama3.2+), but
 * the current platform {@link OllamaCompletionService} only implements the base
 * {@link com.ghatana.ai.llm.CompletionService} interface. This adapter bridges the gap so
 * that Ollama can be registered as an {@link com.ghatana.ai.llm.LLMGateway} provider.
 *
 * <p>Tool-calling methods ({@link #completeWithTools} and {@link #continueWithToolResults})
 * fall back to the standard completion path without tool context. This is safe for
 * development and testing scenarios where tool results are handled by the agent loop.
 *
 * @doc.type class
 * @doc.purpose Adapter making OllamaCompletionService compatible with ToolAwareCompletionService
 * @doc.layer product
 * @doc.pattern Adapter
 */
final class ToolAwareOllamaCompletionService implements ToolAwareCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ToolAwareOllamaCompletionService.class);

    private final OllamaCompletionService delegate;

    ToolAwareOllamaCompletionService(OllamaCompletionService delegate) {
        this.delegate = delegate;
    }

    // ── CompletionService ─────────────────────────────────────────────────────

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        return delegate.complete(request);
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        return delegate.completeBatch(requests);
    }

    @Override
    public LLMConfiguration getConfig() {
        return delegate.getConfig();
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return delegate.getMetricsCollector();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    // ── ToolAwareCompletionService ────────────────────────────────────────────

    /**
     * Delegates to the standard {@link #complete} path. Ollama tool-call format
     * is not yet wired in the platform client; tool definitions are dropped.
     */
    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request,
                                                       List<ToolDefinition> tools) {
        log.debug("Ollama provider ignoring {} tool definitions — falling back to standard completion",
                tools.size());
        return delegate.complete(request);
    }

    /**
     * Delegates to the standard {@link #complete} path. Tool results are not
     * forwarded to Ollama in this adapter; the agent loop should handle them.
     */
    @Override
    public Promise<CompletionResult> continueWithToolResults(CompletionRequest request,
                                                              List<ToolCallResult> toolResults) {
        log.debug("Ollama provider ignoring {} tool results — continuing with standard completion",
                toolResults.size());
        return delegate.complete(request);
    }
}
