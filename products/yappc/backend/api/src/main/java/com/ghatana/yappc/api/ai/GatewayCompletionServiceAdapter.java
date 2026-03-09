/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that exposes {@link LLMGateway} as a {@link CompletionService}.
 *
 * <p>Delegates all completion requests to the multi-provider gateway, inheriting
 * its routing, fallback, and circuit-breaker behaviour. This avoids duplicating
 * provider wiring and ensures a single LLM integration path throughout the
 * application.</p>
 *
 * @doc.type class
 * @doc.purpose Adapts LLMGateway to CompletionService contract
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class GatewayCompletionServiceAdapter implements CompletionService {

    private final LLMGateway gateway;
    private final MetricsCollector metrics;

    /**
     * Creates a new adapter.
     *
     * @param gateway the multi-provider LLM gateway
     * @param metrics the metrics collector for instrumentation
     */
    public GatewayCompletionServiceAdapter(LLMGateway gateway, MetricsCollector metrics) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        return gateway.complete(request);
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        return Promises.toList(requests.stream().map(gateway::complete).toList());
    }

    @Override
    public LLMConfiguration getConfig() {
        // Gateway manages its own per-provider configs; return a summary config.
        return LLMConfiguration.builder()
                .modelName(gateway.getDefaultProvider())
                .build();
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return metrics;
    }

    @Override
    public String getProviderName() {
        return "gateway(" + gateway.getDefaultProvider() + ")";
    }
}
