package com.ghatana.aiplatform.gateway;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Central gateway for LLM operations, coordinating routing, caching, and rate limiting.
 *
 * <p><b>Responsibilities</b>
 * <ol>
 *   <li>Check per-tenant rate limits via {@link RateLimiter} before forwarding to a provider.</li>
 *   <li>Consult the {@link PromptCache} for cached embeddings/completions to avoid redundant API calls.</li>
 *   <li>Delegate to the appropriate {@link EmbeddingService} or {@link CompletionService} via
 *       {@link ProviderRouter}.</li>
 *   <li>Populate the cache and emit metrics on completion.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Unified gateway for embedding and completion LLM operations
 * @doc.layer platform
 * @doc.pattern Facade + Gateway
 */
public class LLMGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(LLMGatewayService.class);

    private final ProviderRouter router;
    private final PromptCache promptCache;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metrics;

    /**
     * Creates a gateway configured with all required collaborators.
     *
     * @param router      provider router for service selection
     * @param promptCache cache for embeddings and completions
     * @param rateLimiter outbound call rate limiter
     * @param metrics     metrics collector for observability
     */
    public LLMGatewayService(
            ProviderRouter router,
            PromptCache promptCache,
            RateLimiter rateLimiter,
            MetricsCollector metrics) {
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.promptCache = Objects.requireNonNull(promptCache, "promptCache must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Generates a single embedding for the given text.
     *
     * @param tenantId tenant making the request
     * @param text     text to embed
     * @return embedding result
     */
    public Promise<EmbeddingResult> generateEmbedding(String tenantId, String text) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(text, "text must not be null");

        return rateLimiter.checkLimit(tenantId, "embedding")
                .then(allowed -> {
                    if (!allowed) {
                        metrics.incrementCounter("llm.gateway.rate_limited", "operation", "embedding");
                        return Promise.ofException(new RateLimitExceededException(tenantId));
                    }
                    return promptCache.getEmbedding(tenantId, text)
                            .then(cached -> {
                                if (cached != null) {
                                    metrics.incrementCounter("llm.gateway.cache_hit", "operation", "embedding");
                                    return Promise.of(cached);
                                }
                                return routeEmbedding(tenantId, text);
                            });
                });
    }

    /**
     * Generates embeddings for a batch of texts.
     *
     * @param tenantId tenant making the request
     * @param texts    texts to embed
     * @return list of embedding results in the same order as the input
     */
    public Promise<List<EmbeddingResult>> generateEmbeddings(String tenantId, List<String> texts) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(texts, "texts must not be null");

        return rateLimiter.checkLimit(tenantId, "embeddings_batch")
                .then(allowed -> {
                    if (!allowed) {
                        metrics.incrementCounter("llm.gateway.rate_limited", "operation", "embeddings_batch");
                        return Promise.ofException(new RateLimitExceededException(tenantId));
                    }
                    EmbeddingService service = router.selectEmbeddingService(tenantId);
                    long start = System.currentTimeMillis();
                    return service.createEmbeddings(texts)
                            .whenResult(results -> {
                                long elapsed = System.currentTimeMillis() - start;
                                metrics.incrementCounter("llm.gateway.embedding_batch.success",
                                        "tenant", tenantId);
                                logger.debug("Batch embedding completed tenant={} count={} ms={}",
                                        tenantId, results.size(), elapsed);
                            })
                            .whenException(e -> {
                                metrics.incrementCounter("llm.gateway.embedding_batch.error",
                                        "tenant", tenantId);
                                logger.error("Batch embedding failed tenant={}", tenantId, e);
                            });
                });
    }

    /**
     * Generates a text completion for the given request.
     *
     * @param tenantId tenant making the request
     * @param request  completion request parameters
     * @return completion result
     */
    public Promise<CompletionResult> generateCompletion(String tenantId, CompletionRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return rateLimiter.checkLimit(tenantId, "completion")
                .then(allowed -> {
                    if (!allowed) {
                        metrics.incrementCounter("llm.gateway.rate_limited", "operation", "completion");
                        return Promise.ofException(new RateLimitExceededException(tenantId));
                    }
                    if (isCacheable(request)) {
                        return promptCache.getCompletion(tenantId, request)
                                .then(cached -> {
                                    if (cached != null) {
                                        metrics.incrementCounter("llm.gateway.cache_hit", "operation", "completion");
                                        return Promise.of(cached);
                                    }
                                    return routeCompletion(tenantId, request);
                                });
                    }
                    return routeCompletion(tenantId, request);
                });
    }

    // ------------------------------------------------------------------ //
    //  Private routing helpers                                             //
    // ------------------------------------------------------------------ //

    private Promise<EmbeddingResult> routeEmbedding(String tenantId, String text) {
        EmbeddingService service;
        try {
            service = router.selectEmbeddingService(tenantId);
        } catch (ProviderRouter.ProviderRouterException e) {
            service = router.selectFallbackEmbeddingService(tenantId);
            if (service == null) {
                return Promise.ofException(e);
            }
            logger.warn("Primary embedding provider unavailable for tenant={}; using fallback", tenantId);
        }

        EmbeddingService selected = service;
        long start = System.currentTimeMillis();
        return selected.createEmbedding(text)
                .whenResult(result -> {
                    long elapsed = System.currentTimeMillis() - start;
                    promptCache.putEmbedding(tenantId, text, result);
                    metrics.incrementCounter("llm.gateway.embedding.success", "tenant", tenantId);
                    logger.debug("Embedding generated tenant={} ms={}", tenantId, elapsed);
                })
                .whenException(e -> {
                    metrics.incrementCounter("llm.gateway.embedding.error", "tenant", tenantId);
                    logger.error("Embedding failed tenant={}", tenantId, e);
                });
    }

    private Promise<CompletionResult> routeCompletion(String tenantId, CompletionRequest request) {
        CompletionService service;
        try {
            service = router.selectCompletionService(tenantId);
        } catch (ProviderRouter.ProviderRouterException e) {
            service = router.selectFallbackCompletionService(tenantId);
            if (service == null) {
                return Promise.ofException(e);
            }
            logger.warn("Primary completion provider unavailable for tenant={}; using fallback", tenantId);
        }

        CompletionService selected = service;
        long start = System.currentTimeMillis();
        return selected.complete(request)
                .whenResult(result -> {
                    long elapsed = System.currentTimeMillis() - start;
                    if (isCacheable(request)) {
                        promptCache.putCompletion(tenantId, request, result);
                    }
                    metrics.incrementCounter("llm.gateway.completion.success", "tenant", tenantId);
                    logger.debug("Completion generated tenant={} ms={}", tenantId, elapsed);
                })
                .whenException(e -> {
                    metrics.incrementCounter("llm.gateway.completion.error", "tenant", tenantId);
                    logger.error("Completion failed tenant={}", tenantId, e);
                });
    }

    private boolean isCacheable(CompletionRequest request) {
        // Cache deterministic requests (temperature=0) only
        return request.getTemperature() == 0.0;
    }

    // ------------------------------------------------------------------ //
    //  Exception types                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Thrown when the rate limiter rejects an outbound LLM call.
     */
    public static final class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String tenantId) {
            super("LLM gateway rate limit exceeded for tenant: " + tenantId);
        }
    }
}
