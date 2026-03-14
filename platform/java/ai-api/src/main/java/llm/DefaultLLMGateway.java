package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of LLMGateway with multi-provider routing and
 * fallback.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides intelligent routing between multiple LLM providers based on: - Task
 * type routing (e.g., code generation → Anthropic, summarization → OpenAI) -
 * Cost optimization - Fallback on provider failures - Rate limiting per
 * provider
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DefaultLLMGateway gateway = DefaultLLMGateway.builder()
 *     .addProvider("openai", openAIService)
 *     .addProvider("anthropic", anthropicService)
 *     .addRoute("code-generation", "anthropic")
 *     .addRoute("summarization", "openai")
 *     .defaultProvider("openai")
 *     .fallbackOrder(List.of("openai", "anthropic"))
 *     .metrics(metricsCollector)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Multi-provider LLM gateway implementation
 * @doc.layer infrastructure
 * @doc.pattern Gateway
 */
public class DefaultLLMGateway implements LLMGateway {

    private static final Logger logger = LoggerFactory.getLogger(DefaultLLMGateway.class);

    private final Map<String, ToolAwareCompletionService> providers;
    private final Map<String, EmbeddingService> embeddingProviders;
    private final Map<String, String> taskTypeRoutes;
    private final List<String> fallbackOrder;
    private final String defaultProvider;
    private final MetricsCollector metrics;
    private final Map<String, AtomicInteger> providerFailureCounts;
    private final int maxFailuresBeforeCircuitBreak;

