package com.ghatana.ai.llm;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import java.util.List;

/**
 * Service for generating LLM completions.
 * @doc.type interface
 * @doc.purpose Abstraction for LLM completion services
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public interface CompletionService {
    Promise<CompletionResult> complete(CompletionRequest request);
    Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests);
    LLMConfiguration getConfig();
    MetricsCollector getMetricsCollector();
    String getProviderName();
}