    private DefaultLLMGateway(Builder builder) {
        this.providers = Map.copyOf(builder.providers);
        this.embeddingProviders = Map.copyOf(builder.embeddingProviders);
        this.taskTypeRoutes = Map.copyOf(builder.taskTypeRoutes);
        this.fallbackOrder = List.copyOf(builder.fallbackOrder);
        this.defaultProvider = Objects.requireNonNull(builder.defaultProvider, "defaultProvider required");
        this.metrics = Objects.requireNonNull(builder.metrics, "metrics required");
        this.providerFailureCounts = new ConcurrentHashMap<>();
        this.maxFailuresBeforeCircuitBreak = builder.maxFailuresBeforeCircuitBreak;

        // Initialize failure counts
        for (String provider : providers.keySet()) {
            providerFailureCounts.put(provider, new AtomicInteger(0));
        }
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        String provider = selectProvider(request);
        return executeWithFallback(provider, svc -> svc.complete(request), "complete");
    }

    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
        String provider = selectProvider(request);
        return executeWithFallback(provider, svc -> svc.completeWithTools(request, tools), "completeWithTools");
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> toolResults
    ) {
        String provider = selectProvider(request);
        return executeWithFallback(provider, svc -> svc.continueWithToolResults(request, toolResults), "continueWithToolResults");
    }

    @Override
    public Promise<EmbeddingResult> embed(String text) {
        EmbeddingService service = embeddingProviders.get(defaultProvider);
        if (service == null) {
            return Promise.ofException(new IllegalStateException("No embedding service for provider: " + defaultProvider));
        }
        return service.createEmbedding(text);
    }

    @Override
    public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) {
        EmbeddingService service = embeddingProviders.get(defaultProvider);
        if (service == null) {
            return Promise.ofException(new IllegalStateException("No embedding service for provider: " + defaultProvider));
        }
        return service.createEmbeddings(texts);
    }

    @Override
    public Promise<TokenStream> stream(CompletionRequest request) {
        String provider = selectProvider(request);
        return executeStreamWithFallback(provider, request);
    }

    /**
     * Executes a streaming request with fallback support.
     *
     * <p>If the selected provider implements {@link StreamingCompletionService},
     * native streaming is used. Otherwise, the gateway falls back to a batch
     * {@link #complete(CompletionRequest)} and emits the full text as a single
     * token on the returned stream.
     */
    private Promise<TokenStream> executeStreamWithFallback(String primaryProvider, CompletionRequest request) {
        ToolAwareCompletionService service = providers.get(primaryProvider);
        if (service == null) {
            return Promise.ofException(new IllegalStateException("Provider not found: " + primaryProvider));
        }

        long startTime = System.currentTimeMillis();
        metrics.incrementCounter("llm.gateway.request", "provider", primaryProvider, "operation", "stream");

        Promise<TokenStream> streamPromise;
        if (service instanceof StreamingCompletionService streamingService) {
            streamPromise = streamingService.stream(request);
        } else {
            // Batch-to-stream adapter: complete() then emit result as single token
            streamPromise = service.complete(request).map(result -> {
                DefaultTokenStream ts = new DefaultTokenStream();
                String text = result.getText() != null ? result.getText() : "";
                ts.emitToken(text);
                ts.complete();
                return ts;
            });
        }

        return streamPromise
                .whenComplete((stream, error) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (error != null) {
                        handleProviderFailure(primaryProvider, error);
                        metrics.incrementCounter("llm.gateway.error", "provider", primaryProvider, "operation", "stream");
                    } else {
                        handleProviderSuccess(primaryProvider);
                        // Note: measures time-to-stream-creation, not time-to-first-token
                        metrics.recordTimer("llm.gateway.stream.setup.latency", duration, "provider", primaryProvider);
                    }
                })
                .then(Promise::of, error -> tryStreamFallback(primaryProvider, request, error));
    }

    /**
     * Attempts streaming on fallback providers after a failure.
     */
    private Promise<TokenStream> tryStreamFallback(
            String failedProvider,
            CompletionRequest request,
            Exception originalError
    ) {
        for (String fallbackProvider : fallbackOrder) {
            if (fallbackProvider.equals(failedProvider) || !isProviderAvailable(fallbackProvider)) {
                continue;
            }

            ToolAwareCompletionService fallbackService = providers.get(fallbackProvider);
            if (fallbackService == null) {
                continue;
            }

            logger.info("Attempting stream fallback from '{}' to '{}' after error: {}",
                    failedProvider, fallbackProvider, originalError.getMessage());
            metrics.incrementCounter("llm.gateway.fallback", "from", failedProvider, "to", fallbackProvider);

            if (fallbackService instanceof StreamingCompletionService streamingFallback) {
                return streamingFallback.stream(request)
                        .whenComplete((s, err) -> {
                            if (err != null) handleProviderFailure(fallbackProvider, err);
                            else handleProviderSuccess(fallbackProvider);
                        });
            } else {
                return fallbackService.complete(request).map(result -> {
                    DefaultTokenStream ts = new DefaultTokenStream();
                    ts.emitToken(result.getText() != null ? result.getText() : "");
                    ts.complete();
                    return ts;
                });
            }
        }

        return Promise.ofException(new LLMGatewayException(
                "All providers failed for stream. Original error: " + originalError.getMessage(), originalError));
    }

    @Override
    public MetricsCollector getMetrics() {
        return metrics;
    }

    @Override
    public String getDefaultProvider() {
        return defaultProvider;
    }

    @Override
    public List<String> getAvailableProviders() {
        return new ArrayList<>(providers.keySet());
    }

    @Override
    public boolean isProviderAvailable(String providerName) {
        if (!providers.containsKey(providerName)) {
            return false;
        }
        AtomicInteger failures = providerFailureCounts.get(providerName);
        return failures == null || failures.get() < maxFailuresBeforeCircuitBreak;
    }

    /**
     * Selects the best provider for the given request based on routing rules.
     */
    private String selectProvider(CompletionRequest request) {
        // Check task type routing from metadata
        if (request.getMetadata() != null) {
            Object taskType = request.getMetadata().get("taskType");
            if (taskType != null) {
                String routed = taskTypeRoutes.get(taskType.toString());
                if (routed != null && isProviderAvailable(routed)) {
                    logger.debug("Routing task type '{}' to provider '{}'", taskType, routed);
                    return routed;
                }
            }
        }

        // Check if default provider is available
        if (isProviderAvailable(defaultProvider)) {
            return defaultProvider;
        }

        // Fall back to first available provider
        for (String provider : fallbackOrder) {
            if (isProviderAvailable(provider)) {
                logger.warn("Default provider '{}' unavailable, falling back to '{}'", defaultProvider, provider);
                return provider;
            }
        }

        // Last resort - use default even if circuit broken
        logger.error("All providers unavailable, attempting default: {}", defaultProvider);
        return defaultProvider;
    }

    /**
     * Executes a request with fallback support.
     */
    private Promise<CompletionResult> executeWithFallback(
            String primaryProvider,
            ProviderOperation operation,
            String operationName
    ) {
        ToolAwareCompletionService service = providers.get(primaryProvider);
        if (service == null) {
            return Promise.ofException(new IllegalStateException("Provider not found: " + primaryProvider));
        }

        long startTime = System.currentTimeMillis();
        metrics.incrementCounter("llm.gateway.request", "provider", primaryProvider, "operation", operationName);

        return operation.execute(service)
                .whenComplete((result, error) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (error != null) {
                        handleProviderFailure(primaryProvider, error);
                        metrics.incrementCounter("llm.gateway.error", "provider", primaryProvider, "operation", operationName);
                    } else {
                        handleProviderSuccess(primaryProvider);
                        metrics.recordTimer("llm.gateway.latency", duration, "provider", primaryProvider);
                        if (result != null) {
                            metrics.incrementCounter("llm.gateway.tokens",
                                    "provider", primaryProvider,
                                    "type", "total");
                        }
                    }
                })
                .then(Promise::of, error -> {
                    // Attempt fallback on failure
                    return tryFallback(primaryProvider, operation, operationName, error);
                });
    }

    /**
     * Attempts to execute on fallback providers after a failure.
     */
    private Promise<CompletionResult> tryFallback(
            String failedProvider,
            ProviderOperation operation,
            String operationName,
            Exception originalError
    ) {
        for (String fallbackProvider : fallbackOrder) {
            if (fallbackProvider.equals(failedProvider)) {
                continue;
            }
            if (!isProviderAvailable(fallbackProvider)) {
                continue;
            }

            ToolAwareCompletionService fallbackService = providers.get(fallbackProvider);
            if (fallbackService == null) {
                continue;
            }

            logger.info("Attempting fallback from '{}' to '{}' after error: {}",
                    failedProvider, fallbackProvider, originalError.getMessage());
            metrics.incrementCounter("llm.gateway.fallback", "from", failedProvider, "to", fallbackProvider);

            return operation.execute(fallbackService)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            handleProviderFailure(fallbackProvider, error);
                        } else {
                            handleProviderSuccess(fallbackProvider);
                        }
                    });
        }

        // All fallbacks exhausted
        return Promise.ofException(new LLMGatewayException(
                "All providers failed. Original error: " + originalError.getMessage(), originalError));
    }

    private void handleProviderFailure(String provider, Throwable error) {
        AtomicInteger failures = providerFailureCounts.get(provider);
        if (failures != null) {
            int count = failures.incrementAndGet();
            if (count >= maxFailuresBeforeCircuitBreak) {
                logger.warn("Provider '{}' circuit breaker triggered after {} failures", provider, count);
            }
        }
    }

    private void handleProviderSuccess(String provider) {
        AtomicInteger failures = providerFailureCounts.get(provider);
        if (failures != null) {
            failures.set(0); // Reset failure count on success
        }
    }

    /**
     * Functional interface for provider operations.
     */
    @FunctionalInterface
    private interface ProviderOperation {

        Promise<CompletionResult> execute(ToolAwareCompletionService service);
    }

    /**
     * Exception thrown when all LLM providers fail.
     */
    public static class LLMGatewayException extends RuntimeException {

        public LLMGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Creates a new builder for DefaultLLMGateway.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DefaultLLMGateway.
     */
    public static final class Builder {

        private final Map<String, ToolAwareCompletionService> providers = new HashMap<>();
        private final Map<String, EmbeddingService> embeddingProviders = new HashMap<>();
        private final Map<String, String> taskTypeRoutes = new HashMap<>();
        private List<String> fallbackOrder = new ArrayList<>();
        private String defaultProvider;
        private MetricsCollector metrics;
        private int maxFailuresBeforeCircuitBreak = 3;

        private Builder() {
        }

        /**
         * Adds a completion provider.
         */
        public Builder addProvider(String name, ToolAwareCompletionService service) {
            this.providers.put(name, service);
            if (this.defaultProvider == null) {
                this.defaultProvider = name;
            }
            if (!this.fallbackOrder.contains(name)) {
                this.fallbackOrder.add(name);
            }
            return this;
        }

        /**
         * Adds an embedding provider.
         */
        public Builder addEmbeddingProvider(String name, EmbeddingService service) {
            this.embeddingProviders.put(name, service);
            return this;
        }

        /**
         * Adds a task type routing rule.
         */
        public Builder addRoute(String taskType, String providerName) {
            this.taskTypeRoutes.put(taskType, providerName);
            return this;
        }

        /**
         * Sets the default provider.
         */
        public Builder defaultProvider(String name) {
            this.defaultProvider = name;
            return this;
        }

        /**
         * Sets the fallback order for providers.
         */
        public Builder fallbackOrder(List<String> order) {
            this.fallbackOrder = new ArrayList<>(order);
            return this;
        }

        /**
         * Sets the metrics collector.
         */
        public Builder metrics(MetricsCollector metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * Sets the max failures before circuit breaker triggers.
         */
        public Builder maxFailuresBeforeCircuitBreak(int max) {
            this.maxFailuresBeforeCircuitBreak = max;
            return this;
        }

        public DefaultLLMGateway build() {
            if (providers.isEmpty()) {
                throw new IllegalStateException("At least one provider is required");
            }
            return new DefaultLLMGateway(this);
        }
    }
}
